package fr.ficheprojet.service;

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
 * Chiffres clés pour la page admin "Santé" : nombre de dossiers, version
 * déployée, date de la dernière sauvegarde. La dernière sauvegarde est lue
 * directement sur le dossier partagé avec le service db-backup (docker-
 * compose.yml monte ./backups en lecture seule dans le conteneur app) :
 * aucun accès au conteneur db-backup lui-même, juste une lecture de fichier.
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
     * Renvoie la version du jar en cours d'exécution (Implementation-Version
     * du MANIFEST, renseignée par spring-boot-maven-plugin au packaging).
     * Vide en dehors d'un jar exécuté (ex. mvn spring-boot:run en dev).
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
