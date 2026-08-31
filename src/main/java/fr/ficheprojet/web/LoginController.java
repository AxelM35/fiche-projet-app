package fr.ficheprojet.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Page de connexion dédiée, nécessaire car Spring Security saute la page de
 * choix générée automatiquement (DefaultLoginPageGeneratingFilter) des lors
 * qu'un seul fournisseur OAuth2 (Google) est enregistré : sans page de
 * connexion explicite (SecurityConfig.loginPage("/login")), GET /login ne
 * correspond à aucune route et renvoie un 404 (reproduit par exemple après
 * une déconnexion, qui redirige vers /login?déconnexion).
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
