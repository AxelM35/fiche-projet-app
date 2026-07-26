package fr.collegesthelier.voyages;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entree de l'application de gestion des voyages scolaires.
 * <p>
 * @EnableScheduling : necessaire pour RelanceService.relancerDossiersBloques()
 * (@Scheduled, relances automatiques quotidiennes des dossiers bloques).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class VoyagesApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoyagesApplication.class, args);
    }
}
