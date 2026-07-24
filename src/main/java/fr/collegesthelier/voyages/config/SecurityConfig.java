package fr.collegesthelier.voyages.config;

import fr.collegesthelier.voyages.security.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

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
                        .requestMatchers("/css/**", "/js/**", "/webjars/**", "/error", "/login/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
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
                );

        return http.build();
    }
}
