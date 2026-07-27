package fr.collegesthelier.ficheprojet.web;

import fr.collegesthelier.ficheprojet.dto.ProjetFormDTO;
import fr.collegesthelier.ficheprojet.model.Projet;
import fr.collegesthelier.ficheprojet.service.ProjetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Tests d'integration verifiant que les vues Thymeleaf (dashboard,
 * formulaire) se rendent correctement et que le workflow de creation
 * fonctionne de bout en bout, sur une base H2 en memoire.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjetService projetService;

    private void connecterEnTantQue(String email, String role) {
        Authentication authentication = new TestingAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void nettoyerContexteSecurite() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Avec un seul fournisseur OAuth2 (Google), Spring Security saute la
     * page de connexion generee automatiquement et redirige directement
     * vers /oauth2/authorization/google : sans page /login explicite
     * (SecurityConfig.loginPage + LoginController), cette URL n'existe
     * plus du tout (404), notamment apres une deconnexion qui y redirige.
     */
    @Test
    void laPageDeConnexionSAfficheSansAuthentification() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void leTableauDeBordSAffiche() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    /**
     * Regression : la navbar utilisait navbar-expand-lg sans bouton
     * hamburger ni <div class="collapse">, donc sans aucun moyen de la
     * replier sous 992px (voir fragments/navbar.html) - verifie que le
     * couple toggler/collapse est bien present.
     */
    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void laNavbarEstRepliableSurMobile() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("navbar-toggler")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("collapse navbar-collapse")));
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void leDashboardChargeLeScriptDesSpinnersDeValidation() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/boutons-validation.js")));
    }

    /**
     * Aide contextuelle (onboarding) : le bouton "Comment ca marche ?" et la
     * modale associee ne s'affichent qu'a un Prof (public vise, cf. cahier
     * des charges) - un role de validation sans PROF ne doit rien en voir.
     */
    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void leDashboardExposeLaideContextuellePourUnProf() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"modalOnboarding\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Comment ça marche ?")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/onboarding.js")));
    }

    @Test
    @WithMockUser(username = "secretariat@college-sthelier.fr", authorities = "ROLE_LECTURE_SEULE")
    void leDashboardNexposePasLaideContextuellePourUnRoleSansProf() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(resultat.getResponse().getContentAsString()).doesNotContain("id=\"modalOnboarding\"");
    }

    /**
     * Filtres avances du dashboard (classe, organisateur, periode de
     * depart) : verifie que les champs de filtre sont bien presents et que
     * chaque carte porte les attributs data-* necessaires au filtrage cote
     * client (voir dashboard.js).
     */
    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void leDashboardExposeLesFiltresAvancesEtLesDonneesDesCartes() throws Exception {
        ProjetFormDTO dto = dtoBase();
        projetService.creerProjet(dto);
        String dateDepartAttendue = dto.getDateDepart().toLocalDate().toString();

        MvcResult resultat = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn();
        String html = resultat.getResponse().getContentAsString();

        assertThat(html).contains("id=\"filtreMesDossiers\"", "id=\"filtreClasse\"", "id=\"filtreOrganisateur\"",
                "id=\"filtreDateDepartDebut\"", "id=\"filtreDateDepartFin\"");
        assertThat(html).contains("data-classe=\"6a\"", "data-organisateur=\"m. prof\"",
                "data-date-depart=\"" + dateDepartAttendue + "\"", "data-mon-dossier=\"true\"");
    }

    /**
     * Le filtre "Mes dossiers uniquement" est coche par defaut pour un Prof
     * sans role de validation (le seul public pour qui le Kanban complet de
     * l'etablissement n'est jamais le point de depart utile), mais pas pour
     * un role de validation qui doit voir tous les dossiers des l'arrivee.
     */
    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void leFiltreMesDossiersEstCocheParDefautPourUnProfSeul() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"filtreMesDossiers\" checked=\"checked\"")));
    }

    @Test
    @WithMockUser(username = "compta@college-sthelier.fr", authorities = {"ROLE_PROF", "ROLE_COMPTA"})
    void leFiltreMesDossiersNestPasCocheParDefautPourUnRoleDeValidation() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(resultat.getResponse().getContentAsString())
                .doesNotContain("id=\"filtreMesDossiers\" checked=\"checked\"");
    }

    /**
     * Regression : les formulaires "Archiver" et "Supprimer définitivement"
     * de la modale de gestion admin n'avaient pas de th:action (l'URL reelle
     * est posee par dashboard.js au clic) - sans th:action, l'extension
     * Thymeleaf Spring Security n'injecte pas le jeton CSRF automatiquement,
     * ce qui faisait echouer la soumission en 403 dans un vrai navigateur
     * (MockMvc + .with(csrf()) ne l'aurait pas detecte, d'ou cette assertion
     * directe sur le HTML rendu).
     */
    @Test
    @WithMockUser(username = "amorvan@college-sthelier.fr", authorities = {"ROLE_PROF", "ROLE_ADMIN"})
    void lesFormulairesDeGestionAdminContiennentLeJetonCsrf() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn();
        String html = resultat.getResponse().getContentAsString();

        assertThat(extraireFormulaire(html, "formArchiverProjet")).contains("name=\"_csrf\"");
        assertThat(extraireFormulaire(html, "formSupprimerProjet")).contains("name=\"_csrf\"");
    }

    private String extraireFormulaire(String html, String idFormulaire) {
        int debut = html.indexOf("id=\"" + idFormulaire + "\"");
        int fin = html.indexOf("</form>", debut);
        return html.substring(debut, fin);
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void leFormulaireDeCreationSAffiche() throws Exception {
        mockMvc.perform(get("/projets/nouveau"))
                .andExpect(status().isOk())
                .andExpect(view().name("formulaire"));
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void laCreationDunProjetValideRedirigeVersSaFiche() throws Exception {
        mockMvc.perform(post("/projets/nouveau")
                        .with(csrf())
                        .param("nomProjet", "Voyage a Londres")
                        .param("description", "Sejour linguistique")
                        .param("dateDepart", "2026-10-01T08:00")
                        .param("dateRetour", "2026-10-05T18:00")
                        .param("lieuDepart", "Collège Saint-Helier")
                        .param("lieuRetour", "Collège Saint-Helier")
                        .param("transport", "Car")
                        .param("organisateurNom", "M. Dupont")
                        .param("organisateurEmail", "dupont@college-sthelier.fr")
                        .param("telephoneOrganisateur", "0102030405")
                        .param("classesConcernees", "5A, 5B")
                        .param("effectif", "30")
                        .param("coutGlobal", "3000")
                        .param("coutParEleve", "100")
                        .param("montantSubvention", "0"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void laCreationDunProjetInvalideReaffichleFormulaireAvecErreurs() throws Exception {
        mockMvc.perform(post("/projets/nouveau").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("formulaire"));
    }

    /**
     * Clarte du formulaire (audit UX, docs/CAHIER_DES_CHARGES.md S4bis) :
     * legende des champs obligatoires toujours visible, et resume d'erreurs
     * en haut de page uniquement affiche apres un echec de soumission (pas
     * sur un formulaire vierge).
     */
    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void leFormulaireVierneAfficheJamaisLeResumeDerreurs() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/projets/nouveau"))
                .andExpect(status().isOk())
                .andReturn();
        String html = resultat.getResponse().getContentAsString();

        assertThat(html).contains("Champs obligatoires");
        assertThat(html).doesNotContain("id=\"resumeErreursFormulaire\"");
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void laCreationDunProjetInvalideAfficheLeResumeDerreursEnHautDePage() throws Exception {
        MvcResult resultat = mockMvc.perform(post("/projets/nouveau").with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(resultat.getResponse().getContentAsString()).contains("id=\"resumeErreursFormulaire\"");
    }

    /**
     * Reproduit le scenario signale en test manuel : ouvrir la fiche d'un
     * projet DEJA enregistre (et non un formulaire vierge) declenchait un
     * org.hibernate.LazyInitializationException sur la collection
     * accompagnateurs, chargee en lazy, car trouverParId(id) et versDTO(...)
     * s'executaient dans deux transactions distinctes.
     */
    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void laFicheDunProjetExistantAvecAccompagnateursSAffiche() throws Exception {
        MvcResult creation = mockMvc.perform(post("/projets/nouveau")
                        .with(csrf())
                        .param("nomProjet", "Voyage a Berlin")
                        .param("dateDepart", "2026-11-01T08:00")
                        .param("dateRetour", "2026-11-05T18:00")
                        .param("lieuDepart", "Collège Saint-Helier")
                        .param("lieuRetour", "Collège Saint-Helier")
                        .param("transport", "Avion")
                        .param("organisateurNom", "Mme Petit")
                        .param("organisateurEmail", "prof@college-sthelier.fr")
                        .param("telephoneOrganisateur", "0102030405")
                        .param("classesConcernees", "3A")
                        .param("effectif", "20")
                        .param("accompagnateurs", "M. Dupont")
                        .param("accompagnateurs", "Mme Durand")
                        .param("coutGlobal", "4000")
                        .param("coutParEleve", "200")
                        .param("montantSubvention", "0"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectedUrl = creation.getResponse().getRedirectedUrl();
        assertThat(redirectedUrl).isNotNull();

        mockMvc.perform(get(redirectedUrl))
                .andExpect(status().isOk())
                .andExpect(view().name("formulaire"));
    }

    @Test
    @WithMockUser(username = "secretariat@college-sthelier.fr", authorities = "ROLE_LECTURE_SEULE")
    void unUtilisateurEnLectureSeuleVoitLaConsultationMemeSurUnBrouillon() throws Exception {
        connecterEnTantQue("prof@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoBase()).getId();

        // La bascule de role ci-dessus (necessaire pour creer le projet en
        // tant que prof) a "ecrase" le contexte pose par @WithMockUser : on
        // le restaure avant la requete HTTP, comme dans creerEtValiderCompletement().
        connecterEnTantQue("secretariat@college-sthelier.fr", "ROLE_LECTURE_SEULE");
        mockMvc.perform(get("/projets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("consultation"));
    }

    /**
     * Regression : avant correctif, un Prof qui n'etait ni l'organisateur du
     * dossier ni un role de validation recevait quand meme le formulaire
     * editable (champs non verrouilles) d'un dossier d'un collegue, alors que
     * l'enregistrement aurait de toute facon ete refuse cote service
     * (verifierDroitModification). Seule la consultation doit s'afficher.
     */
    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void unProfNonOrganisateurVoitLaConsultationSurLeDossierDunCollegue() throws Exception {
        connecterEnTantQue("collegue@college-sthelier.fr", "ROLE_PROF");
        ProjetFormDTO dto = dtoBase();
        dto.setOrganisateurEmail("collegue@college-sthelier.fr");
        Long id = projetService.creerProjet(dto).getId();

        connecterEnTantQue("prof@college-sthelier.fr", "ROLE_PROF");
        mockMvc.perform(get("/projets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("consultation"));
    }

    /**
     * Le bouton "Soumettre pour validation" poste desormais (via formaction)
     * vers preparer-soumission, qui enregistre le formulaire puis redirige
     * vers le recapitulatif : verifie ce redirect, que la page s'affiche, et
     * que "Confirmer et soumettre" fait bien avancer le statut.
     */
    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void preparerLaSoumissionEnregistreEtRedirigeVersLeRecapitulatif() throws Exception {
        Long id = projetService.creerProjet(dtoBase()).getId();

        MvcResult preparation = mockMvc.perform(post("/projets/{id}/preparer-soumission", id)
                        .with(csrf())
                        .param("nomProjet", "Voyage a Barcelone (modifie)")
                        .param("dateDepart", "2026-11-01T08:00")
                        .param("dateRetour", "2026-11-05T18:00")
                        .param("lieuDepart", "College")
                        .param("lieuRetour", "College")
                        .param("transport", "Car")
                        .param("organisateurNom", "M. Prof")
                        .param("organisateurEmail", "prof@college-sthelier.fr")
                        .param("telephoneOrganisateur", "0102030405")
                        .param("classesConcernees", "6A")
                        .param("effectif", "28")
                        .param("coutGlobal", "1500")
                        .param("coutParEleve", "50")
                        .param("montantSubvention", "0"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(preparation.getResponse().getRedirectedUrl()).isEqualTo("/projets/" + id + "/recapitulatif");
        assertThat(projetService.trouverParId(id).getNomProjet()).isEqualTo("Voyage a Barcelone (modifie)");

        mockMvc.perform(get("/projets/{id}/recapitulatif", id))
                .andExpect(status().isOk())
                .andExpect(view().name("recapitulatif"));

        mockMvc.perform(post("/projets/{id}/soumettre", id).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(projetService.trouverParId(id).getStatut().name()).isEqualTo("EN_ATTENTE_COMPTA");
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void preparerLaSoumissionAvecDonneesInvalidesReaffichleFormulaireAvecErreurs() throws Exception {
        Long id = projetService.creerProjet(dtoBase()).getId();

        mockMvc.perform(post("/projets/{id}/preparer-soumission", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("formulaire"));
    }

    /**
     * Un dossier deja engage dans le circuit de validation n'a plus rien a
     * relire avant soumission : le recapitulatif redirige simplement vers la
     * fiche plutot que d'afficher une page vide de sens pour ce statut.
     */
    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void leRecapitulatifDunDossierDejaValideRedirigeVersLaFiche() throws Exception {
        Long id = creerEtValiderCompletement();

        mockMvc.perform(get("/projets/{id}/recapitulatif", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/projets/" + id));
    }

    /**
     * Le bouton "Exporter en PDF" doit fonctionner quel que soit le statut du
     * dossier (ici un simple brouillon) et renvoyer un vrai document PDF en
     * piece jointe.
     */
    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void lExportPdfRenvoieUnDocumentPdfEnPieceJointe() throws Exception {
        Long id = projetService.creerProjet(dtoBase()).getId();

        mockMvc.perform(get("/projets/{id}/export-pdf", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("fiche-projet-" + id + ".pdf")));
    }

    /**
     * L'organisateur peut publier un commentaire depuis la fiche, et le fil
     * l'affiche ensuite sur la page.
     */
    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void publierUnCommentaireLaffichEEnsuiteSurLaFiche() throws Exception {
        Long id = projetService.creerProjet(dtoBase()).getId();

        mockMvc.perform(post("/projets/{id}/commentaires", id).with(csrf()).param("texte", "Merci de vérifier le budget"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/projets/" + id));

        mockMvc.perform(get("/projets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Merci de vérifier le budget")));
    }

    private ProjetFormDTO dtoBase() {
        ProjetFormDTO dto = new ProjetFormDTO();
        dto.setNomProjet("Voyage a Barcelone");
        dto.setDateDepart(LocalDateTime.now().plusMonths(1));
        dto.setDateRetour(LocalDateTime.now().plusMonths(1).plusDays(3));
        dto.setLieuDepart("College");
        dto.setLieuRetour("College");
        dto.setTransport("Car");
        dto.setOrganisateurNom("M. Prof");
        dto.setOrganisateurEmail("prof@college-sthelier.fr");
        dto.setTelephoneOrganisateur("0102030405");
        dto.setClassesConcernees("6A");
        dto.setEffectif(28);
        dto.setCoutGlobal(new BigDecimal("1500"));
        dto.setCoutParEleve(new BigDecimal("50"));
        dto.setMontantSubvention(BigDecimal.ZERO);
        return dto;
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void laFicheDunProjetValideAfficheLaVueDeConsultation() throws Exception {
        Long id = creerEtValiderCompletement();

        mockMvc.perform(get("/projets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("consultation"));
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void dupliquerUnProjetValideCreeUnBrouillonIndependant() throws Exception {
        Long idOriginal = creerEtValiderCompletement();

        MvcResult duplication = mockMvc.perform(post("/projets/{id}/dupliquer", idOriginal).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectedUrl = duplication.getResponse().getRedirectedUrl();
        assertThat(redirectedUrl).isNotNull().isNotEqualTo("/projets/" + idOriginal);

        // La copie est bien une fiche editable independante, en brouillon.
        mockMvc.perform(get(redirectedUrl))
                .andExpect(status().isOk())
                .andExpect(view().name("formulaire"));

        Projet copie = projetService.trouverParId(Long.valueOf(redirectedUrl.substring(redirectedUrl.lastIndexOf('/') + 1)));
        assertThat(copie.getNomProjet()).isEqualTo("Voyage a Marseille (copie)");
        assertThat(copie.getStatut().name()).isEqualTo("BROUILLON");
        assertThat(copie.getDateValidationProf()).isNull();
        assertThat(copie.getDateValidationCompta()).isNull();
        assertThat(copie.getOrganisateurEmail()).isEqualTo("prof@college-sthelier.fr");
    }

    /**
     * Fait progresser un projet jusqu'a VALIDE en manipulant directement le
     * service (bascule de role via SecurityContextHolder, comme dans
     * ProjetServiceTest), pour tester ensuite le routage HTTP sur ce statut.
     */
    private Long creerEtValiderCompletement() throws Exception {
        ProjetFormDTO dto = new ProjetFormDTO();
        dto.setNomProjet("Voyage a Marseille");
        dto.setDateDepart(LocalDateTime.now().plusMonths(1));
        dto.setDateRetour(LocalDateTime.now().plusMonths(1).plusDays(3));
        dto.setLieuDepart("College");
        dto.setLieuRetour("College");
        dto.setTransport("Car");
        dto.setOrganisateurNom("M. Prof");
        dto.setOrganisateurEmail("prof@college-sthelier.fr");
        dto.setTelephoneOrganisateur("0102030405");
        dto.setClassesConcernees("6A");
        dto.setEffectif(28);
        dto.setCoutGlobal(new BigDecimal("1500"));
        dto.setCoutParEleve(new BigDecimal("50"));
        dto.setMontantSubvention(BigDecimal.ZERO);

        Long id = projetService.creerProjet(dto).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);

        connecterEnTantQue("viesco@college-sthelier.fr", "ROLE_VIESCO");
        projetService.validerVieScolaire(id);

        connecterEnTantQue("direction@college-sthelier.fr", "ROLE_DIRECTION");
        projetService.validerDirection(id);

        // Remet le contexte de securite du prof pour la suite du test HTTP,
        // @WithMockUser ayant ete "ecrase" par les bascules de role ci-dessus.
        connecterEnTantQue("prof@college-sthelier.fr", "ROLE_PROF");

        return id;
    }
}
