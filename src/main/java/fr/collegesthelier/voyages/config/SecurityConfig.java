package fr.collegesthelier.voyages.config;

import fr.collegesthelier.voyages.security.CustomOAuth2UserService;
import fr.collegesthelier.voyages.security.LoginRateLimitingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.session.DisableEncodeUrlFilter;

/**
 * Configuration de la securite web : authentification exclusivement via
 * Google OAuth2, RBAC applique au niveau des methodes de service
 * (@EnableMethodSecurity + @PreAuthorize) en complement du sec:authorize
 * cote vue (qui ne fait qu'ameliorer l'UX, jamais la seule protection).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Tout au debut du filter chain : rejette les requetes en exces avant
                // tout traitement de securite (session, CSRF...) sur les routes
                // d'authentification (voir LoginRateLimitingFilter).
                .addFilterBefore(new LoginRateLimitingFilter(), DisableEncodeUrlFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/webjars/**", "/error", "/login", "/login/**",
                                "/oauth2/**").permitAll()
                        // Public (pas d'authentification) : sonde de monitoring du conteneur
                        // (Docker healthcheck, futur reverse proxy...). N'expose que le statut
                        // global UP/DOWN (voir management.endpoint.health.show-details=never).
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        // Page de connexion explicite : avec un seul fournisseur (Google),
                        // Spring Security saute sinon la page generee automatiquement et
                        // redirige directement vers /oauth2/authorization/google, ce qui
                        // laisse /login sans aucune route (404 constate sur la redirection
                        // de logoutSuccessUrl). LoginController fournit la vue "login".
                        .loginPage("/login")
                        // Google est enregistre avec le scope "openid" : le flux est OIDC,
                        // donc oidcUserService(...) est le point d'extension a utiliser
                        // (userService(...) ne serait jamais invoque dans ce cas).
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(customOAuth2UserService))
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?erreur")
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?deconnexion")
                        .permitAll()
                )
                .headers(headers -> headers
                        // Tous les scripts/styles sont desormais externes (fichiers sous
                        // /js, /css, ou CDN Bootstrap/Google Fonts) : plus aucun script,
                        // style ou gestionnaire d'evenement inline dans les templates
                        // (voir static/js/*.js), donc plus besoin de 'unsafe-inline'.
                        // Seul le template d'email (email/notification.html) garde des
                        // styles inline, hors de portee de cette CSP (ce n'est pas une
                        // reponse HTTP de l'application).
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' https://cdn.jsdelivr.net; "
                                        + "style-src 'self' https://cdn.jsdelivr.net https://fonts.googleapis.com; "
                                        + "font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net; "
                                        + "img-src 'self' data:; "
                                        + "connect-src 'self'; "
                                        + "base-uri 'self'; "
                                        + "form-action 'self'; "
                                        + "frame-ancestors 'none'"
                        ))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                );

        return http.build();
    }
}
