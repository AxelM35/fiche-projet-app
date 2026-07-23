package fr.collegesthelier.voyages;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Point d'entree de l'application de gestion des voyages scolaires.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class VoyagesApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoyagesApplication.class, args);
    }
}
