package fr.collegesthelier.voyages.web;

import fr.collegesthelier.voyages.dto.ProjetFormDTO;
import fr.collegesthelier.voyages.dto.RefusFormDTO;
import fr.collegesthelier.voyages.model.Projet;
import fr.collegesthelier.voyages.model.StatutProjet;
import fr.collegesthelier.voyages.service.ProjetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        return "formulaire";
    }

    @GetMapping("/projets/{id}")
    public String formulaireEdition(@PathVariable Long id, Model model, Authentication authentication) {
        Projet projet = projetService.trouverParId(id);

        // Un dossier definitivement valide n'est plus modifiable, et un
        // observateur en lecture seule ne doit jamais voir un formulaire
        // editable (meme sans bouton actif) : dans les deux cas, on affiche
        // la vue de consultation plutot que le formulaire.
        if (projet.getStatut() == StatutProjet.VALIDE || estEnLectureSeule(authentication)) {
            model.addAttribute("projet", projetService.chargerConsultation(id));
            return "consultation";
        }

        model.addAttribute("projet", projetService.chargerFormulaire(id));
        model.addAttribute("statutCourant", projet.getStatut());
        model.addAttribute("motifRefus", projet.getMotifRefus());
        return "formulaire";
    }

    private boolean estEnLectureSeule(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_LECTURE_SEULE"::equals);
    }

    @PostMapping("/projets/nouveau")
    public String creer(@Valid @ModelAttribute("projet") ProjetFormDTO dto, BindingResult bindingResult,
                         Model model, RedirectAttributes redirectAttributes) {
        validerCoherenceDates(dto, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("statutCourant", StatutProjet.BROUILLON);
            model.addAttribute("motifRefus", null);
            return "formulaire";
        }

        Projet projet = projetService.creerProjet(dto);
        redirectAttributes.addFlashAttribute("messageSucces", "Le projet a ete cree en brouillon.");
        return "redirect:/projets/" + projet.getId();
    }

    @PostMapping("/projets/{id}")
    public String modifier(@PathVariable Long id, @Valid @ModelAttribute("projet") ProjetFormDTO dto,
                            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        validerCoherenceDates(dto, bindingResult);
        if (bindingResult.hasErrors()) {
            Projet projetExistant = projetService.trouverParId(id);
            model.addAttribute("statutCourant", projetExistant.getStatut());
            model.addAttribute("motifRefus", projetExistant.getMotifRefus());
            return "formulaire";
        }

        projetService.modifierProjet(id, dto);
        redirectAttributes.addFlashAttribute("messageSucces", "Les modifications ont ete enregistrees.");
        return "redirect:/projets/" + id;
    }

    @PostMapping("/projets/{id}/dupliquer")
    public String dupliquer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Projet copie = projetService.dupliquer(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Le projet a ete duplique en brouillon. Pensez a adapter les classes et les dates.");
        return "redirect:/projets/" + copie.getId();
    }

    @PostMapping("/projets/{id}/soumettre")
    public String soumettre(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        projetService.soumettre(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Le dossier a ete soumis pour validation.");
        return "redirect:/dashboard";
    }

    @PostMapping("/projets/{id}/valider-compta")
    public String validerCompta(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        projetService.validerCompta(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Validation comptable enregistree.");
        return "redirect:/dashboard";
    }

    @PostMapping("/projets/{id}/valider-vie-scolaire")
    public String validerVieScolaire(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        projetService.validerVieScolaire(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Validation vie scolaire enregistree.");
        return "redirect:/dashboard";
    }

    @PostMapping("/projets/{id}/valider-direction")
    public String validerDirection(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        projetService.validerDirection(id);
        redirectAttributes.addFlashAttribute("messageSucces", "Le dossier a ete valide definitivement.");
        return "redirect:/dashboard";
    }

    @PostMapping("/projets/{id}/refuser")
    public String refuser(@PathVariable Long id, @Valid @ModelAttribute("refus") RefusFormDTO refusDto,
                           BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("erreurRefus", "Le motif de refus est obligatoire.");
            return "redirect:/dashboard";
        }

        projetService.refuser(id, refusDto.getMotifRefus());
        redirectAttributes.addFlashAttribute("messageSucces", "Le dossier a ete renvoye au professeur pour correction.");
        return "redirect:/dashboard";
    }

    private void validerCoherenceDates(ProjetFormDTO dto, BindingResult bindingResult) {
        if (dto.getDateDepart() != null && dto.getDateRetour() != null
                && dto.getDateRetour().isBefore(dto.getDateDepart())) {
            bindingResult.rejectValue("dateRetour", "date.incoherente",
                    "La date de retour doit etre posterieure ou egale a la date de depart.");
        }
    }
}
