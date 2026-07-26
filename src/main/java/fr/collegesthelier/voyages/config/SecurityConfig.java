package fr.collegesthelier.voyages.config;

import fr.collegesthelier.voyages.security.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/webjars/**", "/error", "/login", "/login/**",
                                "/oauth2/**").permitAll()
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
                        // Bootstrap/bootstrap-icons/Google Fonts sont charges depuis un CDN et
                        // les templates contiennent quelques scripts/styles inline : la CSP
                        // reste donc en 'unsafe-inline' pour l'instant plutot que de casser
                        // l'UI. Un durcissement ulterieur consisterait a externaliser ces
                        // scripts/styles et passer a une CSP par nonce.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                                        + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; "
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
