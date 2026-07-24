package fr.collegesthelier.voyages.service;

import fr.collegesthelier.voyages.dto.ProjetFormDTO;
import fr.collegesthelier.voyages.event.ProjetEvent;
import fr.collegesthelier.voyages.exception.ProjetNotFoundException;
import fr.collegesthelier.voyages.exception.TransitionInvalideException;
import fr.collegesthelier.voyages.model.Projet;
import fr.collegesthelier.voyages.model.StatutProjet;
import fr.collegesthelier.voyages.repository.ProjetRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * propre reste affiche dans la vue pour les distinguer visuellement.
     */
    @Transactional(readOnly = true)
    public Map<StatutProjet, List<Projet>> projetsPourTableauDeBord() {
        Map<StatutProjet, List<Projet>> tableau = new LinkedHashMap<>();

        List<Projet> brouillons = new ArrayList<>(
                projetRepository.findByStatutOrderByDateDepartAsc(StatutProjet.BROUILLON));
        brouillons.addAll(projetRepository.findByStatutOrderByDateDepartAsc(StatutProjet.A_CORRIGER));

        tableau.put(StatutProjet.BROUILLON, brouillons);
        tableau.put(StatutProjet.EN_ATTENTE_COMPTA,
                projetRepository.findByStatutOrderByDateDepartAsc(StatutProjet.EN_ATTENTE_COMPTA));
        tableau.put(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE,
                projetRepository.findByStatutOrderByDateDepartAsc(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE));
        tableau.put(StatutProjet.EN_ATTENTE_DIRECTION,
                projetRepository.findByStatutOrderByDateDepartAsc(StatutProjet.EN_ATTENTE_DIRECTION));
        tableau.put(StatutProjet.VALIDE,
                projetRepository.findByStatutOrderByDateDepartAsc(StatutProjet.VALIDE));

        return tableau;
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
        return projetRepository.save(projet);
    }

    @PreAuthorize("hasRole('PROF')")
    @Transactional
    public Projet modifierProjet(Long id, ProjetFormDTO dto) {
        Projet projet = trouverParId(id);

        if (projet.getStatut() != StatutProjet.BROUILLON && projet.getStatut() != StatutProjet.A_CORRIGER) {
            throw new TransitionInvalideException("Ce dossier est deja engage dans le circuit de validation et ne peut plus etre modifie.");
        }
        verifierDroitModification(projet);
        verifierVersion(projet, dto.getVersion());

        copierDtoVersEntite(dto, projet);
        return projetRepository.save(projet);
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
        projet.setStatut(StatutProjet.EN_ATTENTE_COMPTA);
        projet.setDateValidationProf(LocalDateTime.now());
        projet.setMotifRefus(null);

        Projet enregistre = projetRepository.save(projet);
        publierEvenement(enregistre, ancienStatut);
        return enregistre;
    }

    @PreAuthorize("hasRole('COMPTA')")
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
        publierEvenement(enregistre, ancienStatut);
        return enregistre;
    }

    @PreAuthorize("hasRole('VIESCO')")
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
        publierEvenement(enregistre, ancienStatut);
        return enregistre;
    }

    @PreAuthorize("hasRole('DIRECTION')")
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
        publierEvenement(enregistre, ancienStatut);
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

        // Les validations anterieures sont effacees : le dossier reprend le
        // circuit depuis le debut une fois corrige et resoumis par le prof.
        projet.setDateValidationProf(null);
        projet.setDateValidationCompta(null);
        projet.setDateValidationVieScolaire(null);
        projet.setDateValidationDirection(null);

        Projet enregistre = projetRepository.save(projet);
        publierEvenement(enregistre, ancienStatut);
        return enregistre;
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
    }

    private void publierEvenement(Projet projet, StatutProjet ancienStatut) {
        eventPublisher.publishEvent(new ProjetEvent(
                projet.getId(), projet.getNomProjet(), projet.getOrganisateurEmail(),
                ancienStatut, projet.getStatut(), projet.getMotifRefus()));
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
