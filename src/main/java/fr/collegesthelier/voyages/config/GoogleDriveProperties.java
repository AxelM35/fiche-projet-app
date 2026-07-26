package fr.collegesthelier.voyages.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de l'integration Google Drive (creation automatique du
 * dossier de pieces jointes d'un projet, via un compte de service membre
 * d'un Drive partage de l'etablissement). Desactivee par defaut : sans
 * configuration, GoogleDriveService reste un no-op silencieux et le lien
 * Drive se saisit alors uniquement a la main (voir ProjetService.modifierLienDrive).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "voyages.drive")
public class GoogleDriveProperties {

    private boolean enabled = false;

    /** Identifiant du Drive partage (Shared Drive) dans lequel creer les dossiers. */
    private String sharedDriveId = "";

    /** Cle JSON du compte de service, encodee en base64 (evite un fichier a monter en volume). */
    private String credentialsJsonBase64 = "";
}
