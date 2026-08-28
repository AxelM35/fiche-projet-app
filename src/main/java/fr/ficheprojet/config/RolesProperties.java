package fr.ficheprojet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Listés d'emails (une par rôle métier) lues depuis application.properties,
 * utilisées à la fois pour l'attribution des rôles RBAC à la connexion
 * (CustomOAuth2UserService) et pour le routage des notifications
 * (NotificationService).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ficheprojet.roles")
public class RolesProperties {

    private List<String> admin = List.of();
    private List<String> compta = List.of();
    private List<String> viesco = List.of();
    private List<String> direction = List.of();
    private List<String> lectureSeule = List.of();
}
