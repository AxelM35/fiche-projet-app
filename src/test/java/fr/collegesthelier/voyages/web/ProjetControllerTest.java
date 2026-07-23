package fr.collegesthelier.voyages.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void leTableauDeBordSAffiche() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
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
                        .param("lieuDepart", "College Saint-Helier")
                        .param("lieuRetour", "College Saint-Helier")
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
}
