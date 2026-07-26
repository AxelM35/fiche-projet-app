package fr.collegesthelier.ficheprojet.model;

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
 * Message du fil de commentaires d'un dossier (echanges entre organisateur
 * et valideurs, independants du motif de refus). Pas de relation JPA vers
 * Projet (simple projetId, comme JournalEntree) : la suppression est geree
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
     * Libelle du role sous lequel l'auteur a poste (ex. "Direction",
     * "Professeur") : capture au moment de l'ecriture, independant d'une
     * eventuelle evolution ulterieure de ses attributions de role.
     */
    private String auteurRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texte;

    @Column(nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    /**
     * Renseignee uniquement si le commentaire a ete modifie apres coup
     * (affiche "modifie le ..." dans le fil), null sinon.
     */
    private LocalDateTime dateModification;
}
