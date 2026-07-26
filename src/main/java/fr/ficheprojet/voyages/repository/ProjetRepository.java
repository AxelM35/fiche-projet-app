package fr.ficheprojet.voyages.repository;

import fr.ficheprojet.voyages.model.Projet;
import fr.ficheprojet.voyages.model.StatutProjet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjetRepository extends JpaRepository<Projet, Long> {

    List<Projet> findByStatutAndArchiveFalseOrderByDateDepartAsc(StatutProjet statut);

    List<Projet> findByArchiveTrueOrderByDateDepartDesc();

    List<Projet> findByOrganisateurEmailIgnoreCaseOrderByIdDesc(String organisateurEmail);
}
