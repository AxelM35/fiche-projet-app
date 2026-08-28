package fr.ficheprojet.web;

import fr.ficheprojet.dto.ProjetFormDTO;
import fr.ficheprojet.model.Projet;
import fr.ficheprojet.service.ProjetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import java.util.Arrays;
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
 * Tests d'intégration vérifiant que les vues Thymeleaf (dashboard,
 * formulaire) se rendent correctement et que le workflow de création
 * fonctionne de bout en bout, sur une base H2 en mémoire.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjetService projetService;

    private void connecterEnTantQue(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        Authentication authentication = new TestingAuthenticationToken(email, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void nettoyerContexteSecurite() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Avec un seul fournisseur OAuth2 (Google), Spring Security saute la
     * page de connexion générée automatiquement et redirige directement
     * vers /oauth2/authorization/google : sans page /login explicite
     * (SecurityConfig.loginPage + LoginController), cette URL n'existe
     * plus du tout (404), notamment après une déconnexion qui y redirige.
     */
    @Test
    void laPageDeConnexionSAfficheSansAuthentification() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void leTableauDeBordSAffiche() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    /**
     * Régression : la navbar utilisait navbar-expand-lg sans bouton
     * hamburger ni <div class="collapse">, donc sans aucun moyen de la
     * replier sous 992px (voir fragments/navbar.html) - vérifie que le
     * couple toggler/collapse est bien présent.
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void laNavbarEstRepliableSurMobile() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("navbar-toggler")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("collapse navbar-collapse")));
    }

    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void leDashboardChargeLeScriptDesSpinnersDeValidation() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/boutons-validation.js")));
    }

    /**
     * Aide contextuelle (onboarding) : le bouton "Comment ça marche ?" et la
     * modale associée ne s'affichent qu'a un Prof (public visé, cf. cahier
     * des charges) - un rôle de validation sans PROF ne doit rien en voir.
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void leDashboardExposeLaideContextuellePourUnProf() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"modalOnboarding\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Comment ça marche ?")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/onboarding.js")));
    }

    @Test
    @WithMockUser(username = "secretariat@exemple.fr", authorities = "ROLE_LECTURE_SEULE")
    void leDashboardNexposePasLaideContextuellePourUnRoleSansProf() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(resultat.getResponse().getContentAsString()).doesNotContain("id=\"modalOnboarding\"");
    }

    /**
     * Filtres avancés du dashboard (classe, organisateur, période de
     * départ) : vérifie que les champs de filtre sont bien présents et que
     * chaque carte porte les attributs data-* nécessaires au filtrage côté
     * client (voir dashboard.js).
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
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
     * Le filtre "Mes dossiers uniquement" est coché par défaut pour un Prof
     * sans rôle de validation (le seul public pour qui le Kanban complet de
     * l'établissement n'est jamais le point de départ utile), mais pas pour
     * un rôle de validation qui doit voir tous les dossiers dès l'arrivée.
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void leFiltreMesDossiersEstCocheParDefautPourUnProfSeul() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"filtreMesDossiers\" checked=\"checked\"")));
    }

    @Test
    @WithMockUser(username = "compta@exemple.fr", authorities = {"ROLE_PROF", "ROLE_COMPTA"})
    void leFiltreMesDossiersNestPasCocheParDefautPourUnRoleDeValidation() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(resultat.getResponse().getContentAsString())
                .doesNotContain("id=\"filtreMesDossiers\" checked=\"checked\"");
    }

    /**
     * Régression : les formulaires "Archiver" et "Supprimer définitivement"
     * de la modale de gestion admin n'avaient pas de th:action (l'URL réelle
     * est posée par dashboard.js au clic) - sans th:action, l'extension
     * Thymeleaf Spring Security n'injecte pas le jeton CSRF automatiquement,
     * ce qui faisait échouer la soumission en 403 dans un vrai navigateur
     * (MockMvc + .with(csrf()) ne l'aurait pas détecté, d'où cette assertion
     * directe sur le HTML rendu).
     */
    @Test
    @WithMockUser(username = "admin@exemple.fr", authorities = {"ROLE_PROF", "ROLE_ADMIN"})
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
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void leFormulaireDeCreationSAffiche() throws Exception {
        mockMvc.perform(get("/projets/nouveau"))
                .andExpect(status().isOk())
                .andExpect(view().name("formulaire"));
    }

    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void laCreationDunProjetValideRedirigeVersSaFiche() throws Exception {
        mockMvc.perform(post("/projets/nouveau")
                        .with(csrf())
                        .param("nomProjet", "Voyage a Londres")
                        .param("description", "Sejour linguistique")
                        .param("dateDepart", "2026-10-01T08:00")
                        .param("dateRetour", "2026-10-05T18:00")
                        .param("lieuDepart", "Collège Exemple")
                        .param("lieuRetour", "Collège Exemple")
                        .param("transport", "Car")
                        .param("organisateurNom", "M. Dupont")
                        .param("organisateurEmail", "dupont@exemple.fr")
                        .param("telephoneOrganisateur", "0102030405")
                        .param("classesConcernees", "5A, 5B")
                        .param("effectif", "30")
                        .param("coutGlobal", "3000")
                        .param("coutParEleve", "100")
                        .param("montantSubvention", "0"))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * "Je ne connais pas encore le budget" (audit UX S4bis) : la création
     * réussit sans coutGlobal/coutParEleve quand la case est cochée, mais
     * ces deux champs redeviennent obligatoires sans elle (voir
     * ProjetController.validerCoherenceBudget).
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void laCreationSansBudgetReussitQuandBudgetInconnuEstCoche() throws Exception {
        mockMvc.perform(post("/projets/nouveau")
                        .with(csrf())
                        .param("nomProjet", "Voyage a Londres")
                        .param("dateDepart", "2026-10-01T08:00")
                        .param("dateRetour", "2026-10-05T18:00")
                        .param("lieuDepart", "Collège Exemple")
                        .param("lieuRetour", "Collège Exemple")
                        .param("transport", "Car")
                        .param("organisateurNom", "M. Dupont")
                        .param("organisateurEmail", "dupont@exemple.fr")
                        .param("telephoneOrganisateur", "0102030405")
                        .param("classesConcernees", "5A, 5B")
                        .param("effectif", "30")
                        .param("budgetInconnu", "true"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void laCreationSansBudgetEchoueSansBudgetInconnuCoche() throws Exception {
        MvcResult resultat = mockMvc.perform(post("/projets/nouveau")
                        .with(csrf())
                        .param("nomProjet", "Voyage a Londres")
                        .param("dateDepart", "2026-10-01T08:00")
                        .param("dateRetour", "2026-10-05T18:00")
                        .param("lieuDepart", "Collège Exemple")
                        .param("lieuRetour", "Collège Exemple")
                        .param("transport", "Car")
                        .param("organisateurNom", "M. Dupont")
                        .param("organisateurEmail", "dupont@exemple.fr")
                        .param("telephoneOrganisateur", "0102030405")
                        .param("classesConcernees", "5A, 5B")
                        .param("effectif", "30"))
                .andExpect(status().isOk())
                .andExpect(view().name("formulaire"))
                .andReturn();

        assertThat(resultat.getResponse().getContentAsString()).contains("Le coût global est obligatoire.");
    }

    /**
     * Une fois le dossier engagé dans le circuit (plus de bouton
     * "Enregistrer"), la carte "Compléter le budget" permet à la
     * Comptabilité de renseigner le budget manquant (audit UX S4bis).
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void laComptabilitePeutCompleterLeBudgetDunDossierEnAttente() throws Exception {
        ProjetFormDTO dto = dtoBase();
        dto.setCoutGlobal(null);
        dto.setCoutParEleve(null);
        Long id = projetService.creerProjet(dto).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@exemple.fr", "ROLE_PROF", "ROLE_COMPTA");
        MvcResult ficheAvantCompletion = mockMvc.perform(get("/projets/{id}", id))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(ficheAvantCompletion.getResponse().getContentAsString()).contains("Compléter le budget");

        mockMvc.perform(post("/projets/{id}/completer-budget", id).with(csrf())
                        .param("coutGlobal", "1800")
                        .param("coutParEleve", "60"))
                .andExpect(status().is3xxRedirection());

        assertThat(projetService.trouverParId(id).getCoutGlobal()).isEqualByComparingTo("1800");
    }

    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void laCreationDunProjetInvalideReaffichleFormulaireAvecErreurs() throws Exception {
        mockMvc.perform(post("/projets/nouveau").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("formulaire"));
    }

    /**
     * Clarté du formulaire (audit UX, docs/CAHIER_DES_CHARGES.md S4bis) :
     * légende des champs obligatoires toujours visible, et résumé d'erreurs
     * en haut de page uniquement affiche après un échec de soumission (pas
     * sur un formulaire vierge).
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void leFormulaireVierneAfficheJamaisLeResumeDerreurs() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/projets/nouveau"))
                .andExpect(status().isOk())
                .andReturn();
        String html = resultat.getResponse().getContentAsString();

        assertThat(html).contains("Champs obligatoires");
        assertThat(html).doesNotContain("id=\"resumeErreursFormulaire\"");
    }

    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void laCreationDunProjetInvalideAfficheLeResumeDerreursEnHautDePage() throws Exception {
        MvcResult resultat = mockMvc.perform(post("/projets/nouveau").with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(resultat.getResponse().getContentAsString()).contains("id=\"resumeErreursFormulaire\"");
    }

    /**
     * Reproduit le scénario signalé en test manuel : ouvrir la fiche d'un
     * projet DÉJÀ enregistré (et non un formulaire vierge) déclenchait un
     * org.hibernate.LazyInitializationException sur la collection
     * accompagnateurs, chargée en lazy, car trouverParId(id) et versDTO(...)
     * s'exécutaient dans deux transactions distinctes.
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void laFicheDunProjetExistantAvecAccompagnateursSAffiche() throws Exception {
        MvcResult creation = mockMvc.perform(post("/projets/nouveau")
                        .with(csrf())
                        .param("nomProjet", "Voyage a Berlin")
                        .param("dateDepart", "2026-11-01T08:00")
                        .param("dateRetour", "2026-11-05T18:00")
                        .param("lieuDepart", "Collège Exemple")
                        .param("lieuRetour", "Collège Exemple")
                        .param("transport", "Avion")
                        .param("organisateurNom", "Mme Petit")
                        .param("organisateurEmail", "prof@exemple.fr")
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
    @WithMockUser(username = "secretariat@exemple.fr", authorities = "ROLE_LECTURE_SEULE")
    void unUtilisateurEnLectureSeuleVoitLaConsultationMemeSurUnBrouillon() throws Exception {
        connecterEnTantQue("prof@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoBase()).getId();

        // La bascule de rôle ci-dessus (nécessaire pour créer le projet en
        // tant que prof) a "écrasé" le contexte posé par @WithMockUser : on
        // le restaure avant la requête HTTP, comme dans creerEtValiderCompletement().
        connecterEnTantQue("secretariat@exemple.fr", "ROLE_LECTURE_SEULE");
        mockMvc.perform(get("/projets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("consultation"));
    }

    /**
     * Régression : avant correctif, un Prof qui n'était ni l'organisateur du
     * dossier ni un rôle de validation recevait quand même le formulaire
     * éditable (champs non verrouillés) d'un dossier d'un collègue, alors que
     * l'enregistrement aurait de toute façon été refusé côté service
     * (verifierDroitModification). Seule la consultation doit s'afficher.
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void unProfNonOrganisateurVoitLaConsultationSurLeDossierDunCollegue() throws Exception {
        connecterEnTantQue("collegue@exemple.fr", "ROLE_PROF");
        ProjetFormDTO dto = dtoBase();
        dto.setOrganisateurEmail("collegue@exemple.fr");
        Long id = projetService.creerProjet(dto).getId();

        connecterEnTantQue("prof@exemple.fr", "ROLE_PROF");
        mockMvc.perform(get("/projets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("consultation"));
    }

    /**
     * Le bouton "Soumettre pour validation" posté désormais (via formaction)
     * vers préparer-soumission, qui enregistré le formulaire puis redirige
     * vers le récapitulatif : vérifie ce redirect, que la page s'affiche, et
     * que "Confirmer et soumettre" fait bien avancer le statut.
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
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
                        .param("organisateurEmail", "prof@exemple.fr")
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
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void preparerLaSoumissionAvecDonneesInvalidesReaffichleFormulaireAvecErreurs() throws Exception {
        Long id = projetService.creerProjet(dtoBase()).getId();

        mockMvc.perform(post("/projets/{id}/preparer-soumission", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("formulaire"));
    }

    /**
     * Hiérarchie visuelle Enregistrer / Soumettre pour validation (audit UX,
     * docs/CAHIER_DES_CHARGES.md S4bis) : sur un dossier A_CORRIGER, les deux
     * boutons avaient un poids visuel comparable, sans rien pour indiquer
     * lequel des deux referme réellement la correction. "Enregistrer" passe
     * en style secondaire discret dans ce statut précis, "Soumettre pour
     * validation" reste seul en btn-success.
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void leBoutonEnregistrerEstDiscretSurUnDossierACorriger() throws Exception {
        Long id = projetService.creerProjet(dtoBase()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@exemple.fr", "ROLE_COMPTA");
        projetService.refuser(id, "Pas assez de budget");

        connecterEnTantQue("prof@exemple.fr", "ROLE_PROF");
        MvcResult resultat = mockMvc.perform(get("/projets/{id}", id))
                .andExpect(status().isOk())
                .andReturn();

        String html = resultat.getResponse().getContentAsString();
        assertThat(html).contains("form=\"formProjet\" class=\"btn btn-outline-secondary\"");
        assertThat(html).contains("btn-success js-bouton-validation");
    }

    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void leBoutonEnregistrerResteEnAvantSurUnBrouillon() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/projets/nouveau"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(resultat.getResponse().getContentAsString())
                .contains("form=\"formProjet\" class=\"btn btn-primary\"");
    }

    /**
     * Un dossier déjà engagé dans le circuit de validation n'a plus rien à
     * relire avant soumission : le récapitulatif redirige simplement vers la
     * fiche plutôt que d'afficher une page vide de sens pour ce statut.
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void leRecapitulatifDunDossierDejaValideRedirigeVersLaFiche() throws Exception {
        Long id = creerEtValiderCompletement();

        mockMvc.perform(get("/projets/{id}/recapitulatif", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/projets/" + id));
    }

    /**
     * Le bouton "Exporter en PDF" doit fonctionner quel que soit le statut du
     * dossier (ici un simple brouillon) et renvoyer un vrai document PDF en
     * pièce jointe.
     */
    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
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
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
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
        dto.setOrganisateurEmail("prof@exemple.fr");
        dto.setTelephoneOrganisateur("0102030405");
        dto.setClassesConcernees("6A");
        dto.setEffectif(28);
        dto.setCoutGlobal(new BigDecimal("1500"));
        dto.setCoutParEleve(new BigDecimal("50"));
        dto.setMontantSubvention(BigDecimal.ZERO);
        return dto;
    }

    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void laFicheDunProjetValideAfficheLaVueDeConsultation() throws Exception {
        Long id = creerEtValiderCompletement();

        mockMvc.perform(get("/projets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("consultation"));
    }

    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void dupliquerUnProjetValideCreeUnBrouillonIndependant() throws Exception {
        Long idOriginal = creerEtValiderCompletement();

        MvcResult duplication = mockMvc.perform(post("/projets/{id}/dupliquer", idOriginal).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectedUrl = duplication.getResponse().getRedirectedUrl();
        assertThat(redirectedUrl).isNotNull().isNotEqualTo("/projets/" + idOriginal);

        // La copie est bien une fiche éditable indépendante, en brouillon.
        mockMvc.perform(get(redirectedUrl))
                .andExpect(status().isOk())
                .andExpect(view().name("formulaire"));

        Projet copie = projetService.trouverParId(Long.valueOf(redirectedUrl.substring(redirectedUrl.lastIndexOf('/') + 1)));
        assertThat(copie.getNomProjet()).isEqualTo("Voyage a Marseille (copie)");
        assertThat(copie.getStatut().name()).isEqualTo("BROUILLON");
        assertThat(copie.getDateValidationProf()).isNull();
        assertThat(copie.getDateValidationCompta()).isNull();
        assertThat(copie.getOrganisateurEmail()).isEqualTo("prof@exemple.fr");
    }

    /**
     * Fait progresser un projet jusqu'a VALIDE en manipulant directement le
     * service (bascule de rôle via SecurityContextHolder, comme dans
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
        dto.setOrganisateurEmail("prof@exemple.fr");
        dto.setTelephoneOrganisateur("0102030405");
        dto.setClassesConcernees("6A");
        dto.setEffectif(28);
        dto.setCoutGlobal(new BigDecimal("1500"));
        dto.setCoutParEleve(new BigDecimal("50"));
        dto.setMontantSubvention(BigDecimal.ZERO);

        Long id = projetService.creerProjet(dto).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@exemple.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);

        connecterEnTantQue("viesco@exemple.fr", "ROLE_VIESCO");
        projetService.validerVieScolaire(id);

        connecterEnTantQue("direction@exemple.fr", "ROLE_DIRECTION");
        projetService.validerDirection(id);

        // Remet le contexte de sécurité du prof pour la suite du test HTTP,
        // @WithMockUser ayant été "écrasé" par les bascules de rôle ci-dessus.
        connecterEnTantQue("prof@exemple.fr", "ROLE_PROF");

        return id;
    }
}
