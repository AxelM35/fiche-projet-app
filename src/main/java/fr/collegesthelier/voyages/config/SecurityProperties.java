package fr.collegesthelier.voyages.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Domaine email autorise a se connecter (transmis via la variable
 * d'environnement ALLOWED_EMAIL_DOMAIN).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "voyages.security")
public class SecurityProperties {

    private String allowedEmailDomain;
}
