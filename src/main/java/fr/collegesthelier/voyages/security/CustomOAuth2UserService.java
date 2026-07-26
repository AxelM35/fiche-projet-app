package fr.collegesthelier.voyages.security;

import fr.collegesthelier.voyages.config.RolesProperties;
import fr.collegesthelier.voyages.config.SecurityProperties;
import fr.collegesthelier.voyages.model.RoleMetier;
import fr.collegesthelier.voyages.repository.RoleAttributionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Google est enregistre avec le scope "openid" (voir application.properties)
 * : la connexion emprunte donc le flux OpenID Connect, pas un simple OAuth2.
 * C'est pourquoi ce service surcharge OidcUserService (et non
 * DefaultOAuth2UserService, qui ne serait jamais invoque pour un flux OIDC)
 * et est branche via userInfoEndpoint().oidcUserService(...) dans
 * SecurityConfig. Il applique :
 * 1. le filtre de domaine (rejet si l'email n'appartient pas au domaine
 *    autorise, variable d'environnement ALLOWED_EMAIL_DOMAIN) ;
 * 2. l'attribution des roles RBAC : ROLE_PROF par defaut, puis
 *    ROLE_COMPTA / ROLE_VIESCO / ROLE_DIRECTION / ROLE_ADMIN selon
 *    l'union de deux sources : les listes d'emails configurees (.env,
 *    RolesProperties) et les attributions gerees depuis le dashboard admin
 *    (RoleAttribution, en base). La base ne fait jamais que s'ajouter aux
 *    listes d'environnement, jamais les remplacer : une erreur de
 *    manipulation dans le dashboard admin ne peut donc jamais retirer
 *    l'acces attribue via .env (voir ROLES_ADMIN, filet de securite contre
 *    un verrouillage total). Un utilisateur peut cumuler plusieurs roles.
 *    Exception : un email figurant dans la liste "lecture seule" recoit
 *    ROLE_LECTURE_SEULE a la place de ROLE_PROF (jamais les deux), pour
 *    un observateur (ex. secretariat) qui doit tout consulter sans jamais
 *    pouvoir creer ni soumettre de dossier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends OidcUserService {

    private static final String ATTRIBUT_EMAIL = "email";

    private final SecurityProperties securityProperties;
    private final RolesProperties rolesProperties;
    private final RoleAttributionRepository roleAttributionRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getAttribute(ATTRIBUT_EMAIL);
        if (email == null || !estDomaineAutorise(email)) {
            log.warn("Connexion refusee pour l'email '{}' : domaine non autorise", email);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("domaine_non_autorise"),
                    "Seuls les comptes @" + securityProperties.getAllowedEmailDomain() + " sont autorises.");
        }

        Set<GrantedAuthority> authorities = construireAuthorities(email);
        log.info("Connexion de '{}' avec les roles {}", email, authorities);

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), ATTRIBUT_EMAIL);
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

        // Tout utilisateur autorise a se connecter recoit le role de base,
        // sauf s'il est inscrit comme simple observateur (lecture seule).
        if (possedeRole(rolesProperties.getLectureSeule(), RoleMetier.LECTURE_SEULE, emailNormalise)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_LECTURE_SEULE"));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_PROF"));
        }

        if (possedeRole(rolesProperties.getCompta(), RoleMetier.COMPTA, emailNormalise)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_COMPTA"));
        }
        if (possedeRole(rolesProperties.getViesco(), RoleMetier.VIESCO, emailNormalise)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_VIESCO"));
        }
        if (possedeRole(rolesProperties.getDirection(), RoleMetier.DIRECTION, emailNormalise)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_DIRECTION"));
        }
        if (possedeRole(rolesProperties.getAdmin(), RoleMetier.ADMIN, emailNormalise)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        return authorities;
    }

    private boolean possedeRole(List<String> emailsConfigures, RoleMetier role, String emailNormalise) {
        return figureDansListe(emailsConfigures, emailNormalise)
                || roleAttributionRepository.existsByEmailAndRole(emailNormalise, role);
    }

    private boolean figureDansListe(List<String> emailsAutorises, String emailNormalise) {
        return emailsAutorises.stream()
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .anyMatch(e -> e.equals(emailNormalise));
    }
}
