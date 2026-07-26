package fr.collegesthelier.ficheprojet.exception;

/**
 * Levee lorsqu'une action de workflow est demandee sur un projet dont le
 * statut courant ne le permet pas (bouton actionne deux fois, dossier deja
 * traite par un autre utilisateur entre-temps, etc.).
 */
public class TransitionInvalideException extends RuntimeException {

    public TransitionInvalideException(String message) {
        super(message);
    }
}
