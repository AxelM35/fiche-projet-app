package fr.collegesthelier.ficheprojet.web;

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
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void unProfNAccedePasALarchivageGroupeParAnneeScolaire() throws Exception {
        mockMvc.perform(post("/admin/archives/archiver-annee").with(csrf()).param("anneeScolaire", "2024-2025"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "amorvan@college-sthelier.fr", authorities = {"ROLE_PROF", "ROLE_ADMIN"})
    void unAdminDeclencheLarchivageGroupeParAnneeScolaireEtRedirigeVersLesArchives() throws Exception {
        mockMvc.perform(post("/admin/archives/archiver-annee").with(csrf()).param("anneeScolaire", "2024-2025"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/admin/archives"));
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

    @Test
    @WithMockUser(username = "amorvan@college-sthelier.fr", authorities = {"ROLE_PROF", "ROLE_ADMIN"})
    void unAdminAccedeALaPageNotificationsEtPeutEnvoyerUnTestSansPlanter() throws Exception {
        mockMvc.perform(get("/admin/notifications"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-notifications"));

        // Aucun vrai serveur SMTP en test (voir application-test.properties) :
        // l'envoi echoue forcement, mais le controleur doit rattraper la
        // MailException et rediriger avec un message d'erreur, pas planter.
        mockMvc.perform(post("/admin/notifications/test").with(csrf()).param("destinataire", "amorvan@college-sthelier.fr"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void unProfNAccedePasAuxNotifications() throws Exception {
        mockMvc.perform(get("/admin/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "amorvan@college-sthelier.fr", authorities = {"ROLE_PROF", "ROLE_ADMIN"})
    void unAdminAccedeALaPageSante() throws Exception {
        mockMvc.perform(get("/admin/sante"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-sante"));
    }

    @Test
    @WithMockUser(username = "prof@college-sthelier.fr", authorities = "ROLE_PROF")
    void unProfNAccedePasALaPageSante() throws Exception {
        mockMvc.perform(get("/admin/sante"))
                .andExpect(status().isForbidden());
    }
}
