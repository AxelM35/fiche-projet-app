package fr.collegesthelier.voyages.repository;

import fr.collegesthelier.voyages.model.RoleAttribution;
import fr.collegesthelier.voyages.model.RoleMetier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleAttributionRepository extends JpaRepository<RoleAttribution, Long> {

    boolean existsByEmailAndRole(String email, RoleMetier role);

    List<RoleAttribution> findAllByOrderByRoleAscEmailAsc();
}
