package fr.collegesthelier.voyages.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Listes d'emails (une par role metier) lues depuis application.properties,
 * utilisees a la fois pour l'attribution des roles RBAC a la connexion
 * (CustomOAuth2UserService) et pour le routage des notifications
 * (NotificationService).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "voyages.roles")
public class RolesProperties {

    private List<String> admin = List.of();
    private List<String> compta = List.of();
    private List<String> viesco = List.of();
    private List<String> direction = List.of();
}
