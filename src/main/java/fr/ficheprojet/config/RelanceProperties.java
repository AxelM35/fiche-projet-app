package fr.ficheprojet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres des relances automatiques sur les dossiers bloqués (voir
 * RelanceService) : seuil avant la première relance, puis période de
 * répétition tant que le dossier reste bloqué.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ficheprojet.relances")
public class RelanceProperties {

    private int seuilJours = 7;
    private int periodeJours = 7;
}
