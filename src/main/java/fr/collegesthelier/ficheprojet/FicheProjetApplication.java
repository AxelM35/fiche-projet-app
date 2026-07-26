package fr.collegesthelier.ficheprojet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entree de l'application Fiche Projet numerique (gestion et
 * validation des projets de voyages scolaires du College Saint-Helier).
 * <p>
 * @EnableScheduling : necessaire pour RelanceService.relancerDossiersBloques()
 * (@Scheduled, relances automatiques quotidiennes des dossiers bloques).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FicheProjetApplication {

    public static void main(String[] args) {
        SpringApplication.run(FicheProjetApplication.class, args);
    }
}
