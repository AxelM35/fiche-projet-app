package fr.collegesthelier.voyages.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entree du journal d'audit (dashboard admin). Le nom du projet est
 * denormalise (copie au moment de l'evenement, pas de relation JPA vers
 * Projet) pour deux raisons : rester lisible meme apres une suppression
 * definitive du dossier concerne, et ne jamais faire porter au journal une
 * contrainte referentielle qui empecherait cette suppression.
 */
@Entity
@Table(name = "journal_entrees")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntree {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime dateEvenement = LocalDateTime.now();

    private String auteurEmail;

    @Column(nullable = false)
    private String action;

    private Long projetId;

    private String projetNom;

    @Column(columnDefinition = "TEXT")
    private String detail;

    public JournalEntree(String auteurEmail, String action, Long projetId, String projetNom, String detail) {
        this.auteurEmail = auteurEmail;
        this.action = action;
        this.projetId = projetId;
        this.projetNom = projetNom;
        this.detail = detail;
    }
}
