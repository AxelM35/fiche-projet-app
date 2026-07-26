package fr.collegesthelier.ficheprojet.repository;

import fr.collegesthelier.ficheprojet.model.RoleAttribution;
import fr.collegesthelier.ficheprojet.model.RoleMetier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleAttributionRepository extends JpaRepository<RoleAttribution, Long> {

    boolean existsByEmailAndRole(String email, RoleMetier role);

    List<RoleAttribution> findAllByOrderByRoleAscEmailAsc();
}
