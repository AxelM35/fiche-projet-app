package fr.collegesthelier.ficheprojet.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import fr.collegesthelier.ficheprojet.config.GoogleDriveProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Creation automatique du dossier Google Drive d'un projet, via un compte
 * de service membre du Drive partage de l'etablissement (pas de delegation
 * domaine-wide necessaire : le Drive partage est simplement partage avec
 * l'adresse du compte de service, comme avec n'importe quel collaborateur).
 * <p>
 * Best-effort volontaire : aucune methode ne leve jamais d'exception vers
 * l'appelant (ProjetService). Si l'integration est desactivee, mal
 * configuree, ou que l'appel Drive echoue, on retourne simplement
 * Optional.empty() et l'enregistrement du projet continue normalement -
 * l'organisateur peut toujours coller le lien a la main ensuite.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleDriveService {

    private static final String APPLICATION_NAME = "Fiche Projet numérique - Collège Saint-Helier";
    private static final List<String> SCOPES = List.of(DriveScopes.DRIVE_FILE);

    private final GoogleDriveProperties driveProperties;

    private volatile Drive driveClient;

    public Optional<String> creerDossierProjet(Long projetId, String nomProjet) {
        if (!driveProperties.isEnabled()) {
            return Optional.empty();
        }

        try {
            Drive drive = client();
            if (drive == null) {
                return Optional.empty();
            }

            File metadata = new File();
            metadata.setName("#" + projetId + " - " + nomProjet);
            metadata.setMimeType("application/vnd.google-apps.folder");
            metadata.setParents(List.of(driveProperties.getSharedDriveId()));

            File cree = drive.files().create(metadata)
                    .setSupportsAllDrives(true)
                    .setFields("id, webViewLink")
                    .execute();

            String lien = cree.getWebViewLink() != null
                    ? cree.getWebViewLink()
                    : "https://drive.google.com/drive/folders/" + cree.getId();
            return Optional.of(lien);
        } catch (Exception e) {
            // Volontairement large : de nombreuses causes possibles (config
            // absente, quota, reseau, credentials invalides) et aucune ne
            // doit faire echouer la creation/duplication du projet.
            log.warn("Echec de la creation automatique du dossier Drive pour le projet {}", projetId, e);
            return Optional.empty();
        }
    }

    private synchronized Drive client() throws Exception {
        if (driveClient != null) {
            return driveClient;
        }
        if (driveProperties.getCredentialsJsonBase64() == null || driveProperties.getCredentialsJsonBase64().isBlank()) {
            log.warn("Integration Google Drive activee (ficheprojet.drive.enabled=true) mais aucune credential "
                    + "configuree (GOOGLE_DRIVE_CREDENTIALS_JSON_BASE64).");
            return null;
        }

        byte[] cleJson = Base64.getDecoder().decode(driveProperties.getCredentialsJsonBase64());
        GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(cleJson))
                .createScoped(SCOPES);

        driveClient = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
        return driveClient;
    }
}
