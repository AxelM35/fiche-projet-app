package fr.collegesthelier.voyages.repository;

import fr.collegesthelier.voyages.model.Projet;
import fr.collegesthelier.voyages.model.StatutProjet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjetRepository extends JpaRepository<Projet, Long> {

    List<Projet> findByStatutOrderByDateDepartAsc(StatutProjet statut);

    List<Projet> findByOrganisateurEmailIgnoreCaseOrderByIdDesc(String organisateurEmail);
}
