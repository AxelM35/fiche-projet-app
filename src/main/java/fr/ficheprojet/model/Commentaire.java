package fr.ficheprojet.model;

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
 * Message du fil de commentaires d'un dossier (échanges entre organisateur
 * et valideurs, indépendants du motif de refus). Pas de relation JPA vers
 * Projet (simple projetId, comme JournalEntree) : la suppression est gérée
 * explicitement par ProjetService.supprimerDefinitivement.
 */
@Entity
@Table(name = "commentaires")
@Getter
@Setter
@NoArgsConstructor
public class Commentaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projetId;

    @Column(nullable = false)
    private String auteurEmail;

    /**
     * Libellé du rôle sous lequel l'auteur a posté (ex. "Direction",
     * "Professeur") : capture au moment de l'écriture, indépendant d'une
     * éventuelle évolution ultérieure de ses attributions de rôle.
     */
    private String auteurRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texte;

    @Column(nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    /**
     * Renseignée uniquement si le commentaire a été modifié après coup
     * (affiche "modifié le ..." dans le fil), null sinon.
     */
    private LocalDateTime dateModification;
}
