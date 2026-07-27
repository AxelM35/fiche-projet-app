package fr.collegesthelier.ficheprojet.web;

import fr.collegesthelier.ficheprojet.dto.SignalementErreurFormDTO;
import fr.collegesthelier.ficheprojet.service.NotificationService;
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
 * decrit ce qu'il faisait avant d'arriver sur l'erreur, transmis par email a
 * l'administrateur avec le contexte technique deja connu (chemin d'origine,
 * code HTTP fournis par des champs caches, voir le fragment) plutot que de
 * lui demander de le retrouver lui-meme. Route ouverte sans authentification
 * (voir SecurityConfig) : une erreur peut survenir avant meme la connexion
 * (lien perime, session expiree...).
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
