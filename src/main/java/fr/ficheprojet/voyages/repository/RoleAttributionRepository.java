package fr.ficheprojet.voyages.repository;

import fr.ficheprojet.voyages.model.RoleAttribution;
import fr.ficheprojet.voyages.model.RoleMetier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleAttributionRepository extends JpaRepository<RoleAttribution, Long> {

    boolean existsByEmailAndRole(String email, RoleMetier role);

    List<RoleAttribution> findAllByOrderByRoleAscEmailAsc();
}
