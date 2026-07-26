package fr.collegesthelier.voyages.service;

import fr.collegesthelier.voyages.model.RoleAttribution;
import fr.collegesthelier.voyages.model.RoleMetier;
import fr.collegesthelier.voyages.repository.RoleAttributionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gestion des attributions de roles depuis le dashboard admin. S'ajoute
 * toujours aux listes d'emails configurees en variables d'environnement
 * (RolesProperties), jamais ne les remplace : voir CustomOAuth2UserService,
 * qui verifie l'union des deux sources a chaque connexion.
 */
@Service
@RequiredArgsConstructor
public class RoleAdminService {

    private final RoleAttributionRepository roleAttributionRepository;

    @Transactional(readOnly = true)
    public Map<RoleMetier, List<RoleAttribution>> listerParRole() {
        Map<RoleMetier, List<RoleAttribution>> parRole = new LinkedHashMap<>();
        for (RoleMetier role : RoleMetier.values()) {
            parRole.put(role, new ArrayList<>());
        }
        roleAttributionRepository.findAllByOrderByRoleAscEmailAsc()
                .forEach(attribution -> parRole.get(attribution.getRole()).add(attribution));
        return parRole;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void ajouter(String email, RoleMetier role) {
        String emailNormalise = email.trim().toLowerCase(Locale.ROOT);
        if (!roleAttributionRepository.existsByEmailAndRole(emailNormalise, role)) {
            roleAttributionRepository.save(new RoleAttribution(emailNormalise, role));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void retirer(Long id) {
        roleAttributionRepository.deleteById(id);
    }
}
