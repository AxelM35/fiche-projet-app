package fr.ficheprojet.event;

import fr.ficheprojet.model.StatutProjet;
import lombok.Getter;

/**
 * Événement métier publié par ProjetService à chaque changement de statut.
 * Consommé uniquement par NotificationService pour router les emails de
 * notification, en dehors du thread de la requête web (voir @Async).
 */
@Getter
public class ProjetEvent {

    private final Long projetId;
    private final String nomProjet;
    private final String organisateurEmail;
    private final StatutProjet ancienStatut;
    private final StatutProjet nouveauStatut;
    private final String motifRefus;

    public ProjetEvent(Long projetId, String nomProjet, String organisateurEmail,
                        StatutProjet ancienStatut, StatutProjet nouveauStatut, String motifRefus) {
        this.projetId = projetId;
        this.nomProjet = nomProjet;
        this.organisateurEmail = organisateurEmail;
        this.ancienStatut = ancienStatut;
        this.nouveauStatut = nouveauStatut;
        this.motifRefus = motifRefus;
    }
}
