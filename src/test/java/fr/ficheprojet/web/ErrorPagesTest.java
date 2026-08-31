package fr.ficheprojet.web;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pages d'erreur personnalisées (audit UX, docs/CAHIER_DES_CHARGES.md S4bis) :
 * un utilisateur non technique qui tombe sur un lien périmé ou une action non
 * autorisée doit voir un message clair en français plutôt que la page
 * Whitelabel de Spring Boot. Templates résolues par convention (Spring Boot
 * DefaultErrorViewResolver) via templates/error/{403,404,500}.html, sans
 * contrôleur dédié.
 *
 * MockMvc ne simule pas le forward container vers /error déclenché par un
 * vrai code d'erreur (contrairement à un serveur réellement démarré) : les
 * tests de rendu appellent donc directement /error avec l'attribut de
 * requête que Spring Boot y lit normalement (jakarta.servlet.error.status_code),
 * comme le ferait le conteneur. La vérification que /admin/rôles renvoie
 * bien 403 (sans reperformer le rendu) reste couverte par AdminControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorPagesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "prof@exemple.fr", authorities = "ROLE_PROF")
    void unAccesRefuseRenvoie403() throws Exception {
        mockMvc.perform(get("/admin/roles"))
                .andExpect(status().isForbidden());
    }

    // Accept: text/html requis pour chaque appel : BasicErrorController choisit
    // sinon sa réponse JSON générique (comportement de négociation de contenu
    // normal, un vrai navigateur envoie toujours cet en-tete).

    @Test
    void laPageDerreur403EstPersonnalisee() throws Exception {
        mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML).requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Accès non autorisé")))
                .andExpect(content().string(containsString("Retour au tableau de bord")));
    }

    /**
     * Formulaire de signalement (voir fragments/signalement-erreur.html) :
     * présent sur la page, avec le code de statut et le chemin d'origine
     * déjà renseignés dans des champs cachés (pas à l'utilisateur de les
     * retrouver lui-même).
     */
    @Test
    void laPageDerreur403ProposeUnFormulaireDeSignalementAvecLeContexte() throws Exception {
        mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/admin/roles"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("action=\"/error/signalement\"")))
                .andExpect(content().string(containsString("id=\"messageSignalement\"")))
                .andExpect(content().string(containsString("name=\"statutHttp\" value=\"403\"")))
                .andExpect(content().string(containsString("name=\"cheminOrigine\" value=\"/admin/roles\"")));
    }

    @Test
    void laPageDerreur404EstPersonnalisee() throws Exception {
        mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML).requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Page introuvable")));
    }

    @Test
    void laPageDerreur500EstPersonnalisee() throws Exception {
        mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML).requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Une erreur est survenue")));
    }
}
