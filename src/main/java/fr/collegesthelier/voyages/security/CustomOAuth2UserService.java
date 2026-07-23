package fr.collegesthelier.voyages.security;

import fr.collegesthelier.voyages.config.RolesProperties;
import fr.collegesthelier.voyages.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Service d'authentification OAuth2 (Google) :
 * 1. Rejette toute connexion dont l'email n'appartient pas au domaine de
 *    l'etablissement (variable d'environnement ALLOWED_EMAIL_DOMAIN).
 * 2. Attribue les roles RBAC : ROLE_PROF par defaut, puis ROLE_COMPTA /
 *    ROLE_VIESCO / ROLE_DIRECTION / ROLE_ADMIN selon les listes d'emails
 *    configurees dans application.properties. Un utilisateur peut cumuler
 *    plusieurs roles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String ATTRIBUT_EMAIL = "email";

    private final SecurityProperties securityProperties;
    private final RolesProperties rolesProperties;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute(ATTRIBUT_EMAIL);
        if (email == null || !estDomaineAutorise(email)) {
            log.warn("Connexion refusee pour l'email '{}' : domaine non autorise", email);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("domaine_non_autorise"),
                    "Seuls les comptes @" + securityProperties.getAllowedEmailDomain() + " sont autorises.");
        }

        Set<GrantedAuthority> authorities = construireAuthorities(email);
        log.info("Connexion de '{}' avec les roles {}", email, authorities);

        return new DefaultOAuth2User(authorities, oAuth2User.getAttributes(), ATTRIBUT_EMAIL);
    }

    private boolean estDomaineAutorise(String email) {
        String domaine = securityProperties.getAllowedEmailDomain();
        if (domaine == null || domaine.isBlank()) {
            return false;
        }
        return email.toLowerCase(Locale.ROOT).endsWith("@" + domaine.toLowerCase(Locale.ROOT));
    }

    private Set<GrantedAuthority> construireAuthorities(String email) {
        String emailNormalise = email.toLowerCase(Locale.ROOT);
        Set<GrantedAuthority> authorities = new HashSet<>();

        // Tout utilisateur autorise a se connecter recoit le role de base.
        authorities.add(new SimpleGrantedAuthority("ROLE_PROF"));

        if (figureDansListe(rolesProperties.getCompta(), emailNormalise)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_COMPTA"));
        }
        if (figureDansListe(rolesProperties.getViesco(), emailNormalise)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_VIESCO"));
        }
        if (figureDansListe(rolesProperties.getDirection(), emailNormalise)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_DIRECTION"));
        }
        if (figureDansListe(rolesProperties.getAdmin(), emailNormalise)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        return authorities;
    }

    private boolean figureDansListe(List<String> emailsAutorises, String emailNormalise) {
        return emailsAutorises.stream()
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .anyMatch(e -> e.equals(emailNormalise));
    }
}
