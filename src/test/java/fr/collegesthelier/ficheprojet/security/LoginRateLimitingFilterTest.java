package fr.collegesthelier.ficheprojet.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le filtre est un singleton partage entre toutes les requetes de la
 * chaine de securite (voir LoginRateLimitingFilter, instancie une seule
 * fois dans SecurityConfig), avec un compteur par adresse IP : chaque test
 * utilise donc sa propre IP simulee (RequestPostProcessor) pour ne pas
 * interferer avec d'autres tests qui appellent /login depuis l'IP par
 * defaut de MockMvc (127.0.0.1), notamment dans le meme contexte Spring
 * reutilise entre classes de test. @DirtiesContext par securite
 * supplementaire (ne mise pas uniquement sur l'isolation par IP).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class LoginRateLimitingFilterTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor depuisIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void bloqueApresTropDeRequetesSurLesRoutesDauthentification() throws Exception {
        RequestPostProcessor ip = depuisIp("203.0.113.10");
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/login").with(ip)).andExpect(status().isOk());
        }

        mockMvc.perform(get("/login").with(ip)).andExpect(status().is(429));
    }

    @Test
    void neLimitePasLesAutresRoutes() throws Exception {
        RequestPostProcessor ip = depuisIp("203.0.113.11");
        for (int i = 0; i < 25; i++) {
            mockMvc.perform(get("/actuator/health").with(ip)).andExpect(status().isOk());
        }
    }
}
