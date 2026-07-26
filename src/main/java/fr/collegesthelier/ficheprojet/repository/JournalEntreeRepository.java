package fr.collegesthelier.ficheprojet.repository;

import fr.collegesthelier.ficheprojet.model.JournalEntree;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JournalEntreeRepository extends JpaRepository<JournalEntree, Long> {

    List<JournalEntree> findTop200ByOrderByDateEvenementDesc();

    /**
     * Historique complet d'un dossier (utilise pour l'export PDF), du plus
     * ancien au plus recent.
     */
    List<JournalEntree> findByProjetIdOrderByDateEvenementAsc(Long projetId);

    /**
     * Derniere relance automatique envoyee pour ce projet depuis qu'il est
     * entre dans son statut de blocage courant (voir RelanceService) : permet
     * de savoir si/quand relancer sans ajouter de champ dedie sur Projet.
     */
    Optional<JournalEntree> findTopByProjetIdAndActionAndDateEvenementAfterOrderByDateEvenementDesc(
            Long projetId, String action, LocalDateTime after);
}
