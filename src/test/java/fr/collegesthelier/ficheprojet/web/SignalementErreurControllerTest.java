package fr.collegesthelier.ficheprojet.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.FlashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signalement volontaire depuis une page d'erreur (audit UX,
 * docs/CAHIER_DES_CHARGES.md S4bis) : transmis par email a l'administrateur
 * (NotificationService.signalerErreur). Accessible sans authentification
 * (voir SecurityConfig), puisqu'une erreur peut survenir avant meme la
 * connexion (lien perime, session expiree...) - aucun @WithMockUser ici,
 * intentionnellement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignalementErreurControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unSignalementValideRedirigeVersLeTableauDeBordAvecUnMessageDeSucces() throws Exception {
        MvcResult resultat = mockMvc.perform(post("/error/signalement").with(csrf())
                        .param("message", "Je venais de cliquer sur le lien reçu par email.")
                        .param("statutHttp", "404")
                        .param("cheminOrigine", "/projets/999"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        FlashMap flashMap = resultat.getFlashMap();
        assertThat(flashMap.get("messageSucces")).isEqualTo("Merci, votre message a été transmis à l'administrateur.");
    }

    @Test
    void unSignalementSansMessageRedirigeAvecUneErreurSansEtreTransmis() throws Exception {
        MvcResult resultat = mockMvc.perform(post("/error/signalement").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(resultat.getFlashMap().get("messageErreur")).isNotNull();
    }
}
