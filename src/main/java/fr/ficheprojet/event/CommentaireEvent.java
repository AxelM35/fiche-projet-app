package fr.ficheprojet.event;

import lombok.Getter;

import java.util.List;

/**
 * Événement publié par CommentaireService à chaque nouveau commentaire sur
 * un dossier. Consommé uniquement par NotificationService pour notifier les
 * autres participants du fil (voir CommentaireService.ajouter), en dehors du
 * thread de la requête web (voir @Async).
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
