package fr.collegesthelier.voyages.exception;

public class ProjetNotFoundException extends RuntimeException {

    public ProjetNotFoundException(Long id) {
        super("Aucun projet trouve avec l'identifiant " + id);
    }
}
