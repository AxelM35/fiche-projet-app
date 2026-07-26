package fr.collegesthelier.voyages.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Chiffres cles pour la page admin "Sante" : nombre de dossiers, version
 * deployee, date de la derniere sauvegarde. La derniere sauvegarde est lue
 * directement sur le dossier partage avec le service db-backup (docker-
 * compose.yml monte ./backups en lecture seule dans le conteneur app) :
 * aucun acces au conteneur db-backup lui-meme, juste une lecture de fichier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanteService {

    private static final Path DOSSIER_DERNIERE_SAUVEGARDE = Path.of("/backups/last");

    private final ProjetService projetService;

    public long nombreDeProjets() {
        return projetService.compterProjets();
    }

    /**
     * Renvoie la version du jar en cours d'execution (Implementation-Version
     * du MANIFEST, renseignee par spring-boot-maven-plugin au packaging).
     * Vide en dehors d'un jar execute (ex. mvn spring-boot:run en dev).
     */
    public String versionApplication() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "developpement (execution hors .jar)";
    }

    public Optional<LocalDateTime> derniereSauvegarde() {
        if (!Files.isDirectory(DOSSIER_DERNIERE_SAUVEGARDE)) {
            return Optional.empty();
        }
        try (Stream<Path> fichiers = Files.list(DOSSIER_DERNIERE_SAUVEGARDE)) {
            return fichiers
                    .filter(Files::isRegularFile)
                    .map(this::dateDerniereModification)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder());
        } catch (IOException e) {
            log.warn("Impossible de lire le dossier de sauvegardes {}", DOSSIER_DERNIERE_SAUVEGARDE, e);
            return Optional.empty();
        }
    }

    private LocalDateTime dateDerniereModification(Path fichier) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(fichier).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            return null;
        }
    }
}
