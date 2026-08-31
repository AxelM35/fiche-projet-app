package fr.ficheprojet.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * En test (comme par défaut sans configuration), l'intégration Drive est
 * désactivée : vérifie que GoogleDriveService reste alors un nô-op
 * silencieux plutôt que de tenter un appel réseau vers l'API Google.
 */
@SpringBootTest
@ActiveProfiles("test")
class GoogleDriveServiceTest {

    @Autowired
    private GoogleDriveService googleDriveService;

    @Test
    void neCreeAucunDossierQuandLintegrationEstDesactivee() {
        assertThat(googleDriveService.creerDossierProjet(1L, "Voyage a Londres")).isEmpty();
    }
}
