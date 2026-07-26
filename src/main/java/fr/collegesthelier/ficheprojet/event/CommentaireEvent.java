package fr.collegesthelier.ficheprojet.event;

import lombok.Getter;

import java.util.List;

/**
 * Evenement publie par CommentaireService a chaque nouveau commentaire sur
 * un dossier. Consomme uniquement par NotificationService pour notifier les
 * autres participants du fil (voir CommentaireService.ajouter), en dehors du
 * thread de la requete web (voir @Async).
 */
@Getter
public class CommentaireEvent {

    private final Long projetId;
    private final String nomProjet;
    private final String auteurEmail;
    private final List<String> destinataires;

    public CommentaireEvent(Long projetId, String nomProjet, String auteurEmail, List<String> destinataires) {
        this.projetId = projetId;
        this.nomProjet = nomProjet;
        this.auteurEmail = auteurEmail;
        this.destinataires = destinataires;
    }
}
