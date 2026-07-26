package fr.ficheprojet.repository;

import fr.ficheprojet.model.Commentaire;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentaireRepository extends JpaRepository<Commentaire, Long> {

    List<Commentaire> findByProjetIdOrderByDateCreationAsc(Long projetId);

    void deleteByProjetId(Long projetId);
}
