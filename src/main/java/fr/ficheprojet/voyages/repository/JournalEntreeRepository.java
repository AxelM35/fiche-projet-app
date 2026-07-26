package fr.ficheprojet.voyages.repository;

import fr.ficheprojet.voyages.model.JournalEntree;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JournalEntreeRepository extends JpaRepository<JournalEntree, Long> {

    List<JournalEntree> findTop200ByOrderByDateEvenementDesc();
}
