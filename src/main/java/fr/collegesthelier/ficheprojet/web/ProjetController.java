package fr.collegesthelier.ficheprojet.web;

import fr.collegesthelier.ficheprojet.dto.LienDriveFormDTO;
import fr.collegesthelier.ficheprojet.dto.ProjetFormDTO;
import fr.collegesthelier.ficheprojet.dto.ReaffectationFormDTO;
import fr.collegesthelier.ficheprojet.dto.RefusFormDTO;
import fr.collegesthelier.ficheprojet.model.Projet;
import fr.collegesthelier.ficheprojet.model.StatutProjet;
import fr.collegesthelier.ficheprojet.service.PdfExportService;
import fr.collegesthelier.ficheprojet.service.ProjetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Couche web : ne manipule que des DTO valides (jamais l'entite Projet
 * directement), conformement au pattern DTO impose pour eviter le Mass
 * Assignment. Le controle d'autorisation metier (qui a le droit de valider
 * quoi) est applique dans ProjetService via @PreAuthorize ; sec:authorize
 * dans les vues n'est qu'un confort d'affichage.
 */
@Controller
@RequiredArgsConstructor
public class ProjetController {

    private final ProjetService projetService;
    private final PdfExportService pdfExportService;

    @GetMapping("/")
    public String racine() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String tableauDeBord(Model model) {
        var tableauDeBord = projetService.projetsPourTableauDeBord();
        model.addAttribute("tableauDeBord", tableauDeBord);
        model.addAttribute("stats", projetService.calculerStatistiques(tableauDeBord));
        model.addAttribute("refus", new RefusFormDTO());
        return "dashboard";
    }

    @GetMapping("/projets/nouveau")
    public String formulaireCreation(Model model) {
        model.addAttribute("projet", new ProjetFormDTO());
        model.addAttribute("statutCourant", StatutProjet.BROUILLON);
        model.addAttribute("motifRefus", null);
        model.addAttribute("etapesWorkflow", construireEtapesWorkflow(StatutProjet.BROUILLON, 1));
        return "formulaire";
    }

    @GetMapping("/projets/{id}")
    public String formulaireEdition(@PathVariable Long id, Model model, Authentication authentication) {
        Projet projet = projetService.trouverParId(id);
        model.addAttribute("etapesWorkflow", construireEtapesWorkflow(projet.getStatut(), calculerEtapeCourante(projet)));
        model.addAttribute("peutModifierLienDrive", projetService.peutGererLienDrive(projet));
        model.addAttribute("lienDriveForm", lienDriveFormPreRempli(projet));

        // Un dossier definitivement valide n'est plus modifiable (sauf par un
        // Admin, correction exceptionnelle apres coup), et un observateur en
        // lecture seule ne doit jamais voir un formulaire editable (meme sans
        // bouton actif) : dans ces cas, on affiche la consultation plutot que
        // le formulaire.
        boolean estValide = projet.getStatut() == StatutProjet.VALIDE;
        if ((estValide && !possedeRole(authentication, "ROLE_ADMIN")) || estEnLectureSeule(authentication)) {
            model.addAttribute("projet", projetService.chargerConsultation(id));
            return "consultation";
        }

        model.addAttribute("projet", projetService.chargerFormulaire(id));
        model.addAttribute("statutCourant", projet.getStatut());
        model.addAttribute("motifRefus", projet.getMotifRefus());
        model.addAttribute("reaffectation", new ReaffectationFormDTO());
        return "formulaire";
    }

    private boolean estEnLectureSeule(Authentication authentication) {
        return possedeRole(authentication, "ROLE_LECTURE_SEULE");
    }

    private boolean possedeRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    private LienDriveFormDTO lienDriveFormPreRempli(Projet projet) {
        LienDriveFormDTO dto = new LienDriveFormDTO();
        dto.setLienDrive(projet.getLienDrive());
        return dto;
    }

    @PostMapping("/projets/{id}/reaffecter-organisateur")
    public String reaffecterOrganisateur(@PathVariable Long id,
                                          @Valid @ModelAttribute("reaffectation") ReaffectationFormDTO dto,
                                          BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("messageErreur", "Email ou nom du nouvel organisateur invalide.");
            return "redirect:/projets/" + id;
        }

        projetService.reaffecterOrganisateur(id, dto.getOrganisateurEmail(), dto.getOrganisateurNom());
        redirectAttributes.addFlashAttribute("messageSucces", "Le dossier a été réaffecté.");
        return "redirect:/projets/" + id;
    }

    @PostMapping("/projets/{id}/lien-drive")
    public String modifierLienDrive(@PathVariable Long id, @Valid @ModelAttribute("lienDriveForm") LienDriveFormDTO dto,
                                     BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("messageErreur",
                    "Le lien doit être une URL Google Drive valide (https://drive.google.com/...).");
            return "redirect:/projets/" + id;
        }

        projetService.modifierLienDrive(id, dto.getLienDrive());
        redirectAttributes.addFlashAttribute("messageSucces", "Le lien du dossier Drive a été mis à jour.");
        return "redirect:/projets/" + id;
    }

    @PostMapping("/projets/nouveau")
    public String creer(@Valid @ModelAttribute("projet") ProjetFormDTO dto, BindingResult bindingResult,
                         Model model, RedirectAttributes redirectAttributes) {
        validerCoherenceDates(dto, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("statutCourant", StatutProjet.BROUILLON);
            model.addAttribute("motifRefus", null);
            model.addAttribute("etapesWorkflow", construireEtapesWorkflow(StatutProjet.BROUILLON, 1));
            return "formulaire";
        }

        Projet projet = projetService.creerProjet(dto);
        redirectAttributes.addFlashAttribute("messageSucces", "Le projet a été créé en brouillon.");
        return "redirect:/projets/" + projet.getId();
    }

    @PostMapping("/projets/{id}")
    public String modifier(@PathVariable Long id, @Valid @ModelAttribute("projet") ProjetFormDTO dto,
                            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        validerCoherenceDates(dto, bindingResult);
        if (bindingResult.hasErrors()) {
            remplirModelPourErreurFormulaire(model, projetService.trouverParId(id));
            return "formulaire";
        }

        projetService.modifierProjet(id, dto);
        redirectAttributes.addFlashAttribute("messageSucces", "Les modifications ont été enregistrées.");
        return "redirect:/projets/" + id;
    }

    /**
     * Enregistre les modifications en cours (identique a modifier()) puis
     * redirige vers le recapitulatif plutot que vers la fiche : la relecture
     * avant soumission porte ainsi exactement sur ce qui vient d'etre
     * sauvegarde, meme si l'organisateur a tape des changements sans passer
     * par "Enregistrer" au prealable (voir le bouton "Soumettre pour
     * validation" dans formulaire.html, qui poste ici via formaction).
     */
    @PostMapping("/projets/{id}/preparer-soumission")
    public String preparerSoumission(@PathVariable Long id, @Valid @ModelAttribute("projet") ProjetFormDTO dto,
                                      BindingResult bindingResult, Model model) {
        validerCoherenceDates(dto, bindingResult);
        if (bindingResult.hasErrors()) {
            remplirModelPourErreurFormulaire(model, projetService.trouverParId(id));
            return "formulaire";
        }

        projetService.modifierProjet(id, dto);
        return "redirect:/projets/" + id + "/recapitulatif";
    }

    @GetMapping("/projets/{id}/recapitulatif")
    public String recapitulatifSoumission(@PathVariable Long id, Model model) {
        Projet projet = projetService.trouverParId(id);
        if (projet.getStatut() != StatutProjet.BROUILLON && projet.getStatut() != StatutProjet.A_CORRIGER) {
            // Deja engage dans le circuit (ou valide) : plus rien a relire avant
            // soumission, on revient simplement sur la fiche.
            return "redirect:/projets/" + id;
        }

        model.addAttribute("projet", projetService.chargerConsultation(id));
        return "recapitulatif";
    }

    /**
     * Export PDF de la fiche (recapitulatif + historique de validation).
     * Accessible a tout utilisateur pouvant deja consulter le dossier
     * (aucune restriction de role supplementaire, memes regles d'acces que
     * GET /projets/{id}).
     */
    @GetMapping("/projets/{id}/export-pdf")
    public ResponseEntity<byte[]> exporterPdf(@PathVariable Long id) {
        byte[] pdf = pdfExportService.genererFichePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fiche-projet-" + id + ".pdf\"")
                .body(pdf);
    }

    private void remplirModelPourErreurFormulaire(Model model, Projet projetExistant) {
        model.addAttribute("statutCourant", projetExistant.getStatut());
        model.addAttribute("motifRefus", projetExistant.getMotifRefus());
        model.addAttribute("etapesWorkflow",
                construireEtapesWorkflow(projetExistant.getStatut(), calculerEtapeCourante(projetExistant)));
        model.addAttribute("peutModifierLienDrive", projetService.peutGererLienDrive(projetExistant));
        model.addAttribute("lienDriveForm", lienDriveFormPreRempli(projetExistant));
    }

    @PostMapping("/projets/{id}/dupliquer")
    public String dupliquer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Projet copie = projetService.dupliquer(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Le projet a été dupliqué en brouillon. Pensez à adapter les classes et les dates.");
        return "redirect:/projets/" + copie.getId();
    }

    @PostMapping("/projets/{id}/soumettre")
    public String soumettre(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        projetService.soumettre(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Le dossier a été soumis pour validation.");
        return "redirect:/dashboard";
    }

    @PostMapping("/projets/{id}/valider-compta")
    public String validerCompta(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        projetService.validerCompta(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Validation comptable enregistrée.");
        return "redirect:/dashboard";
    }

    @PostMapping("/projets/{id}/valider-vie-scolaire")
    public String validerVieScolaire(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        projetService.validerVieScolaire(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Validation vie scolaire enregistrée.");
        return "redirect:/dashboard";
    }

    @PostMapping("/projets/{id}/valider-direction")
    public String validerDirection(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        projetService.validerDirection(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Le dossier a été validé définitivement.");
        return "redirect:/dashboard";
    }

    @PostMapping("/projets/{id}/archiver")
    public String archiver(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        projetService.archiver(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Le dossier a été archivé.");
        return "redirect:/dashboard";
    }

    @PostMapping("/projets/{id}/desarchiver")
    public String desarchiver(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        projetService.desarchiver(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Le dossier a été désarchivé.");
        return "redirect:/admin/archives";
    }

    @PostMapping("/projets/{id}/supprimer")
    public String supprimer(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        projetService.supprimerDefinitivement(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Le dossier a été supprimé définitivement.");
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null && referer.contains("/admin/archives") ? "/admin/archives" : "/dashboard");
    }

    @PostMapping("/projets/{id}/refuser")
    public String refuser(@PathVariable Long id, @Valid @ModelAttribute("refus") RefusFormDTO refusDto,
                           BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("erreurRefus", "Le motif de refus est obligatoire.");
            return "redirect:/dashboard";
        }

        projetService.refuser(id, refusDto.getMotifRefus());
        redirectAttributes.addFlashAttribute("messageSucces", "Le dossier a été renvoyé au professeur pour correction.");
        return "redirect:/dashboard";
    }

    private void validerCoherenceDates(ProjetFormDTO dto, BindingResult bindingResult) {
        if (dto.getDateDepart() != null && dto.getDateRetour() != null
                && dto.getDateRetour().isBefore(dto.getDateDepart())) {
            bindingResult.rejectValue("dateRetour", "date.incoherente",
                    "La date de retour doit être postérieure ou égale à la date de départ.");
        }
    }

    private static final String[] LIBELLES_ETAPES_WORKFLOW = {"Soumission", "Comptabilité", "Vie Scolaire", "Direction"};
    private static final String[] ICONES_ETAPES_WORKFLOW = {"bi-send", "bi-cash-coin", "bi-people", "bi-mortarboard"};

    /**
     * Etape (1 a 4) mise en evidence dans le stepper. Pour un dossier
     * A_CORRIGER, il s'agit de l'etape qui a refuse le dossier : c'est
     * exactement celle par laquelle la resoumission repassera (voir
     * ProjetService.determinerEtapeDeReprise, meme logique basee sur les
     * dates de validation deja acquises).
     */
    private int calculerEtapeCourante(Projet projet) {
        return switch (projet.getStatut()) {
            case BROUILLON -> 1;
            case EN_ATTENTE_COMPTA -> 2;
            case EN_ATTENTE_VIE_SCOLAIRE -> 3;
            case EN_ATTENTE_DIRECTION, VALIDE -> 4;
            case A_CORRIGER -> projet.getDateValidationVieScolaire() != null ? 4
                    : projet.getDateValidationCompta() != null ? 3 : 2;
        };
    }

    private List<EtapeWorkflowVue> construireEtapesWorkflow(StatutProjet statut, int etapeCourante) {
        boolean toutesValidees = statut == StatutProjet.VALIDE;
        List<EtapeWorkflowVue> etapes = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String etat;
            if (toutesValidees || i < etapeCourante) {
                etat = "fait";
            } else if (i == etapeCourante) {
                etat = statut == StatutProjet.A_CORRIGER ? "erreur" : "actif";
            } else {
                etat = "avenir";
            }
            etapes.add(new EtapeWorkflowVue(LIBELLES_ETAPES_WORKFLOW[i - 1], ICONES_ETAPES_WORKFLOW[i - 1], etat));
        }
        return etapes;
    }

    private record EtapeWorkflowVue(String libelle, String icone, String etat) {
    }
}
