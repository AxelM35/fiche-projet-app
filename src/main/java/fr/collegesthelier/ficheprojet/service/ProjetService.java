package fr.collegesthelier.ficheprojet.service;

import fr.collegesthelier.ficheprojet.dto.ProjetBloqueDTO;
import fr.collegesthelier.ficheprojet.dto.ProjetConsultationDTO;
import fr.collegesthelier.ficheprojet.dto.ProjetFormDTO;
import fr.collegesthelier.ficheprojet.dto.TableauDeBordStatsDTO;
import fr.collegesthelier.ficheprojet.event.ProjetEvent;
import fr.collegesthelier.ficheprojet.exception.ProjetNotFoundException;
import fr.collegesthelier.ficheprojet.exception.TransitionInvalideException;
import fr.collegesthelier.ficheprojet.model.Projet;
import fr.collegesthelier.ficheprojet.model.StatutProjet;
import fr.collegesthelier.ficheprojet.repository.ProjetRepository;
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
 * Couche metier du workflow de validation des voyages scolaires.
 * <p>
 * Concurrence : chaque transition recharge le projet en base au debut de sa
 * propre transaction et verifie son statut courant avant d'agir. Combine au
 * verrouillage optimiste (@Version sur Projet), cela empeche deux
 * utilisateurs de faire progresser deux fois le meme dossier (le second
 * echoue soit sur la verification de statut, soit, en cas de course
 * vraiment simultanee, sur l'ecriture JPA elle-meme qui remonte alors une
 * ObjectOptimisticLockingFailureException geree par le controleur).
 */
@Service
@RequiredArgsConstructor
public class ProjetService {

    private final ProjetRepository projetRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JournalService journalService;
    private final GoogleDriveService googleDriveService;

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
     * dossiers archives (Admin) n'apparaissent jamais ici, quel que soit leur
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
     * Dossiers archives par un Admin (retires du tableau de bord mais
     * toujours en base, recuperables via desarchiver()).
     */
    @Transactional(readOnly = true)
    public List<Projet> listerArchives() {
        return projetRepository.findByArchiveTrueOrderByDateDepartDesc();
    }

    @Transactional(readOnly = true)
    public long compterProjets() {
        return projetRepository.count();
    }

    /**
     * Chiffres cles affiches en tuiles au-dessus du Kanban. Calcules a
     * partir du tableau deja charge (pas de requete supplementaire) : le
     * budget "engage" ne compte que les projets definitivement VALIDE.
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
    // Creation / modification (reserve aux profs, protection Mass Assignment
    // via le DTO : seuls les champs exposes par ProjetFormDTO sont copies)
    // -------------------------------------------------------------------

    @PreAuthorize("hasRole('PROF')")
    @Transactional
    public Projet creerProjet(ProjetFormDTO dto) {
        Projet projet = new Projet();
        copierDtoVersEntite(dto, projet);
        projet.setStatut(StatutProjet.BROUILLON);
        Projet enregistre = projetRepository.save(projet);
        creerDossierDriveSiActive(enregistre);
        journalService.enregistrer("Creation", enregistre.getId(), enregistre.getNomProjet(), null);
        return enregistre;
    }

    /**
     * Tentative best-effort de creation automatique du dossier Drive du
     * projet (voir GoogleDriveService, jamais bloquant : un echec ou une
     * integration desactivee laisse simplement le lien vide, saisissable a
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
            throw new TransitionInvalideException("Ce dossier est deja engage dans le circuit de validation et ne peut plus etre modifie.");
        }
        verifierDroitModification(projet);
        verifierVersion(projet, dto.getVersion());

        copierDtoVersEntite(dto, projet);
        Projet enregistre = projetRepository.save(projet);

        // Un dossier VALIDE ne peut etre modifie que par un Admin (correction
        // exceptionnelle apres coup) : cas assez sensible pour meriter sa
        // propre entree de journal, distincte d'une simple modification de
        // brouillon par son organisateur.
        String action = (projet.getStatut() == StatutProjet.VALIDE && estAdmin)
                ? "Modification (admin, dossier deja valide)" : "Modification";
        journalService.enregistrer(action, enregistre.getId(), enregistre.getNomProjet(), null);
        return enregistre;
    }

    /**
     * Cree une copie en BROUILLON d'un projet existant (utile pour decliner
     * le meme voyage sur plusieurs classes ou dates). Les champs d'audit du
     * workflow ne sont jamais copies : la copie demarre son propre cycle de
     * validation depuis zero. L'organisateur est repointe vers l'utilisateur
     * qui duplique (et non celui du projet source), pour qu'il puisse
     * immediatement modifier et soumettre sa copie.
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
     * A utiliser depuis le controleur pour pre-remplir le formulaire
     * d'edition : charge le projet ET le convertit en DTO au sein d'une
     * seule et meme transaction. La collection accompagnateurs etant
     * chargee en lazy, un appel a trouverParId(id) suivi d'un appel
     * separe a versDTO(projet) echouerait (session Hibernate deja fermee
     * entre les deux appels transactionnels).
     */
    @Transactional(readOnly = true)
    public ProjetFormDTO chargerFormulaire(Long id) {
        return versDTO(trouverParId(id));
    }

    /**
     * Vue en lecture seule d'un projet valide (voir ProjetConsultationDTO) :
     * meme precaution transactionnelle que chargerFormulaire pour la
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
        dto.setCoutGlobal(projet.getCoutGlobal());
        dto.setCoutParEleve(projet.getCoutParEleve());
        dto.setMontantSubvention(projet.getMontantSubvention());
        dto.setEligiblePassCulture(Boolean.TRUE.equals(projet.getEligiblePassCulture()));
        dto.setCommentaire(projet.getCommentaire());
        dto.setLienDrive(projet.getLienDrive());
        return dto;
    }

    // -------------------------------------------------------------------
    // Workflow lineaire
    // -------------------------------------------------------------------

    @PreAuthorize("hasRole('PROF')")
    @Transactional
    public Projet soumettre(Long id) {
        Projet projet = trouverParId(id);
        verifierDroitModification(projet);
        if (projet.getStatut() != StatutProjet.BROUILLON && projet.getStatut() != StatutProjet.A_CORRIGER) {
            throw new TransitionInvalideException("Seul un dossier en brouillon ou a corriger peut etre soumis.");
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
     * Un premier envoi (aucune validation anterieure) repart de Comptabilite.
     * La resoumission d'un dossier corrige reprend juste apres la derniere
     * etape deja validee, sans faire revalider ceux qui avaient deja donne
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

        StatutProjet ancienStatut = projet.getStatut();
        projet.setStatut(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE);
        projet.setDateValidationCompta(LocalDateTime.now());

        Projet enregistre = projetRepository.save(projet);
        publierEvenement(enregistre, ancienStatut, "Validation Comptabilite", null);
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
            throw new TransitionInvalideException("Ce dossier n'est pas dans un etat pouvant etre refuse.");
        }

        StatutProjet ancienStatut = projet.getStatut();
        projet.setStatut(StatutProjet.A_CORRIGER);
        projet.setMotifRefus(motifRefus);

        // Les validations DEJA obtenues (etapes anterieures a celle qui
        // refuse) sont conservees : la resoumission (voir
        // determinerEtapeDeReprise) reprendra directement a l'etape qui a
        // refuse, sans faire revalider ceux qui avaient deja donne leur
        // accord. La date de l'etape qui refuse elle-meme n'a jamais ete
        // renseignee (on ne l'ecrit que lors d'une validation), rien a
        // effacer pour elle ni pour les etapes suivantes.

        Projet enregistre = projetRepository.save(projet);
        publierEvenement(enregistre, ancienStatut, "Refus", motifRefus);
        return enregistre;
    }

    // -------------------------------------------------------------------
    // Administration (Admin) : archivage et suppression, independants du
    // workflow de validation - un dossier peut etre archive/supprime quel
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

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void desarchiver(Long id) {
        Projet projet = trouverParId(id);
        projet.setArchive(false);
        projetRepository.save(projet);
        journalService.enregistrer("Desarchivage", projet.getId(), projet.getNomProjet(), null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void supprimerDefinitivement(Long id) {
        Projet projet = trouverParId(id);
        Long projetId = projet.getId();
        String nomProjet = projet.getNomProjet();
        projetRepository.delete(projet);
        journalService.enregistrer("Suppression definitive", projetId, nomProjet, null);
    }

    /**
     * Reaffecte un dossier a un autre organisateur (ex. professeur ayant
     * quitte l'etablissement en cours d'annee). N'affecte que l'identite de
     * l'organisateur, jamais le statut ni les dates de validation deja
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
        journalService.enregistrer("Reaffectation organisateur", projet.getId(), projet.getNomProjet(),
                "De " + ancienEmail + " vers " + nouvelEmail);
    }

    /**
     * Lien vers le dossier Google Drive des pieces jointes (MVP : simple
     * URL, pas d'integration API Drive). Modifiable independamment du
     * statut du dossier (y compris pendant l'instruction, EN_ATTENTE_*, ou
     * une fois VALIDE) : contrairement au reste du formulaire, l'ajout d'une
     * piece jointe n'est pas bloque par le circuit de validation. Ouvert a
     * l'organisateur du dossier ainsi qu'aux roles de validation
     * (Compta/Vie Scolaire/Direction), qui peuvent avoir besoin d'attacher
     * un document recu pendant l'instruction.
     */
    @PreAuthorize("hasRole('PROF')")
    @Transactional
    public Projet modifierLienDrive(Long id, String lienDrive) {
        Projet projet = trouverParId(id);
        if (!peutGererLienDrive(projet)) {
            throw new AccessDeniedException("Vous n'etes pas autorise a modifier les pieces jointes de ce dossier.");
        }

        projet.setLienDrive(lienDrive == null || lienDrive.isBlank() ? null : lienDrive);
        Projet enregistre = projetRepository.save(projet);
        journalService.enregistrer("Lien Drive", enregistre.getId(), enregistre.getNomProjet(),
                enregistre.getLienDrive() != null ? enregistre.getLienDrive() : "Lien retire");
        return enregistre;
    }

    /**
     * Determine si l'utilisateur connecte peut ajouter/modifier/retirer le
     * lien Drive d'un dossier : l'organisateur du dossier, ou n'importe quel
     * role de validation (pas necessairement celui de l'etape en cours,
     * puisqu'une piece jointe peut arriver a n'importe quel moment du
     * circuit). ROLE_LECTURE_SEULE reste volontairement exclu (role de
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
     * Dossiers en attente de validation depuis longtemps, tries du plus
     * ancien au plus recent, pour permettre une relance manuelle. La date
     * d'entree dans le statut courant se deduit des dates de validation deja
     * enregistrees (pas de champ dedie necessaire).
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
     * compris les dossiers archives (jamais visibles depuis le tableau de
     * bord principal). Filtrage en memoire plutot qu'une requete dynamique :
     * la volumetrie attendue (quelques dizaines de projets par an) le
     * permet largement, pour une implementation bien plus simple.
     */
    @Transactional(readOnly = true)
    public List<Projet> rechercherPourAdmin(String nom, String organisateur, String classe,
                                             StatutProjet statut, Boolean archive) {
        return projetRepository.findAll().stream()
                .filter(p -> contient(p.getNomProjet(), nom))
                .filter(p -> contient(p.getOrganisateurNom(), organisateur) || contient(p.getOrganisateurEmail(), organisateur))
                .filter(p -> contient(p.getClassesConcernees(), classe))
                .filter(p -> statut == null || p.getStatut() == statut)
                .filter(p -> archive == null || p.isArchive() == archive)
                .sorted(Comparator.comparing(Projet::getId).reversed())
                .collect(Collectors.toList());
    }

    private boolean contient(String valeur, String recherche) {
        if (recherche == null || recherche.isBlank()) {
            return true;
        }
        return valeur != null && valeur.toLowerCase(Locale.ROOT).contains(recherche.toLowerCase(Locale.ROOT));
    }

    // -------------------------------------------------------------------
    // Utilitaires prives
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
     * Verifie que la version editee par l'utilisateur (chargee au moment de
     * l'ouverture du formulaire) correspond toujours a la version courante
     * en base. Sans ce controle explicite, deux requetes HTTP successives
     * (GET formulaire puis POST) rechargeraient systematiquement la version
     * la plus recente avant d'ecrire, masquant silencieusement une
     * modification concurrente (perte de mise a jour).
     */
    private void verifierVersion(Projet projetActuel, Long versionSoumise) {
        if (versionSoumise != null && !versionSoumise.equals(projetActuel.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Projet.class, projetActuel.getId());
        }
    }

    /**
     * Seul l'organisateur du dossier (ou un administrateur / la direction)
     * peut modifier un brouillon : empeche un professeur de modifier le
     * dossier d'un collegue en devinant son identifiant.
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
     * renvoie donc directement l'email de l'utilisateur connecte, sans
     * dependre du type concret du principal (utile aussi pour les tests
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
