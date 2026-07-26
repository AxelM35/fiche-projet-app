package fr.collegesthelier.voyages.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Page de connexion dediee, necessaire car Spring Security saute la page de
 * choix generee automatiquement (DefaultLoginPageGeneratingFilter) des lors
 * qu'un seul fournisseur OAuth2 (Google) est enregistre : sans page de
 * connexion explicite (SecurityConfig.loginPage("/login")), GET /login ne
 * correspond a aucune route et renvoie un 404 (reproduit par exemple apres
 * une deconnexion, qui redirige vers /login?deconnexion).
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
