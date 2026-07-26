package fr.collegesthelier.ficheprojet.service;

import fr.collegesthelier.ficheprojet.dto.ProjetConsultationDTO;
import fr.collegesthelier.ficheprojet.model.Commentaire;
import fr.collegesthelier.ficheprojet.model.JournalEntree;
import fr.collegesthelier.ficheprojet.repository.JournalEntreeRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Genere le PDF recapitulatif d'un dossier (bouton "Exporter en PDF" sur la
 * fiche projet, formulaire.html et consultation.html) : rend le template
 * Thymeleaf pdf/fiche-projet-pdf.html (recapitulatif + historique de
 * validation + fil de commentaires) en HTML, puis le convertit en PDF via
 * openhtmltopdf. Le HTML rendu par Thymeleaf n'est pas garanti strictement
 * XHTML (attributs booleens, balises non fermees...) : on passe par Jsoup
 * pour le reparser en document XML bien forme avant de le donner a
 * openhtmltopdf, qui l'exige.
 */
@Service
@RequiredArgsConstructor
public class PdfExportService {

    /**
     * Sous-ensemble du journal d'audit correspondant au fil de validation du
     * workflow (par etape) : on exclut volontairement les actions
     * administratives (archivage, reaffectation, lien Drive...) qui ne font
     * pas partie de l'historique de validation attendu dans l'export.
     */
    private static final Set<String> ACTIONS_HISTORIQUE_VALIDATION = Set.of(
            "Création", "Soumission", "Resoumission",
            "Validation Comptabilité", "Validation Vie Scolaire",
            "Validation Direction (finale)", "Refus");

    private final ProjetService projetService;
    private final JournalEntreeRepository journalEntreeRepository;
    private final CommentaireService commentaireService;
    private final TemplateEngine templateEngine;

    @Transactional(readOnly = true)
    public byte[] genererFichePdf(Long id) {
        ProjetConsultationDTO projet = projetService.chargerConsultation(id);
        List<JournalEntree> historiqueValidation = journalEntreeRepository
                .findByProjetIdOrderByDateEvenementAsc(id).stream()
                .filter(entree -> ACTIONS_HISTORIQUE_VALIDATION.contains(entree.getAction()))
                .toList();
        List<Commentaire> commentaires = commentaireService.lister(id);

        Context contexte = new Context(Locale.FRENCH);
        contexte.setVariable("projet", projet);
        contexte.setVariable("historiqueValidation", historiqueValidation);
        contexte.setVariable("commentaires", commentaires);
        String html = templateEngine.process("pdf/fiche-projet-pdf", contexte);

        return convertirEnPdf(html);
    }

    private byte[] convertirEnPdf(String html) {
        Document documentJsoup = Jsoup.parse(html);
        documentJsoup.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        org.w3c.dom.Document documentW3c = new W3CDom().fromJsoup(documentJsoup);

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withW3cDocument(documentW3c, null);
            builder.toStream(sortie);
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException("Erreur lors de la génération du PDF", e);
        }
        return sortie.toByteArray();
    }
}
