package fr.collegesthelier.ficheprojet.web;

import fr.collegesthelier.ficheprojet.exception.ProjetNotFoundException;
import fr.collegesthelier.ficheprojet.exception.TransitionInvalideException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Traduit les exceptions metier en messages utilisateur lisibles plutot que
 * de laisser remonter une page d'erreur technique. Redirige vers la page
 * d'origine (Referer) quand elle est connue, sinon vers le tableau de bord.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProjetNotFoundException.class)
    public String gererProjetIntrouvable(ProjetNotFoundException exception, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("messageErreur", exception.getMessage());
        return "redirect:/dashboard";
    }

    @ExceptionHandler(TransitionInvalideException.class)
    public String gererTransitionInvalide(TransitionInvalideException exception, HttpServletRequest request,
                                           RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("messageErreur", exception.getMessage());
        return "redirect:" + pageOrigine(request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String gererConflitConcurrence(ObjectOptimisticLockingFailureException exception, HttpServletRequest request,
                                           RedirectAttributes redirectAttributes) {
        log.warn("Conflit de mise a jour concurrente detecte", exception);
        redirectAttributes.addFlashAttribute("messageErreur",
                "Ce dossier a été modifié entre-temps par quelqu'un d'autre. Merci de recharger la page et de réessayer.");
        return "redirect:" + pageOrigine(request);
    }

    private String pageOrigine(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        return referer != null ? referer : "/dashboard";
    }
}
