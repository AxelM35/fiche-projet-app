package fr.ficheprojet.repository;

import fr.ficheprojet.model.JournalEntree;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JournalEntreeRepository extends JpaRepository<JournalEntree, Long> {

    List<JournalEntree> findTop200ByOrderByDateEvenementDesc();

    /**
     * Historique complet d'un dossier (utilisé pour l'export PDF), du plus
     * ancien au plus récent.
     */
    List<JournalEntree> findByProjetIdOrderByDateEvenementAsc(Long projetId);

    /**
     * Dernière relance automatique envoyée pour ce projet depuis qu'il est
     * entre dans son statut de blocage courant (voir RelanceService) : permet
     * de savoir si/quand relancer sans ajouter de champ dédié sur Projet.
     */
    Optional<JournalEntree> findTopByProjetIdAndActionAndDateEvenementAfterOrderByDateEvenementDesc(
            Long projetId, String action, LocalDateTime after);
}
