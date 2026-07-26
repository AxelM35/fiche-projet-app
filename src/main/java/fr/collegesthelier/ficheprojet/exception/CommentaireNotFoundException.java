package fr.collegesthelier.ficheprojet.exception;

public class CommentaireNotFoundException extends RuntimeException {

    public CommentaireNotFoundException(Long id) {
        super("Aucun commentaire trouvé avec l'identifiant " + id);
    }
}
