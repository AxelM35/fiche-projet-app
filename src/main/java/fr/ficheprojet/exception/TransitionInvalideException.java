package fr.ficheprojet.exception;

/**
 * Levée lorsqu'une action de workflow est demandée sur un projet dont le
 * statut courant ne le permet pas (bouton actionné deux fois, dossier déjà
 * traité par un autre utilisateur entre-temps, etc.).
 */
public class TransitionInvalideException extends RuntimeException {

    public TransitionInvalideException(String message) {
        super(message);
    }
}
