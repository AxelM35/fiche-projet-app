package fr.collegesthelier.ficheprojet.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Attributs de modele communs a toutes les vues (navbar notamment), pour ne
 * pas dupliquer leur calcul dans chaque @Controller. Authentication.getName()
 * plutot que @AuthenticationPrincipal OAuth2User : le principal OAuth2 est
 * construit (CustomOAuth2UserService) avec l'attribut "email" comme
 * nameAttributeKey, donc getName() renvoie deja directement l'email, comme
 * ailleurs dans l'appli (ProjetService.emailUtilisateurConnecte) - et
 * fonctionne aussi avec les principaux de test (@WithMockUser,
 * TestingAuthenticationToken), qui ne sont jamais des OAuth2User.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("utilisateurConnecte")
    public String utilisateurConnecte(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
