package fr.collegesthelier.voyages.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * En test (comme par defaut sans configuration), l'integration Drive est
 * desactivee : verifie que GoogleDriveService reste alors un no-op
 * silencieux plutot que de tenter un appel reseau vers l'API Google.
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
