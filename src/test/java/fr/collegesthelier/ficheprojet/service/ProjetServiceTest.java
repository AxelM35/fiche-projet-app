package fr.collegesthelier.ficheprojet.service;

import fr.collegesthelier.ficheprojet.dto.ProjetFormDTO;
import fr.collegesthelier.ficheprojet.exception.ProjetNotFoundException;
import fr.collegesthelier.ficheprojet.exception.TransitionInvalideException;
import fr.collegesthelier.ficheprojet.model.JournalEntree;
import fr.collegesthelier.ficheprojet.model.Projet;
import fr.collegesthelier.ficheprojet.model.StatutProjet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifie le workflow lineaire complet ainsi que les deux garde-fous de
 * concurrence/securite : verrouillage optimiste et controle de propriete
 * d'un dossier. Le contexte de securite est bascule manuellement d'un role
 * a l'autre au sein d'un meme test (@WithMockUser ne s'applique qu'au
 * demarrage d'une methode @Test, pas a des appels internes).
 */
@SpringBootTest
@ActiveProfiles("test")
class ProjetServiceTest {

    @Autowired
    private ProjetService projetService;

    @Autowired
    private JournalService journalService;

    private ProjetFormDTO dtoValide() {
        ProjetFormDTO dto = new ProjetFormDTO();
        dto.setNomProjet("Voyage a Rome");
        dto.setDescription("Voyage culturel");
        dto.setDateDepart(LocalDateTime.now().plusMonths(2));
        dto.setDateRetour(LocalDateTime.now().plusMonths(2).plusDays(4));
        dto.setLieuDepart("College");
        dto.setLieuRetour("College");
        dto.setTransport("Avion");
        dto.setOrganisateurNom("Mme Martin");
        dto.setOrganisateurEmail("martin@college-sthelier.fr");
        dto.setTelephoneOrganisateur("0102030405");
        dto.setClassesConcernees("4A");
        dto.setEffectif(25);
        dto.setCoutGlobal(new BigDecimal("2500"));
        dto.setCoutParEleve(new BigDecimal("100"));
        dto.setMontantSubvention(BigDecimal.ZERO);
        return dto;
    }

    private void connecterEnTantQue(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        Authentication authentication = new TestingAuthenticationToken(email, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void nettoyerContexteSecurite() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lesChampsOrganismeEtCommentaireSontFacultatifsEtBienPersistesQuandRenseignes() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");

        // Facultatifs : la creation reussit sans eux (dtoValide() ne les renseigne pas).
        Projet sansOrganisme = projetService.creerProjet(dtoValide());
        assertThat(sansOrganisme.getOrganismeNom()).isNull();
        assertThat(sansOrganisme.getCommentaire()).isNull();

        // Quand ils sont renseignes, l'aller-retour DTO <-> Entite les conserve.
        ProjetFormDTO dto = dtoValide();
        dto.setOrganismeNom("Voyages Culture Plus");
        dto.setOrganismeTelephone("0102030405");
        dto.setOrganismeEmail("contact@voyages-culture-plus.fr");
        dto.setCommentaire("Prevoir un accueil adapte pour un eleve en fauteuil.");

        Long id = projetService.creerProjet(dto).getId();
        ProjetFormDTO releve = projetService.chargerFormulaire(id);

        assertThat(releve.getOrganismeNom()).isEqualTo("Voyages Culture Plus");
        assertThat(releve.getOrganismeTelephone()).isEqualTo("0102030405");
        assertThat(releve.getOrganismeEmail()).isEqualTo("contact@voyages-culture-plus.fr");
        assertThat(releve.getCommentaire()).isEqualTo("Prevoir un accueil adapte pour un eleve en fauteuil.");
    }

    @Test
    void leWorkflowLineaireCompletFaitProgresserLeStatutEtHorodateChaqueEtape() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Projet projet = projetService.creerProjet(dtoValide());
        assertThat(projet.getStatut()).isEqualTo(StatutProjet.BROUILLON);

        projetService.soumettre(projet.getId());
        Projet apresSoumission = projetService.trouverParId(projet.getId());
        assertThat(apresSoumission.getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_COMPTA);
        assertThat(apresSoumission.getDateValidationProf()).isNotNull();
    }

    @Test
    void laValidationCompletePasseParTousLesStatutsJusquaValide() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);
        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE);

        connecterEnTantQue("viesco@college-sthelier.fr", "ROLE_VIESCO");
        projetService.validerVieScolaire(id);
        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_DIRECTION);

        connecterEnTantQue("direction@college-sthelier.fr", "ROLE_DIRECTION");
        projetService.validerDirection(id);
        Projet valide = projetService.trouverParId(id);
        assertThat(valide.getStatut()).isEqualTo(StatutProjet.VALIDE);
        assertThat(valide.getDateValidationCompta()).isNotNull();
        assertThat(valide.getDateValidationVieScolaire()).isNotNull();
        assertThat(valide.getDateValidationDirection()).isNotNull();
    }

    @Test
    void unAdminPeutValiderNimporteQuelleEtapeALaPlaceDuRoleMetier() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        // Un ADMIN peut debloquer un dossier a n'importe quelle etape, sans
        // avoir a endosser le role metier (COMPTA/VIESCO/DIRECTION).
        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        projetService.validerCompta(id);
        projetService.validerVieScolaire(id);
        projetService.validerDirection(id);

        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.VALIDE);
    }

    @Test
    void unRefusParVieScolaireConserveLaValidationComptaDejaObtenue() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);

        connecterEnTantQue("viesco@college-sthelier.fr", "ROLE_VIESCO");
        projetService.refuser(id, "Effectif incoherent avec les autorisations de sortie.");

        Projet refuse = projetService.trouverParId(id);
        assertThat(refuse.getStatut()).isEqualTo(StatutProjet.A_CORRIGER);
        assertThat(refuse.getMotifRefus()).isEqualTo("Effectif incoherent avec les autorisations de sortie.");
        // La validation comptable, obtenue avant ce refus, est conservee :
        // elle ne sera pas redemandee a la resoumission.
        assertThat(refuse.getDateValidationCompta()).isNotNull();
        assertThat(refuse.getDateValidationVieScolaire()).isNull();
    }

    @Test
    void laResoumissionRepredALEtapeQuiARefuseSansFaireRevaliderComptaDejaValide() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);
        LocalDateTime dateValidationComptaInitiale = projetService.trouverParId(id).getDateValidationCompta();

        connecterEnTantQue("viesco@college-sthelier.fr", "ROLE_VIESCO");
        projetService.refuser(id, "A revoir.");

        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        projetService.soumettre(id);

        Projet resoumis = projetService.trouverParId(id);
        // Reprend directement a Vie Scolaire : pas de retour a Comptabilite.
        assertThat(resoumis.getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE);
        assertThat(resoumis.getMotifRefus()).isNull();
        assertThat(resoumis.getDateValidationCompta()).isEqualTo(dateValidationComptaInitiale);
    }

    @Test
    void unRefusDesLaComptabiliteRepredBienAComptabiliteALaResoumission() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.refuser(id, "Devis manquant.");

        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        projetService.soumettre(id);

        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_COMPTA);
    }

    @Test
    void uneTransitionHorsSequenceEstRejetee() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();

        // Le projet est encore en BROUILLON : la validation comptable directe
        // (par un utilisateur qui a pourtant bien le role COMPTA) doit echouer
        // sur la regle metier, pas sur les droits d'acces.
        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        assertThatThrownBy(() -> projetService.validerCompta(id)).isInstanceOf(TransitionInvalideException.class);
    }

    @Test
    void unProfNePeutPasModifierLeDossierDunCollegue() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        Projet projet = projetService.trouverParId(id);

        connecterEnTantQue("autre.prof@college-sthelier.fr", "ROLE_PROF");
        ProjetFormDTO dto = dtoValide();
        dto.setVersion(projet.getVersion());
        assertThatThrownBy(() -> projetService.modifierProjet(id, dto))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unUtilisateurEnLectureSeulePeutConsulterMaisNeCreeNiNeValideRien() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("secretariat@college-sthelier.fr", "ROLE_LECTURE_SEULE");

        // La consultation reste ouverte a tout utilisateur authentifie.
        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_COMPTA);
        assertThat(projetService.projetsPourTableauDeBord()).isNotEmpty();

        // Mais aucune action de creation ou de workflow ne lui est ouverte.
        assertThatThrownBy(() -> projetService.creerProjet(dtoValide())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projetService.validerCompta(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projetService.dupliquer(id)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unProfNePeutNiArchiverNiSupprimerUnDossier() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();

        assertThatThrownBy(() -> projetService.archiver(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projetService.supprimerDefinitivement(id)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unAdminPeutArchiverUnDossierQuiDisparaitDuTableauDeBordEtLeDesarchiver() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();

        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        projetService.archiver(id);

        assertThat(projetService.projetsPourTableauDeBord().get(StatutProjet.BROUILLON))
                .extracting(Projet::getId).doesNotContain(id);
        assertThat(projetService.listerArchives()).extracting(Projet::getId).contains(id);

        projetService.desarchiver(id);
        assertThat(projetService.projetsPourTableauDeBord().get(StatutProjet.BROUILLON))
                .extracting(Projet::getId).contains(id);
        assertThat(projetService.listerArchives()).extracting(Projet::getId).doesNotContain(id);
    }

    @Test
    void unAdminPeutSupprimerDefinitivementUnDossier() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();

        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        projetService.supprimerDefinitivement(id);

        assertThatThrownBy(() -> projetService.trouverParId(id)).isInstanceOf(ProjetNotFoundException.class);
    }

    @Test
    void seulUnAdminPeutModifierUnDossierDejaValide() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);
        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);
        connecterEnTantQue("viesco@college-sthelier.fr", "ROLE_VIESCO");
        projetService.validerVieScolaire(id);
        connecterEnTantQue("direction@college-sthelier.fr", "ROLE_DIRECTION");
        projetService.validerDirection(id);
        Projet valide = projetService.trouverParId(id);

        // Un prof (meme organisateur) ne peut plus toucher a un dossier valide.
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        ProjetFormDTO dto = dtoValide();
        dto.setVersion(valide.getVersion());
        dto.setNomProjet("Tentative de modification par le prof");
        assertThatThrownBy(() -> projetService.modifierProjet(id, dto)).isInstanceOf(TransitionInvalideException.class);

        // Un admin, si : correction exceptionnelle apres validation. Un
        // admin recoit aussi ROLE_PROF en production (CustomOAuth2UserService) :
        // modifierProjet() l'exige au niveau @PreAuthorize.
        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN", "ROLE_PROF");
        dto.setNomProjet("Correction admin post-validation");
        projetService.modifierProjet(id, dto);
        assertThat(projetService.trouverParId(id).getNomProjet()).isEqualTo("Correction admin post-validation");
    }

    @Test
    void unAdminPeutReaffecterLOrganisateurDunDossier() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();

        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        projetService.reaffecterOrganisateur(id, "remplacant@college-sthelier.fr", "Mme Remplacante");

        Projet projet = projetService.trouverParId(id);
        assertThat(projet.getOrganisateurEmail()).isEqualTo("remplacant@college-sthelier.fr");
        assertThat(projet.getOrganisateurNom()).isEqualTo("Mme Remplacante");
    }

    @Test
    void unDossierSoumisApparaitDansLesDossiersBloquesAvecSonAncienneteEnJours() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        assertThat(projetService.listerDossiersBloques())
                .filteredOn(dossier -> dossier.id().equals(id))
                .singleElement()
                .satisfies(dossier -> {
                    assertThat(dossier.statut()).isEqualTo(StatutProjet.EN_ATTENTE_COMPTA);
                    assertThat(dossier.joursEnAttente()).isGreaterThanOrEqualTo(0);
                });
    }

    @Test
    void laRechercheAdminTrouveUnDossierArchiveInvisibleDuTableauDeBord() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        ProjetFormDTO dto = dtoValide();
        dto.setNomProjet("Voyage a Kyoto");
        dto.setClassesConcernees("3B");
        Long id = projetService.creerProjet(dto).getId();

        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        projetService.archiver(id);

        assertThat(projetService.rechercherPourAdmin("kyoto", null, null, null, null))
                .extracting(Projet::getId).contains(id);
        assertThat(projetService.rechercherPourAdmin(null, null, "3B", null, true))
                .extracting(Projet::getId).contains(id);
        assertThat(projetService.rechercherPourAdmin(null, null, null, null, false))
                .extracting(Projet::getId).doesNotContain(id);
    }

    @Test
    void chaqueActionMajeureLaisseUneTraceDansLeJournalDaudit() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        projetService.archiver(id);
        projetService.desarchiver(id);

        List<String> actions = journalService.listerRecentes().stream()
                .filter(entree -> id.equals(entree.getProjetId()))
                .map(JournalEntree::getAction)
                .toList();

        assertThat(actions).contains("Création", "Soumission", "Archivage", "Désarchivage");
    }

    @Test
    void uneModificationAvecUneVersionPerimeeEstRejetee() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Projet projet = projetService.creerProjet(dtoValide());
        Long id = projet.getId();

        // Le professeur ouvre le formulaire (version courante), puis une
        // premiere modification aboutit et fait progresser la version...
        ProjetFormDTO premiereModification = dtoValide();
        premiereModification.setVersion(projet.getVersion());
        premiereModification.setNomProjet("Voyage a Rome (premiere modification)");
        projetService.modifierProjet(id, premiereModification);

        // ...puis une seconde soumission, restee sur l'ancienne version
        // (onglet du navigateur reste ouvert sur le formulaire d'origine),
        // doit etre rejetee au lieu d'ecraser silencieusement le premier
        // changement.
        ProjetFormDTO secondeModificationPerimee = dtoValide();
        secondeModificationPerimee.setVersion(projet.getVersion());
        secondeModificationPerimee.setNomProjet("Voyage a Rome (ecrasement non souhaite)");

        assertThatThrownBy(() -> projetService.modifierProjet(id, secondeModificationPerimee))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
