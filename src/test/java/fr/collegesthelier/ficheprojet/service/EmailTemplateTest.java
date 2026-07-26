package fr.collegesthelier.ficheprojet.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie le rendu du template email/notification.html utilise par
 * NotificationService pour les emails HTML : aucun test d'envoi reel (pas
 * de serveur SMTP en test), mais on s'assure que le HTML produit contient
 * bien les elements attendus selon les variables fournies.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmailTemplateTest {

    @Autowired
    private TemplateEngine templateEngine;

    private String rendre(String titre, String message, String motifRefus, String lienDossier) {
        Context contexte = new Context(Locale.FRENCH);
        contexte.setVariable("titre", titre);
        contexte.setVariable("message", message);
        contexte.setVariable("motifRefus", motifRefus);
        contexte.setVariable("lienDossier", lienDossier);
        return templateEngine.process("email/notification", contexte);
    }

    @Test
    void afficheLeMessageEtLeLienSansMotifDeRefus() {
        String html = rendre("Nouveau dossier a valider : Voyage a Londres",
                "Le dossier attend votre validation comptable.", null,
                "http://localhost:8080/projets/42");

        assertThat(html)
                .contains("Nouveau dossier a valider : Voyage a Londres")
                .contains("Le dossier attend votre validation comptable.")
                .contains("http://localhost:8080/projets/42")
                .contains("Consulter le dossier")
                .doesNotContain("Motif :");
    }

    @Test
    void afficheLeMotifDeRefusQuandFourni() {
        String html = rendre("Dossier a corriger : Voyage a Londres",
                "Votre dossier a ete refuse.", "Budget incomplet",
                "http://localhost:8080/projets/42");

        assertThat(html)
                .contains("Motif :")
                .contains("Budget incomplet");
    }

    @Test
    void neRendPasDeBoutonSansLienDossier() {
        String html = rendre("Email de test - Voyages Scolaires",
                "Ceci est un email de test.", null, null);

        assertThat(html).doesNotContain("Consulter le dossier");
    }
}
