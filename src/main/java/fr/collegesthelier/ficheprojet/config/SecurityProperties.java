package fr.collegesthelier.ficheprojet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Domaine email autorise a se connecter (transmis via la variable
 * d'environnement ALLOWED_EMAIL_DOMAIN).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ficheprojet.security")
public class SecurityProperties {

    private String allowedEmailDomain;
}
