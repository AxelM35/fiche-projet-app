package fr.ficheprojet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de l'intégration Google Drive (création automatique du
 * dossier de pièces jointes d'un projet, via un compte de service membre
 * d'un Drive partagé de l'établissement). Désactivée par défaut : sans
 * configuration, GoogleDriveService reste un nô-op silencieux et le lien
 * Drive se saisit alors uniquement à la main (voir ProjetService.modifierLienDrive).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ficheprojet.drive")
public class GoogleDriveProperties {

    private boolean enabled = false;

    /** Identifiant du Drive partagé (Shared Drive) dans lequel créer les dossiers. */
    private String sharedDriveId = "";

    /** Clé JSON du compte de service, encodée en base64 (évite un fichier à monter en volume). */
    private String credentialsJsonBase64 = "";
}
