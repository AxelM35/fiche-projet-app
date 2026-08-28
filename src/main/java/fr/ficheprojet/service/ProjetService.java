package fr.ficheprojet.service;

import fr.ficheprojet.dto.ProjetBloqueDTO;
import fr.ficheprojet.dto.ProjetConsultationDTO;
import fr.ficheprojet.dto.ProjetFormDTO;
import fr.ficheprojet.dto.TableauDeBordStatsDTO;
import fr.ficheprojet.event.ProjetEvent;
import fr.ficheprojet.exception.ProjetNotFoundException;
import fr.ficheprojet.exception.TransitionInvalideException;
import fr.ficheprojet.model.Projet;
import fr.ficheprojet.model.StatutProjet;
import fr.ficheprojet.repository.CommentaireRepository;
import fr.ficheprojet.repository.ProjetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Couche métier du workflow de validation des voyages scolaires.
 * <p>
 * Concurrence : chaque transition recharge le projet en base au début de sa
 * propre transaction et vérifie son statut courant avant d'agir. Combiné au
 * verrouillage optimiste (@Version sur Projet), cela empêche deux
 * utilisateurs de faire progresser deux fois le même dossier (le second
 * échoue soit sur la vérification de statut, soit, en cas de course
 * vraiment simultanée, sur l'écriture JPA elle-même qui remonte alors une
 * ObjectOptimisticLockingFailureException gérée par le contrôleur).
 */
@Service
@RequiredArgsConstructor
public class ProjetService {

    private final ProjetRepository projetRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JournalService journalService;
    private final GoogleDriveService googleDriveService;
    private final CommentaireRepository commentaireRepository;

    // -------------------------------------------------------------------
    // Lecture
    // -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Projet trouverParId(Long id) {
        return projetRepository.findById(id)
                .orElseThrow(() -> new ProjetNotFoundException(id));
    }

    /**
     * Regroupe les projets par colonne du tableau de bord Kanban. Les
     * dossiers "A_CORRIGER" apparaissent dans la colonne Brouillon (c'est au
     * professeur de les retravailler avant de les resoumettre) ; leur statut
     * propre reste affiche dans la vue pour les distinguer visuellement. Les
     * dossiers archivés (Admin) n'apparaissent jamais ici, quel que soit leur
     * statut : voir listerArchives() pour les retrouver.
     */
    @Transactional(readOnly = true)
    public Map<StatutProjet, List<Projet>> projetsPourTableauDeBord() {
        Map<StatutProjet, List<Projet>> tableau = new LinkedHashMap<>();

        List<Projet> brouillons = new ArrayList<>(
                projetRepository.findByStatutAndArchiveFalseOrderByDateDepartAsc(StatutProjet.BROUILLON));
        brouillons.addAll(projetRepository.findByStatutAndArchiveFalseOrderByDateDepartAsc(StatutProjet.A_CORRIGER));

        tableau.put(StatutProjet.BROUILLON, brouillons);
        tableau.put(StatutProjet.EN_ATTENTE_COMPTA,
                projetRepository.findByStatutAndArchiveFalseOrderByDateDepartAsc(StatutProjet.EN_ATTENTE_COMPTA));
        tableau.put(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE,
                projetRepository.findByStatutAndArchiveFalseOrderByDateDepartAsc(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE));
        tableau.put(StatutProjet.EN_ATTENTE_DIRECTION,
                projetRepository.findByStatutAndArchiveFalseOrderByDateDepartAsc(StatutProjet.EN_ATTENTE_DIRECTION));
        tableau.put(StatutProjet.VALIDE,
                projetRepository.findByStatutAndArchiveFalseOrderByDateDepartAsc(StatutProjet.VALIDE));

        return tableau;
    }

    /**
     * Dossiers archivés par un Admin (retirés du tableau de bord mais
     * toujours en base, récupérables via desarchiver()), filtres sur une
     * année scolaire si précisée (voir AnneeScolaireUtil).
     */
    @Transactional(readOnly = true)
    public List<Projet> listerArchives(String anneeScolaire) {
        return projetRepository.findByArchiveTrueOrderByDateDepartDesc().stream()
                .filter(p -> anneeScolaire == null || anneeScolaire.equals(AnneeScolaireUtil.calculer(p.getDateDepart())))
                .collect(Collectors.toList());
    }

    /**
     * Années scolaires distinctes présentes parmi les dossiers archivés,
     * pour alimenter le filtre de /admin/archives.
     */
    @Transactional(readOnly = true)
    public List<String> listerAnneesScolairesDesArchives() {
        return anneesScolairesDistinctes(projetRepository.findByArchiveTrueOrderByDateDepartDesc());
    }

    /**
     * Années scolaires distinctes parmi les dossiers VALIDE non encore
     * archivés, pour alimenter le sélecteur de l'archivage groupé.
     */
    @Transactional(readOnly = true)
    public List<String> listerAnneesScolairesDesDossiersValidesActifs() {
        return anneesScolairesDistinctes(projetRepository.findByStatutAndArchiveFalseOrderByDateDepartAsc(StatutProjet.VALIDE));
    }

    private List<String> anneesScolairesDistinctes(List<Projet> projets) {
        return projets.stream()
                .map(p -> AnneeScolaireUtil.calculer(p.getDateDepart()))
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    @Transactional(readOnly = true)
    public long compterProjets() {
        return projetRepository.count();
    }

    /**
     * Chiffres clés affichés en tuiles au-dessus du Kanban. Calculés à
     * partir du tableau déjà chargé (pas de requête supplémentaire) : le
     * budget "engagé" ne compte que les projets définitivement VALIDE.
     */
    public TableauDeBordStatsDTO calculerStatistiques(Map<StatutProjet, List<Projet>> tableauDeBord) {
        long totalProjets = tableauDeBord.values().stream().mapToLong(List::size).sum();
        long projetsValides = tableauDeBord.getOrDefault(StatutProjet.VALIDE, List.of()).size();
        long projetsEnAttente = tableauDeBord.getOrDefault(StatutProjet.EN_ATTENTE_COMPTA, List.of()).size()
                + tableauDeBord.getOrDefault(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE, List.of()).size()
                + tableauDeBord.getOrDefault(StatutProjet.EN_ATTENTE_DIRECTION, List.of()).size();

        BigDecimal budgetTotalEngage = tableauDeBord.getOrDefault(StatutProjet.VALIDE, List.of()).stream()
                .map(Projet::getCoutGlobal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TableauDeBordStatsDTO(totalProjets, projetsValides, projetsEnAttente, budgetTotalEngage);
    }

    // -------------------------------------------------------------------
    // Création / modification (réservé aux profs, protection Mass Assignment
    // via le DTO : seuls les champs exposés par ProjetFormDTO sont copiés)
    // -------------------------------------------------------------------

    @PreAuthorize("hasRole('PROF')")
    @Transactional
    public Projet creerProjet(ProjetFormDTO dto) {
        Projet projet = new Projet();
        copierDtoVersEntite(dto, projet);
        projet.setStatut(StatutProjet.BROUILLON);
        Projet enregistre = projetRepository.save(projet);
        creerDossierDriveSiActive(enregistre);
        journalService.enregistrer("Création", enregistre.getId(), enregistre.getNomProjet(), null);
        return enregistre;
    }

    /**
     * Tentative best-effort de création automatique du dossier Drive du
     * projet (voir GoogleDriveService, jamais bloquant : un échec ou une
     * intégration désactivée laisse simplement le lien vide, saisissable à
     * la main ensuite).
     */
    private void creerDossierDriveSiActive(Projet projet) {
        googleDriveService.creerDossierProjet(projet.getId(), projet.getNomProjet()).ifPresent(lien -> {
            projet.setLienDrive(lien);
            projetRepository.save(projet);
        });
    }

    @PreAuthorize("hasRole('PROF')")
    @Transactional
    public Projet modifierProjet(Long id, ProjetFormDTO dto) {
        Projet projet = trouverParId(id);
        boolean estAdmin = possedeRole("ROLE_ADMIN");

        boolean modifiable = projet.getStatut() == StatutProjet.BROUILLON
                || projet.getStatut() == StatutProjet.A_CORRIGER
                || (projet.getStatut() == StatutProjet.VALIDE && estAdmin);
        if (!modifiable) {
            throw new TransitionInvalideException("Ce dossier est déjà engagé dans le circuit de validation et ne peut plus être modifié.");
        }
        verifierDroitModification(projet);
        verifierVersion(projet, dto.getVersion());

        copierDtoVersEntite(dto, projet);
        Projet enregistre = projetRepository.save(projet);

        // Un dossier VALIDE ne peut être modifié que par un Admin (correction
        // exceptionnelle après coup) : cas assez sensible pour mériter sa
        // propre entrée de journal, distincte d'une simple modification de
        // brouillon par son organisateur.
        String action = (projet.getStatut() == StatutProjet.VALIDE && estAdmin)
                ? "Modification (admin, dossier déjà validé)" : "Modification";
        journalService.enregistrer(action, enregistre.getId(), enregistre.getNomProjet(), null);
        return enregistre;
    }

    /**
     * Crée une copie en BROUILLON d'un projet existant (utile pour décliner
     * le même voyage sur plusieurs classes ou dates). Les champs d'audit du
     * workflow ne sont jamais copiés : la copie démarre son propre cycle de
     * validation depuis zéro. L'organisateur est repointé vers l'utilisateur
     * qui duplique (et non celui du projet source), pour qu'il puisse
     * immédiatement modifier et soumettre sa copie.
     */
    @PreAuthorize("hasRole('PROF')")
    @Transactional
    public Projet dupliquer(Long id) {
        Projet original = trouverParId(id);

        Projet copie = new Projet();
        copie.setNomProjet(original.getNomProjet() + " (copie)");
        copie.setDescription(original.getDescription());
        copie.setDateDepart(original.getDateDepart());
        copie.setDateRetour(original.getDateRetour());
        copie.setLieuDepart(original.getLieuDepart());
        copie.setLieuRetour(original.getLieuRetour());
        copie.setTransport(original.getTransport());
        copie.setOrganismeNom(original.getOrganismeNom());
        copie.setOrganismeTelephone(original.getOrganismeTelephone());
        copie.setOrganismeEmail(original.getOrganismeEmail());
        copie.setOrganisateurNom(original.getOrganisateurNom());
        copie.setOrganisateurEmail(emailUtilisateurConnecteOuOriginal(original));
        copie.setTelephoneOrganisateur(original.getTelephoneOrganisateur());
        copie.setClassesConcernees(original.getClassesConcernees());
        copie.setEffectif(original.getEffectif());
        copie.setAccompagnateurs(new ArrayList<>(original.getAccompagnateurs()));
        copie.setCoutGlobal(original.getCoutGlobal());
        copie.setCoutParEleve(original.getCoutParEleve());
        copie.setMontantSubvention(original.getMontantSubvention());
        copie.setEligiblePassCulture(original.getEligiblePassCulture());
        copie.setCommentaire(original.getCommentaire());
        copie.setStatut(StatutProjet.BROUILLON);

        Projet enregistree = projetRepository.save(copie);
        creerDossierDriveSiActive(enregistree);
        journalService.enregistrer("Duplication (depuis #" + original.getId() + ")",
                enregistree.getId(), enregistree.getNomProjet(), null);
        return enregistree;
    }

    private String emailUtilisateurConnecteOuOriginal(Projet original) {
        String emailConnecte = emailUtilisateurConnecte();
        return emailConnecte != null ? emailConnecte : original.getOrganisateurEmail();
    }

    /**
     * À utiliser depuis le contrôleur pour pré-remplir le formulaire
     * d'édition : charge le projet ET le convertit en DTO au sein d'une
     * seule et même transaction. La collection accompagnateurs étant
     * chargée en lazy, un appel à trouverParId(id) suivi d'un appel
     * séparé à versDTO(projet) échouerait (session Hibernate déjà fermée
     * entre les deux appels transactionnels).
     */
    @Transactional(readOnly = true)
    public ProjetFormDTO chargerFormulaire(Long id) {
        return versDTO(trouverParId(id));
    }

    /**
     * Vue en lecture seule d'un projet validé (voir ProjetConsultationDTO) :
     * même précaution transactionnelle que chargerFormulaire pour la
     * collection accompagnateurs.
     */
    @Transactional(readOnly = true)
    public ProjetConsultationDTO chargerConsultation(Long id) {
        Projet projet = trouverParId(id);
        return new ProjetConsultationDTO(
                projet.getId(),
                projet.getNomProjet(),
                projet.getDescription(),
                projet.getDateDepart(),
                projet.getDateRetour(),
                projet.getLieuDepart(),
                projet.getLieuRetour(),
                projet.getTransport(),
                projet.getOrganismeNom(),
                projet.getOrganismeTelephone(),
                projet.getOrganismeEmail(),
                projet.getOrganisateurNom(),
                projet.getOrganisateurEmail(),
                projet.getTelephoneOrganisateur(),
                projet.getClassesConcernees(),
                projet.getEffectif(),
                new ArrayList<>(projet.getAccompagnateurs()),
                projet.getCoutGlobal(),
                projet.getCoutParEleve(),
                projet.getMontantSubvention(),
                Boolean.TRUE.equals(projet.getEligiblePassCulture()),
                projet.getCommentaire(),
                projet.getLienDrive(),
                projet.getStatut(),
                projet.getMotifRefus(),
                projet.getDateValidationProf(),
                projet.getDateValidationCompta(),
                projet.getDateValidationVieScolaire(),
                projet.getDateValidationDirection());
    }

    public ProjetFormDTO versDTO(Projet projet) {
        ProjetFormDTO dto = new ProjetFormDTO();
        dto.setId(projet.getId());
        dto.setVersion(projet.getVersion());
        dto.setNomProjet(projet.getNomProjet());
        dto.setDescription(projet.getDescription());
        dto.setDateDepart(projet.getDateDepart());
        dto.setDateRetour(projet.getDateRetour());
        dto.setLieuDepart(projet.getLieuDepart());
        dto.setLieuRetour(projet.getLieuRetour());
        dto.setTransport(projet.getTransport());
        dto.setOrganismeNom(projet.getOrganismeNom());
        dto.setOrganismeTelephone(projet.getOrganismeTelephone());
        dto.setOrganismeEmail(projet.getOrganismeEmail());
        dto.setOrganisateurNom(projet.getOrganisateurNom());
        dto.setOrganisateurEmail(projet.getOrganisateurEmail());
        dto.setTelephoneOrganisateur(projet.getTelephoneOrganisateur());
        dto.setClassesConcernees(projet.getClassesConcernees());
        dto.setEffectif(projet.getEffectif());
        dto.setAccompagnateurs(new ArrayList<>(projet.getAccompagnateurs()));
        dto.setBudgetInconnu(projet.getCoutGlobal() == null);
        dto.setCoutGlobal(projet.getCoutGlobal());
        dto.setCoutParEleve(projet.getCoutParEleve());
        dto.setMontantSubvention(projet.getMontantSubvention());
        dto.setEligiblePassCulture(Boolean.TRUE.equals(projet.getEligiblePassCulture()));
        dto.setCommentaire(projet.getCommentaire());
        dto.setLienDrive(projet.getLienDrive());
        return dto;
    }

    // -------------------------------------------------------------------
    // Workflow linéaire
    // -------------------------------------------------------------------

    @PreAuthorize("hasRole('PROF')")
    @Transactional
    public Projet soumettre(Long id) {
        Projet projet = trouverParId(id);
        verifierDroitModification(projet);
        if (projet.getStatut() != StatutProjet.BROUILLON && projet.getStatut() != StatutProjet.A_CORRIGER) {
            throw new TransitionInvalideException("Seul un dossier en brouillon ou à corriger peut être soumis.");
        }

        StatutProjet ancienStatut = projet.getStatut();
        projet.setStatut(determinerEtapeDeReprise(projet));
        projet.setDateValidationProf(LocalDateTime.now());
        projet.setMotifRefus(null);

        Projet enregistre = projetRepository.save(projet);
        publierEvenement(enregistre, ancienStatut,
                ancienStatut == StatutProjet.A_CORRIGER ? "Resoumission" : "Soumission", null);
        return enregistre;
    }

    /**
     * Un premier envoi (aucune validation antérieure) repart de Comptabilité.
     * La resoumission d'un dossier corrigé reprend juste après la dernière
     * étape déjà validée, sans faire revalider ceux qui avaient déjà donné
     * leur accord avant que le refus n'intervienne plus loin dans le circuit.
     */
    private StatutProjet determinerEtapeDeReprise(Projet projet) {
        if (projet.getDateValidationVieScolaire() != null) {
            return StatutProjet.EN_ATTENTE_DIRECTION;
        }
        if (projet.getDateValidationCompta() != null) {
            return StatutProjet.EN_ATTENTE_VIE_SCOLAIRE;
        }
        return StatutProjet.EN_ATTENTE_COMPTA;
    }

    @PreAuthorize("hasAnyRole('COMPTA', 'ADMIN')")
    @Transactional
    public Projet validerCompta(Long id) {
        Projet projet = trouverParId(id);
        if (projet.getStatut() != StatutProjet.EN_ATTENTE_COMPTA) {
            throw new TransitionInvalideException("Ce dossier n'est pas (ou plus) en attente de validation comptable.");
        }
        // Le budget peut avoir été laissé vide à la soumission ("Je ne
        // connais pas encore le budget", voir ProjetFormDTO.budgetInconnu) :
        // completerBudget() permet de le renseigner avant d'en arriver la,
        // mais la Comptabilité ne peut pas valider un budget qui n'existe
        // toujours pas.
        if (projet.getCoutGlobal() == null || projet.getCoutParEleve() == null) {
            throw new TransitionInvalideException(
                    "Le budget doit être renseigné avant de pouvoir être validé par la Comptabilité.");
        }

        StatutProjet ancienStatut = projet.getStatut();
        projet.setStatut(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE);
        projet.setDateValidationCompta(LocalDateTime.now());

        Projet enregistre = projetRepository.save(projet);
        publierEvenement(enregistre, ancienStatut, "Validation Comptabilité", null);
        return enregistre;
    }

    @PreAuthorize("hasAnyRole('VIESCO', 'ADMIN')")
    @Transactional
    public Projet validerVieScolaire(Long id) {
        Projet projet = trouverParId(id);
        if (projet.getStatut() != StatutProjet.EN_ATTENTE_VIE_SCOLAIRE) {
            throw new TransitionInvalideException("Ce dossier n'est pas (ou plus) en attente de validation vie scolaire.");
        }

        StatutProjet ancienStatut = projet.getStatut();
        projet.setStatut(StatutProjet.EN_ATTENTE_DIRECTION);
        projet.setDateValidationVieScolaire(LocalDateTime.now());

        Projet enregistre = projetRepository.save(projet);
        publierEvenement(enregistre, ancienStatut, "Validation Vie Scolaire", null);
        return enregistre;
    }

    @PreAuthorize("hasAnyRole('DIRECTION', 'ADMIN')")
    @Transactional
    public Projet validerDirection(Long id) {
        Projet projet = trouverParId(id);
        if (projet.getStatut() != StatutProjet.EN_ATTENTE_DIRECTION) {
            throw new TransitionInvalideException("Ce dossier n'est pas (ou plus) en attente de validation direction.");
        }

        StatutProjet ancienStatut = projet.getStatut();
        projet.setStatut(StatutProjet.VALIDE);
        projet.setDateValidationDirection(LocalDateTime.now());

        Projet enregistre = projetRepository.save(projet);
        publierEvenement(enregistre, ancienStatut, "Validation Direction (finale)", null);
        return enregistre;
    }

    private static final EnumSet<StatutProjet> STATUTS_REFUSABLES = EnumSet.of(
            StatutProjet.EN_ATTENTE_COMPTA, StatutProjet.EN_ATTENTE_VIE_SCOLAIRE, StatutProjet.EN_ATTENTE_DIRECTION);

    @PreAuthorize("hasAnyRole('COMPTA', 'VIESCO', 'DIRECTION', 'ADMIN')")
    @Transactional
    public Projet refuser(Long id, String motifRefus) {
        Projet projet = trouverParId(id);
        if (!STATUTS_REFUSABLES.contains(projet.getStatut())) {
            throw new TransitionInvalideException("Ce dossier n'est pas dans un état pouvant être refusé.");
        }

        StatutProjet ancienStatut = projet.getStatut();
        projet.setStatut(StatutProjet.A_CORRIGER);
        projet.setMotifRefus(motifRefus);

        // Les validations DÉJÀ obtenues (étapes antérieures à celle qui
        // refusé) sont conservées : la resoumission (voir
        // determinerEtapeDeReprise) reprendra directement à l'étape qui a
        // refusé, sans faire revalider ceux qui avaient déjà donné leur
        // accord. La date de l'étape qui refuse elle-même n'a jamais été
        // renseignée (on ne l'écrit que lors d'une validation), rien à
        // effacer pour elle ni pour les étapes suivantes.

        Projet enregistre = projetRepository.save(projet);
        publierEvenement(enregistre, ancienStatut, "Refus (" + libelleEtape(ancienStatut) + ")", motifRefus);
        return enregistre;
    }

    /**
     * Libellé de l'étape à laquelle un dossier était en attente (utilisé
     * pour qualifier l'action "Refus (...)" dans le journal d'audit,
     * nécessaire au calcul du taux de refus par étape - voir
     * StatistiquesService).
     */
    private String libelleEtape(StatutProjet statut) {
        return switch (statut) {
            case EN_ATTENTE_COMPTA -> "Comptabilité";
            case EN_ATTENTE_VIE_SCOLAIRE -> "Vie Scolaire";
            case EN_ATTENTE_DIRECTION -> "Direction";
            default -> statut.name();
        };
    }

    // -------------------------------------------------------------------
    // Administration (Admin) : archivage et suppression, indépendants du
    // workflow de validation - un dossier peut être archivé/supprime quel
    // que soit son statut.
    // -------------------------------------------------------------------

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void archiver(Long id) {
        Projet projet = trouverParId(id);
        projet.setArchive(true);
        projetRepository.save(projet);
        journalService.enregistrer("Archivage", projet.getId(), projet.getNomProjet(), null);
    }

    /**
     * Archivage groupe de tous les dossiers VALIDE non déjà archivés d'une
     * année scolaire donnée (voir AnneeScolaireUtil) : action Admin depuis
     * /admin/archivés, pour éviter d'archiver dossier par dossier à chaque
     * fin d'année. Volontairement pas automatique (voir décision du cahier
     * des charges §3) : l'Admin choisit quand déclencher l'archivage.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public int archiverDossiersValidesDeLAnneeScolaire(String anneeScolaire) {
        List<Projet> candidats = projetRepository.findByStatutAndArchiveFalseOrderByDateDepartAsc(StatutProjet.VALIDE).stream()
                .filter(p -> anneeScolaire.equals(AnneeScolaireUtil.calculer(p.getDateDepart())))
                .toList();
        for (Projet projet : candidats) {
            projet.setArchive(true);
            projetRepository.save(projet);
            journalService.enregistrer("Archivage", projet.getId(), projet.getNomProjet(),
                    "Archivage groupé — année scolaire " + anneeScolaire);
        }
        return candidats.size();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void desarchiver(Long id) {
        Projet projet = trouverParId(id);
        projet.setArchive(false);
        projetRepository.save(projet);
        journalService.enregistrer("Désarchivage", projet.getId(), projet.getNomProjet(), null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void supprimerDefinitivement(Long id) {
        Projet projet = trouverParId(id);
        Long projetId = projet.getId();
        String nomProjet = projet.getNomProjet();
        commentaireRepository.deleteByProjetId(projetId);
        projetRepository.delete(projet);
        journalService.enregistrer("Suppression définitive", projetId, nomProjet, null);
    }

    /**
     * Réaffecte un dossier à un autre organisateur (ex. professeur ayant
     * quitte l'établissement en cours d'année). N'affecte que l'identité de
     * l'organisateur, jamais le statut ni les dates de validation déjà
     * obtenues.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void reaffecterOrganisateur(Long id, String nouvelEmail, String nouveauNom) {
        Projet projet = trouverParId(id);
        String ancienEmail = projet.getOrganisateurEmail();
        projet.setOrganisateurEmail(nouvelEmail);
        projet.setOrganisateurNom(nouveauNom);
        projetRepository.save(projet);
        journalService.enregistrer("Réaffectation organisateur", projet.getId(), projet.getNomProjet(),
                "De " + ancienEmail + " vers " + nouvelEmail);
    }

    /**
     * Lien vers le dossier Google Drive des pièces jointes (MVP : simple
     * URL, pas d'intégration API Drive). Modifiable indépendamment du
     * statut du dossier (y compris pendant l'instruction, EN_ATTENTE_*, ou
     * une fois VALIDE) : contrairement au reste du formulaire, l'ajout d'une
     * pièce jointe n'est pas bloqué par le circuit de validation. Ouvert à
     * l'organisateur du dossier ainsi qu'aux rôles de validation
     * (Compta/Vie Scolaire/Direction), qui peuvent avoir besoin d'attacher
     * un document reçu pendant l'instruction.
     */
    @PreAuthorize("hasRole('PROF')")
    @Transactional
    public Projet modifierLienDrive(Long id, String lienDrive) {
        Projet projet = trouverParId(id);
        if (!peutGererLienDrive(projet)) {
            throw new AccessDeniedException("Vous n'êtes pas autorisé à modifier les pièces jointes de ce dossier.");
        }

        projet.setLienDrive(lienDrive == null || lienDrive.isBlank() ? null : lienDrive);
        Projet enregistre = projetRepository.save(projet);
        journalService.enregistrer("Lien Drive", enregistre.getId(), enregistre.getNomProjet(),
                enregistre.getLienDrive() != null ? enregistre.getLienDrive() : "Lien retiré");
        return enregistre;
    }

    /**
     * Complète le budget d'un dossier laissé vide à la création (case
     * "Je ne connais pas encore le budget" du formulaire, voir
     * ProjetFormDTO.budgetInconnu) : même périmètre d'autorisation et même
     * indépendance vis-à-vis du statut que le lien Drive ci-dessus
     * (peutGererLienDrive), puisque le formulaire principal n'est plus
     * modifiable une fois le dossier engagé dans le circuit de validation
     * (aucun bouton "Enregistrer" en EN_ATTENTE_*, voir formulaire.html) -
     * indispensable notamment à la Comptabilité pour renseigner le budget
     * avant de pouvoir le valider (voir validerCompta ci-dessus).
     */
    @PreAuthorize("hasRole('PROF')")
    @Transactional
    public Projet completerBudget(Long id, BigDecimal coutGlobal, BigDecimal coutParEleve, BigDecimal montantSubvention) {
        Projet projet = trouverParId(id);
        if (!peutGererLienDrive(projet)) {
            throw new AccessDeniedException("Vous n'êtes pas autorisé à compléter le budget de ce dossier.");
        }

        projet.setCoutGlobal(coutGlobal);
        projet.setCoutParEleve(coutParEleve);
        projet.setMontantSubvention(montantSubvention);
        Projet enregistre = projetRepository.save(projet);
        journalService.enregistrer("Budget complété", enregistre.getId(), enregistre.getNomProjet(), null);
        return enregistre;
    }

    /**
     * Détermine si l'utilisateur connecté peut ajouter/modifier/retirer le
     * lien Drive d'un dossier : l'organisateur du dossier, ou n'importe quel
     * rôle de validation (pas nécessairement celui de l'étape en cours,
     * puisqu'une pièce jointe peut arriver à n'importe quel moment du
     * circuit). ROLE_LECTURE_SEULE reste volontairement exclu (rôle de
     * consultation uniquement).
     */
    @Transactional(readOnly = true)
    public boolean peutGererLienDrive(Projet projet) {
        String emailConnecte = emailUtilisateurConnecte();
        boolean estProprietaire = emailConnecte != null
                && emailConnecte.equalsIgnoreCase(projet.getOrganisateurEmail());
        return estProprietaire || possedeRole("ROLE_COMPTA") || possedeRole("ROLE_VIESCO")
                || possedeRole("ROLE_DIRECTION") || possedeRole("ROLE_ADMIN");
    }

    /**
     * Dossiers en attente de validation depuis longtemps, triés du plus
     * ancien au plus récent, pour permettre une relance manuelle. La date
     * d'entrée dans le statut courant se déduit des dates de validation déjà
     * enregistrées (pas de champ dédié nécessaire).
     */
    @Transactional(readOnly = true)
    public List<ProjetBloqueDTO> listerDossiersBloques() {
        List<ProjetBloqueDTO> resultat = new ArrayList<>();
        for (StatutProjet statut : List.of(StatutProjet.EN_ATTENTE_COMPTA,
                StatutProjet.EN_ATTENTE_VIE_SCOLAIRE, StatutProjet.EN_ATTENTE_DIRECTION)) {
            for (Projet projet : projetRepository.findByStatutAndArchiveFalseOrderByDateDepartAsc(statut)) {
                LocalDateTime depuis = dateEntreeDansStatutCourant(projet);
                long jours = depuis != null ? Duration.between(depuis, LocalDateTime.now()).toDays() : 0;
                resultat.add(new ProjetBloqueDTO(projet.getId(), projet.getNomProjet(), statut, depuis, jours,
                        projet.getOrganisateurNom(), projet.getOrganisateurEmail()));
            }
        }
        resultat.sort(Comparator.comparingLong(ProjetBloqueDTO::joursEnAttente).reversed());
        return resultat;
    }

    private LocalDateTime dateEntreeDansStatutCourant(Projet projet) {
        return switch (projet.getStatut()) {
            case EN_ATTENTE_COMPTA -> projet.getDateValidationProf();
            case EN_ATTENTE_VIE_SCOLAIRE -> projet.getDateValidationCompta();
            case EN_ATTENTE_DIRECTION -> projet.getDateValidationVieScolaire();
            default -> null;
        };
    }

    /**
     * Recherche libre pour le dashboard admin : tous statuts confondus, y
     * compris les dossiers archivés (jamais visibles depuis le tableau de
     * bord principal). Filtrage en mémoire plutôt qu'une requête dynamique :
     * la volumétrie attendue (quelques dizaines de projets par an) le
     * permet largement, pour une implementation bien plus simple.
     */
    @Transactional(readOnly = true)
    public List<Projet> rechercherPourAdmin(String nom, String organisateur, String classe,
                                             StatutProjet statut, Boolean archive, String anneeScolaire) {
        return projetRepository.findAll().stream()
                .filter(p -> contient(p.getNomProjet(), nom))
                .filter(p -> contient(p.getOrganisateurNom(), organisateur) || contient(p.getOrganisateurEmail(), organisateur))
                .filter(p -> contient(p.getClassesConcernees(), classe))
                .filter(p -> statut == null || p.getStatut() == statut)
                .filter(p -> archive == null || p.isArchive() == archive)
                .filter(p -> anneeScolaire == null || anneeScolaire.equals(AnneeScolaireUtil.calculer(p.getDateDepart())))
                .sorted(Comparator.comparing(Projet::getId).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Années scolaires distinctes, tous statuts/archivage confondus, pour
     * alimenter le filtre de /admin/recherche.
     */
    @Transactional(readOnly = true)
    public List<String> listerAnneesScolaires() {
        return anneesScolairesDistinctes(projetRepository.findAll());
    }

    private boolean contient(String valeur, String recherche) {
        if (recherche == null || recherche.isBlank()) {
            return true;
        }
        return valeur != null && valeur.toLowerCase(Locale.ROOT).contains(recherche.toLowerCase(Locale.ROOT));
    }

    // -------------------------------------------------------------------
    // Utilitaires privés
    // -------------------------------------------------------------------

    private void copierDtoVersEntite(ProjetFormDTO dto, Projet cible) {
        cible.setNomProjet(dto.getNomProjet());
        cible.setDescription(dto.getDescription());
        cible.setDateDepart(dto.getDateDepart());
        cible.setDateRetour(dto.getDateRetour());
        cible.setLieuDepart(dto.getLieuDepart());
        cible.setLieuRetour(dto.getLieuRetour());
        cible.setTransport(dto.getTransport());
        cible.setOrganismeNom(dto.getOrganismeNom());
        cible.setOrganismeTelephone(dto.getOrganismeTelephone());
        cible.setOrganismeEmail(dto.getOrganismeEmail());
        cible.setOrganisateurNom(dto.getOrganisateurNom());
        cible.setOrganisateurEmail(dto.getOrganisateurEmail());
        cible.setTelephoneOrganisateur(dto.getTelephoneOrganisateur());
        cible.setClassesConcernees(dto.getClassesConcernees());
        cible.setEffectif(dto.getEffectif());

        List<String> accompagnateurs = dto.getAccompagnateurs() == null ? List.of() : dto.getAccompagnateurs();
        // ArrayList mutable explicite : Hibernate doit pouvoir vider/repeupler
        // la collection persistante existante (@ElementCollection) lors du
        // prochain merge, ce qu'une liste immuable (Stream.toList()) interdit.
        cible.setAccompagnateurs(accompagnateurs.stream()
                .filter(nom -> nom != null && !nom.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(ArrayList::new)));

        cible.setCoutGlobal(dto.getCoutGlobal());
        cible.setCoutParEleve(dto.getCoutParEleve());
        cible.setMontantSubvention(dto.getMontantSubvention());
        cible.setEligiblePassCulture(dto.isEligiblePassCulture());
        cible.setCommentaire(dto.getCommentaire());
    }

    private void publierEvenement(Projet projet, StatutProjet ancienStatut, String action, String detailJournal) {
        eventPublisher.publishEvent(new ProjetEvent(
                projet.getId(), projet.getNomProjet(), projet.getOrganisateurEmail(),
                ancienStatut, projet.getStatut(), projet.getMotifRefus()));
        journalService.enregistrer(action, projet.getId(), projet.getNomProjet(), detailJournal);
    }

    /**
     * Vérifie que la version éditée par l'utilisateur (chargée au moment de
     * l'ouverture du formulaire) correspond toujours à la version courante
     * en base. Sans ce contrôle explicite, deux requêtes HTTP successives
     * (GET formulaire puis POST) rechargeraient systématiquement la version
     * la plus récente avant d'écrire, masquant silencieusement une
     * modification concurrente (perte de mise à jour).
     */
    private void verifierVersion(Projet projetActuel, Long versionSoumise) {
        if (versionSoumise != null && !versionSoumise.equals(projetActuel.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Projet.class, projetActuel.getId());
        }
    }

    /**
     * Seul l'organisateur du dossier (ou un administrateur / la direction)
     * peut modifier un brouillon : empêche un professeur de modifier le
     * dossier d'un collègue en devinant son identifiant.
     */
    private void verifierDroitModification(Projet projet) {
        String emailConnecte = emailUtilisateurConnecte();
        boolean estProprietaire = emailConnecte != null
                && emailConnecte.equalsIgnoreCase(projet.getOrganisateurEmail());

        if (!estProprietaire && !possedeRole("ROLE_ADMIN") && !possedeRole("ROLE_DIRECTION")) {
            throw new AccessDeniedException("Vous ne pouvez modifier que vos propres dossiers.");
        }
    }

    /**
     * Le principal OAuth2 est construit (CustomOAuth2UserService) avec
     * l'attribut "email" comme nameAttributeKey : Authentication.getName()
     * renvoie donc directement l'email de l'utilisateur connecté, sans
     * dépendre du type concret du principal (utile aussi pour les tests
     * utilisant TestingAuthenticationToken / @WithMockUser).
     */
    private String emailUtilisateurConnecte() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    private boolean possedeRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
