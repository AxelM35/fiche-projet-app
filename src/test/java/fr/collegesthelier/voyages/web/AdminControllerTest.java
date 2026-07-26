package fr.collegesthelier.voyages.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Le dashboard admin (gestion des roles) doit rester inaccessible a tout
 * utilisateur autre que ROLE_ADMIN, y compris par simple navigation directe
 * vers l'URL (contrairement au reste de l'application, ouvert en lecture a
 * tout utilisateur authentifie).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void unProfNAccedePasAuDashboardAdmin() throws Exception {
        mockMvc.perform(get("/admin/roles"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "amorvan@college-sthelier.fr", authorities = {"ROLE_PROF", "ROLE_ADMIN"})
    void unAdminAccedeAuDashboardAdmin() throws Exception {
        mockMvc.perform(get("/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-roles"));
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void unProfNAccedePasALaPageDesArchives() throws Exception {
        mockMvc.perform(get("/admin/archives"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "amorvan@college-sthelier.fr", authorities = {"ROLE_PROF", "ROLE_ADMIN"})
    void unAdminAccedeALaPageDesArchives() throws Exception {
        mockMvc.perform(get("/admin/archives"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-archives"));
    }

    @Test
    @WithMockUser(username = "amorvan@college-sthelier.fr", authorities = {"ROLE_PROF", "ROLE_ADMIN"})
    void unAdminAccedeALaPageDesDossiersBloques() throws Exception {
        mockMvc.perform(get("/admin/dossiers-bloques"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-dossiers-bloques"));
    }

    @Test
    @WithMockUser(username = "amorvan@college-sthelier.fr", authorities = {"ROLE_PROF", "ROLE_ADMIN"})
    void unAdminAccedeAuJournalDaudit() throws Exception {
        mockMvc.perform(get("/admin/journal"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-journal"));
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void unProfNAccedePasAuJournalDaudit() throws Exception {
        mockMvc.perform(get("/admin/journal"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "amorvan@college-sthelier.fr", authorities = {"ROLE_PROF", "ROLE_ADMIN"})
    void unAdminAccedeALaRechercheEtALexportCsv() throws Exception {
        mockMvc.perform(get("/admin/recherche").param("nom", "voyage"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-recherche"));

        mockMvc.perform(get("/admin/recherche/export.csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void unProfNAccedePasALaRecherche() throws Exception {
        mockMvc.perform(get("/admin/recherche"))
                .andExpect(status().isForbidden());
    }
}
