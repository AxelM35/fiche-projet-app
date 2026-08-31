package fr.ficheprojet.service;

import fr.ficheprojet.dto.ProjetFormDTO;
import fr.ficheprojet.exception.ProjetNotFoundException;
import fr.ficheprojet.exception.TransitionInvalideException;
import fr.ficheprojet.model.JournalEntree;
import fr.ficheprojet.model.Projet;
import fr.ficheprojet.model.StatutProjet;
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
 * Vérifie le workflow linéaire complet ainsi que les deux garde-fous de
 * concurrence/sécurité : verrouillage optimiste et contrôle de propriété
 * d'un dossier. Le contexte de sécurité est basculé manuellement d'un rôle
 * à l'autre au sein d'un même test (@WithMockUser ne s'applique qu'au
 * démarrage d'une méthode @Test, pas à des appels internes).
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
        dto.setOrganisateurEmail("martin@exemple.fr");
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
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");

        // Facultatifs : la création réussit sans eux (dtoValide() ne les renseigne pas).
        Projet sansOrganisme = projetService.creerProjet(dtoValide());
        assertThat(sansOrganisme.getOrganismeNom()).isNull();
        assertThat(sansOrganisme.getCommentaire()).isNull();

        // Quand ils sont renseignés, l'aller-retour DTO <-> Entité les conserve.
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
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Projet projet = projetService.creerProjet(dtoValide());
        assertThat(projet.getStatut()).isEqualTo(StatutProjet.BROUILLON);

        projetService.soumettre(projet.getId());
        Projet apresSoumission = projetService.trouverParId(projet.getId());
        assertThat(apresSoumission.getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_COMPTA);
        assertThat(apresSoumission.getDateValidationProf()).isNotNull();
    }

    @Test
    void laValidationCompletePasseParTousLesStatutsJusquaValide() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@exemple.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);
        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE);

        connecterEnTantQue("viesco@exemple.fr", "ROLE_VIESCO");
        projetService.validerVieScolaire(id);
        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_DIRECTION);

        connecterEnTantQue("direction@exemple.fr", "ROLE_DIRECTION");
        projetService.validerDirection(id);
        Projet valide = projetService.trouverParId(id);
        assertThat(valide.getStatut()).isEqualTo(StatutProjet.VALIDE);
        assertThat(valide.getDateValidationCompta()).isNotNull();
        assertThat(valide.getDateValidationVieScolaire()).isNotNull();
        assertThat(valide.getDateValidationDirection()).isNotNull();
    }

    /**
     * "Je ne connais pas encore le budget" (voir ProjetFormDTO.budgetInconnu,
     * audit UX S4bis) : un dossier peut être soumis sans budget connu, mais
     * la Comptabilité ne peut pas le valider tant qu'il manque - completerBudget
     * (même périmètre d'autorisation que le lien Drive) permet de le
     * renseigner après coup, y compris par la Comptabilité elle-même,
     * puisque le formulaire principal n'a plus de bouton "Enregistrer" une
     * fois le dossier engagé dans le circuit.
     */
    @Test
    void unDossierSansBudgetPeutEtreSoumisMaisPasValideTantQuIlManque() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        ProjetFormDTO dto = dtoValide();
        dto.setCoutGlobal(null);
        dto.setCoutParEleve(null);
        dto.setMontantSubvention(null);
        Long id = projetService.creerProjet(dto).getId();
        projetService.soumettre(id);
        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_COMPTA);

        connecterEnTantQue("compta@exemple.fr", "ROLE_COMPTA");
        assertThatThrownBy(() -> projetService.validerCompta(id)).isInstanceOf(TransitionInvalideException.class);
    }

    @Test
    void completerBudgetPermetALaComptabiliteDeRenseignerPuisDeValider() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        ProjetFormDTO dto = dtoValide();
        dto.setCoutGlobal(null);
        dto.setCoutParEleve(null);
        Long id = projetService.creerProjet(dto).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@exemple.fr", "ROLE_PROF", "ROLE_COMPTA");
        projetService.completerBudget(id, new BigDecimal("2000"), new BigDecimal("80"), BigDecimal.ZERO);
        Projet complete = projetService.trouverParId(id);
        assertThat(complete.getCoutGlobal()).isEqualByComparingTo("2000");
        assertThat(complete.getCoutParEleve()).isEqualByComparingTo("80");

        projetService.validerCompta(id);
        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE);
    }

    @Test
    void completerBudgetEstReserveALorganisateurOuAUnRoleDeValidation() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        ProjetFormDTO dto = dtoValide();
        dto.setCoutGlobal(null);
        dto.setCoutParEleve(null);
        Long id = projetService.creerProjet(dto).getId();

        connecterEnTantQue("collegue@exemple.fr", "ROLE_PROF");
        assertThatThrownBy(() -> projetService.completerBudget(id, new BigDecimal("2000"), new BigDecimal("80"), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unAdminPeutValiderNimporteQuelleEtapeALaPlaceDuRoleMetier() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        // Un ADMIN peut débloquer un dossier à n'importe quelle étape, sans
        // avoir à endosser le rôle métier (COMPTA/VIESCO/DIRECTION).
        connecterEnTantQue("admin@exemple.fr", "ROLE_ADMIN");
        projetService.validerCompta(id);
        projetService.validerVieScolaire(id);
        projetService.validerDirection(id);

        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.VALIDE);
    }

    @Test
    void unRefusParVieScolaireConserveLaValidationComptaDejaObtenue() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@exemple.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);

        connecterEnTantQue("viesco@exemple.fr", "ROLE_VIESCO");
        projetService.refuser(id, "Effectif incoherent avec les autorisations de sortie.");

        Projet refuse = projetService.trouverParId(id);
        assertThat(refuse.getStatut()).isEqualTo(StatutProjet.A_CORRIGER);
        assertThat(refuse.getMotifRefus()).isEqualTo("Effectif incoherent avec les autorisations de sortie.");
        // La validation comptable, obtenue avant ce refus, est conservée :
        // elle ne sera pas redemandée à la resoumission.
        assertThat(refuse.getDateValidationCompta()).isNotNull();
        assertThat(refuse.getDateValidationVieScolaire()).isNull();
    }

    @Test
    void laResoumissionRepredALEtapeQuiARefuseSansFaireRevaliderComptaDejaValide() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@exemple.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);
        LocalDateTime dateValidationComptaInitiale = projetService.trouverParId(id).getDateValidationCompta();

        connecterEnTantQue("viesco@exemple.fr", "ROLE_VIESCO");
        projetService.refuser(id, "A revoir.");

        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        projetService.soumettre(id);

        Projet resoumis = projetService.trouverParId(id);
        // Reprend directement à Vie Scolaire : pas de retour à Comptabilité.
        assertThat(resoumis.getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE);
        assertThat(resoumis.getMotifRefus()).isNull();
        assertThat(resoumis.getDateValidationCompta()).isEqualTo(dateValidationComptaInitiale);
    }

    @Test
    void unRefusDesLaComptabiliteRepredBienAComptabiliteALaResoumission() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@exemple.fr", "ROLE_COMPTA");
        projetService.refuser(id, "Devis manquant.");

        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        projetService.soumettre(id);

        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_COMPTA);
    }

    @Test
    void uneTransitionHorsSequenceEstRejetee() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();

        // Le projet est encore en BROUILLON : la validation comptable directe
        // (par un utilisateur qui a pourtant bien le rôle COMPTA) doit échouer
        // sur la règle métier, pas sur les droits d'accès.
        connecterEnTantQue("compta@exemple.fr", "ROLE_COMPTA");
        assertThatThrownBy(() -> projetService.validerCompta(id)).isInstanceOf(TransitionInvalideException.class);
    }

    @Test
    void unProfNePeutPasModifierLeDossierDunCollegue() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        Projet projet = projetService.trouverParId(id);

        connecterEnTantQue("autre.prof@exemple.fr", "ROLE_PROF");
        ProjetFormDTO dto = dtoValide();
        dto.setVersion(projet.getVersion());
        assertThatThrownBy(() -> projetService.modifierProjet(id, dto))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unUtilisateurEnLectureSeulePeutConsulterMaisNeCreeNiNeValideRien() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("secretariat@exemple.fr", "ROLE_LECTURE_SEULE");

        // La consultation reste ouverte à tout utilisateur authentifié.
        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_COMPTA);
        assertThat(projetService.projetsPourTableauDeBord()).isNotEmpty();

        // Mais aucune action de création ou de workflow ne lui est ouverte.
        assertThatThrownBy(() -> projetService.creerProjet(dtoValide())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projetService.validerCompta(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projetService.dupliquer(id)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unProfNePeutNiArchiverNiSupprimerUnDossier() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();

        assertThatThrownBy(() -> projetService.archiver(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projetService.supprimerDefinitivement(id)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unAdminPeutArchiverUnDossierQuiDisparaitDuTableauDeBordEtLeDesarchiver() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();

        connecterEnTantQue("admin@exemple.fr", "ROLE_ADMIN");
        projetService.archiver(id);

        assertThat(projetService.projetsPourTableauDeBord().get(StatutProjet.BROUILLON))
                .extracting(Projet::getId).doesNotContain(id);
        assertThat(projetService.listerArchives(null)).extracting(Projet::getId).contains(id);

        projetService.desarchiver(id);
        assertThat(projetService.projetsPourTableauDeBord().get(StatutProjet.BROUILLON))
                .extracting(Projet::getId).contains(id);
        assertThat(projetService.listerArchives(null)).extracting(Projet::getId).doesNotContain(id);
    }

    @Test
    void unAdminPeutSupprimerDefinitivementUnDossier() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();

        connecterEnTantQue("admin@exemple.fr", "ROLE_ADMIN");
        projetService.supprimerDefinitivement(id);

        assertThatThrownBy(() -> projetService.trouverParId(id)).isInstanceOf(ProjetNotFoundException.class);
    }

    @Test
    void seulUnAdminPeutModifierUnDossierDejaValide() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);
        connecterEnTantQue("compta@exemple.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);
        connecterEnTantQue("viesco@exemple.fr", "ROLE_VIESCO");
        projetService.validerVieScolaire(id);
        connecterEnTantQue("direction@exemple.fr", "ROLE_DIRECTION");
        projetService.validerDirection(id);
        Projet valide = projetService.trouverParId(id);

        // Un prof (même organisateur) ne peut plus toucher à un dossier validé.
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        ProjetFormDTO dto = dtoValide();
        dto.setVersion(valide.getVersion());
        dto.setNomProjet("Tentative de modification par le prof");
        assertThatThrownBy(() -> projetService.modifierProjet(id, dto)).isInstanceOf(TransitionInvalideException.class);

        // Un admin, si : correction exceptionnelle après validation. Un
        // admin reçoit aussi ROLE_PROF en production (CustomOAuth2UserService) :
        // modifierProjet() l'exige au niveau @PreAuthorize.
        connecterEnTantQue("admin@exemple.fr", "ROLE_ADMIN", "ROLE_PROF");
        dto.setNomProjet("Correction admin post-validation");
        projetService.modifierProjet(id, dto);
        assertThat(projetService.trouverParId(id).getNomProjet()).isEqualTo("Correction admin post-validation");
    }

    @Test
    void unAdminPeutReaffecterLOrganisateurDunDossier() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();

        connecterEnTantQue("admin@exemple.fr", "ROLE_ADMIN");
        projetService.reaffecterOrganisateur(id, "remplacant@exemple.fr", "Mme Remplacante");

        Projet projet = projetService.trouverParId(id);
        assertThat(projet.getOrganisateurEmail()).isEqualTo("remplacant@exemple.fr");
        assertThat(projet.getOrganisateurNom()).isEqualTo("Mme Remplacante");
    }

    @Test
    void unDossierSoumisApparaitDansLesDossiersBloquesAvecSonAncienneteEnJours() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
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
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        ProjetFormDTO dto = dtoValide();
        dto.setNomProjet("Voyage a Kyoto");
        dto.setClassesConcernees("3B");
        Long id = projetService.creerProjet(dto).getId();

        connecterEnTantQue("admin@exemple.fr", "ROLE_ADMIN");
        projetService.archiver(id);

        assertThat(projetService.rechercherPourAdmin("kyoto", null, null, null, null, null))
                .extracting(Projet::getId).contains(id);
        assertThat(projetService.rechercherPourAdmin(null, null, "3B", null, true, null))
                .extracting(Projet::getId).contains(id);
        assertThat(projetService.rechercherPourAdmin(null, null, null, null, false, null))
                .extracting(Projet::getId).doesNotContain(id);
    }

    /**
     * Archivage groupe par année scolaire (voir AnneeScolaireUtil) : deux
     * dossiers VALIDE d'années scolaires différentes, seul celui de l'année
     * ciblée doit être archivé.
     */
    @Test
    void archiverDossiersValidesDeLAnneeScolaireNarchiveQueLannéeCiblée() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        ProjetFormDTO dtoAncien = dtoValide();
        dtoAncien.setNomProjet("Voyage a Berlin");
        dtoAncien.setDateDepart(LocalDateTime.of(2024, 10, 10, 8, 0));
        dtoAncien.setDateRetour(LocalDateTime.of(2024, 10, 14, 18, 0));
        Long idAncien = validerCompletement(dtoAncien);

        ProjetFormDTO dtoRecent = dtoValide();
        dtoRecent.setNomProjet("Voyage a Madrid");
        dtoRecent.setDateDepart(LocalDateTime.of(2025, 11, 5, 8, 0));
        dtoRecent.setDateRetour(LocalDateTime.of(2025, 11, 9, 18, 0));
        Long idRecent = validerCompletement(dtoRecent);

        connecterEnTantQue("admin@exemple.fr", "ROLE_ADMIN");
        int nombreArchive = projetService.archiverDossiersValidesDeLAnneeScolaire("2024-2025");

        assertThat(nombreArchive).isEqualTo(1);
        assertThat(projetService.trouverParId(idAncien).isArchive()).isTrue();
        assertThat(projetService.trouverParId(idRecent).isArchive()).isFalse();

        assertThat(projetService.listerArchives("2024-2025")).extracting(Projet::getId).contains(idAncien);
        assertThat(projetService.listerArchives("2025-2026")).extracting(Projet::getId).doesNotContain(idAncien);
    }

    /**
     * Crée un dossier et le fait passer par tout le workflow jusqu'a VALIDE,
     * en reconnectant à chaque étape sous le rôle métier concerne (comme
     * laValidationCompletePasseParTousLesStatutsJusquaValide), pour tester
     * l'archivage groupé sur des dossiers réalistes.
     */
    private Long validerCompletement(ProjetFormDTO dto) {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dto).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@exemple.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);

        connecterEnTantQue("viesco@exemple.fr", "ROLE_VIESCO");
        projetService.validerVieScolaire(id);

        connecterEnTantQue("direction@exemple.fr", "ROLE_DIRECTION");
        projetService.validerDirection(id);
        return id;
    }

    @Test
    void chaqueActionMajeureLaisseUneTraceDansLeJournalDaudit() {
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("admin@exemple.fr", "ROLE_ADMIN");
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
        connecterEnTantQue("martin@exemple.fr", "ROLE_PROF");
        Projet projet = projetService.creerProjet(dtoValide());
        Long id = projet.getId();

        // Le professeur ouvre le formulaire (version courante), puis une
        // première modification aboutit et fait progresser la version...
        ProjetFormDTO premiereModification = dtoValide();
        premiereModification.setVersion(projet.getVersion());
        premiereModification.setNomProjet("Voyage a Rome (premiere modification)");
        projetService.modifierProjet(id, premiereModification);

        // ...puis une seconde soumission, restée sur l'ancienne version
        // (onglet du navigateur reste ouvert sur le formulaire d'origine),
        // doit être rejetée au lieu d'écraser silencieusement le premier
        // changement.
        ProjetFormDTO secondeModificationPerimee = dtoValide();
        secondeModificationPerimee.setVersion(projet.getVersion());
        secondeModificationPerimee.setNomProjet("Voyage a Rome (ecrasement non souhaite)");

        assertThatThrownBy(() -> projetService.modifierProjet(id, secondeModificationPerimee))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
