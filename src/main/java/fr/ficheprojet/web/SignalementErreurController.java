package fr.ficheprojet.web;

import fr.ficheprojet.dto.SignalementErreurFormDTO;
import fr.ficheprojet.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Signalement volontaire depuis une page d'erreur (403/404/500, voir
 * templates/error/ et fragments/signalement-erreur.html) : l'utilisateur
 * décrit ce qu'il faisait avant d'arriver sur l'erreur, transmis par email à
 * l'administrateur avec le contexte technique déjà connu (chemin d'origine,
 * code HTTP fournis par des champs cachés, voir le fragment) plutôt que de
 * lui demander de le retrouver lui-même. Route ouverte sans authentification
 * (voir SecurityConfig) : une erreur peut survenir avant même la connexion
 * (lien périmé, session expirée...).
 */
@Controller
@RequiredArgsConstructor
public class SignalementErreurController {

    private final NotificationService notificationService;

    @PostMapping("/error/signalement")
    public String envoyer(@Valid @ModelAttribute SignalementErreurFormDTO dto, BindingResult bindingResult,
                           Authentication authentication, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("messageErreur", "Merci de préciser votre message avant d'envoyer.");
            return "redirect:/dashboard";
        }

        String emailUtilisateur = authentication != null ? authentication.getName() : "utilisateur non connecté";
        notificationService.signalerErreur(emailUtilisateur, dto.getStatutHttp(), dto.getCheminOrigine(), dto.getMessage());
        redirectAttributes.addFlashAttribute("messageSucces", "Merci, votre message a été transmis à l'administrateur.");
        return "redirect:/dashboard";
    }
}
