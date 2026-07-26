package fr.collegesthelier.ficheprojet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametres utilises par NotificationService pour construire les emails
 * de notification du workflow de validation.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ficheprojet.notifications")
public class NotificationProperties {

    private String emailExpediteur;
    private String urlApplication;
}
