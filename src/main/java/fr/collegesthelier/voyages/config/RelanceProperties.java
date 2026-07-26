package fr.collegesthelier.voyages.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametres des relances automatiques sur les dossiers bloques (voir
 * RelanceService) : seuil avant la premiere relance, puis periode de
 * repetition tant que le dossier reste bloque.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "voyages.relances")
public class RelanceProperties {

    private int seuilJours = 7;
    private int periodeJours = 7;
}
