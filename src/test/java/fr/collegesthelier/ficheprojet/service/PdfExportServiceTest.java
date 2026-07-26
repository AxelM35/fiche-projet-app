package fr.collegesthelier.ficheprojet.service;

import fr.collegesthelier.ficheprojet.dto.ProjetFormDTO;
import fr.collegesthelier.ficheprojet.model.Projet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie que l'export PDF (bouton "Exporter en PDF" sur la fiche projet)
 * produit bien un document PDF valide, aussi bien pour un dossier en
 * brouillon (sans historique de validation) que pour un dossier ayant
 * franchi des etapes du workflow (avec historique).
 */
@SpringBootTest
@ActiveProfiles("test")
class PdfExportServiceTest {

    @Autowired
    private ProjetService projetService;

    @Autowired
    private PdfExportService pdfExportService;

    @Autowired
    private CommentaireService commentaireService;

    private ProjetFormDTO dtoValide() {
        ProjetFormDTO dto = new ProjetFormDTO();
        dto.setNomProjet("Voyage a Rome");
        dto.setDescription("Voyage culturel");
        dto.setDateDepart(LocalDateTime.now().plusMonths(2));
        dto.setDateRetour(LocalDateTime.now().plusMonths(2).plusDays(4));
        dto.setLieuDepart("College");
        dto.setLieuRetour("College");
        dto.setTransport("Avion");
        dto.setOrganisateurNom("Mme Martin");
        dto.setOrganisateurEmail("martin@college-sthelier.fr");
        dto.setTelephoneOrganisateur("0102030405");
        dto.setClassesConcernees("4A");
        dto.setEffectif(25);
        dto.setCoutGlobal(new BigDecimal("2500"));
        dto.setCoutParEleve(new BigDecimal("100"));
        dto.setMontantSubvention(BigDecimal.ZERO);
        return dto;
    }

    private void connecterEnTantQue(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        Authentication authentication = new TestingAuthenticationToken(email, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void nettoyerContexteSecurite() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void genereUnPdfValidePourUnBrouillon() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Projet brouillon = projetService.creerProjet(dtoValide());

        byte[] pdf = pdfExportService.genererFichePdf(brouillon.getId());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void genereUnPdfIncluantLHistoriqueDeValidation() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Projet projet = projetService.creerProjet(dtoValide());
        projetService.soumettre(projet.getId());

        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.validerCompta(projet.getId());

        byte[] pdf = pdfExportService.genererFichePdf(projet.getId());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void genereUnPdfIncluantLeFilDeCommentaires() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Projet projet = projetService.creerProjet(dtoValide());
        commentaireService.ajouter(projet.getId(), "Merci de vérifier le budget avant validation.");

        byte[] pdf = pdfExportService.genererFichePdf(projet.getId());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
