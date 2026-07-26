package fr.ficheprojet.repository;

import fr.ficheprojet.model.RoleAttribution;
import fr.ficheprojet.model.RoleMetier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleAttributionRepository extends JpaRepository<RoleAttribution, Long> {

    boolean existsByEmailAndRole(String email, RoleMetier role);

    List<RoleAttribution> findAllByOrderByRoleAscEmailAsc();
}
