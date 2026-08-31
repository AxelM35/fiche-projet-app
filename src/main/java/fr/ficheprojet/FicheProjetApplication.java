package fr.ficheprojet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée de l'application Fiche Projet numérique (gestion et
 * validation des projets de voyages scolaires).
 * <p>
 * @EnableScheduling : nécessaire pour RelanceService.relancerDossiersBloques()
 * (@Scheduled, relances automatiques quotidiennes des dossiers bloqués).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FicheProjetApplication {

    public static void main(String[] args) {
        SpringApplication.run(FicheProjetApplication.class, args);
    }
}
