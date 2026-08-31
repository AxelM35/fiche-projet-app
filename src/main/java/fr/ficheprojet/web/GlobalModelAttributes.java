package fr.ficheprojet.web;

import fr.ficheprojet.config.EtablissementProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Attributs de modèle communs à toutes les vues (navbar notamment), pour ne
 * pas dupliquer leur calcul dans chaque @Controller. Authentication.getName()
 * plutôt que @AuthenticationPrincipal OAuth2User : le principal OAuth2 est
 * construit (CustomOAuth2UserService) avec l'attribut "email" comme
 * nameAttributeKey, donc getName() renvoie déjà directement l'email, comme
 * ailleurs dans l'appli (ProjetService.emailUtilisateurConnecte) - et
 * fonctionne aussi avec les principaux de test (@WithMockUser,
 * TestingAuthenticationToken), qui ne sont jamais des OAuth2User.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final EtablissementProperties etablissement;

    @ModelAttribute("utilisateurConnecte")
    public String utilisateurConnecte(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

    /**
     * Nom de l'établissement, disponible dans toutes les vues (navbar, page de
     * connexion). Chaîne vide si non configuré : les gabarits masquent alors
     * la mention.
     */
    @ModelAttribute("nomEtablissement")
    public String nomEtablissement() {
        return etablissement.getNom();
    }
}
