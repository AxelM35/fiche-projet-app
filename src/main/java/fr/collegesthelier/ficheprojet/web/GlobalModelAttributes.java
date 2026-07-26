package fr.collegesthelier.ficheprojet.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Attributs de modele communs a toutes les vues (navbar notamment), pour ne
 * pas dupliquer leur calcul dans chaque @Controller.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("utilisateurConnecte")
    public String utilisateurConnecte(@AuthenticationPrincipal OAuth2User principal) {
        return principal != null ? principal.getAttribute("email") : null;
    }
}
