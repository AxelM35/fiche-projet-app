package fr.ficheprojet.service;

import fr.ficheprojet.model.RoleAttribution;
import fr.ficheprojet.model.RoleMetier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie que la gestion des attributions de rôles (dashboard admin) est
 * bien réservée à ROLE_ADMIN, et que les emails sont normalisés / dedupliques.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoleAdminServiceTest {

    @Autowired
    private RoleAdminService roleAdminService;

    private void connecterEnTantQue(String email, String role) {
        Authentication authentication = new TestingAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void nettoyerContexteSecurite() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unNonAdminNePeutNiAjouterNiRetirerUneAttribution() {
        connecterEnTantQue("prof@exemple.fr", "ROLE_PROF");

        assertThatThrownBy(() -> roleAdminService.ajouter("secretariat@exemple.fr", RoleMetier.LECTURE_SEULE))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> roleAdminService.retirer(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unAdminPeutAjouterEtRetirerUneAttributionEtLEmailEstNormalise() {
        connecterEnTantQue("admin@exemple.fr", "ROLE_ADMIN");

        roleAdminService.ajouter("  Secretariat@exemple.fr  ", RoleMetier.LECTURE_SEULE);

        Map<RoleMetier, List<RoleAttribution>> parRole = roleAdminService.listerParRole();
        assertThat(parRole.get(RoleMetier.LECTURE_SEULE))
                .extracting(RoleAttribution::getEmail)
                .containsExactly("secretariat@exemple.fr");

        // Ajouter deux fois le même couple email/rôle ne crée pas de doublon.
        roleAdminService.ajouter("secretariat@exemple.fr", RoleMetier.LECTURE_SEULE);
        assertThat(roleAdminService.listerParRole().get(RoleMetier.LECTURE_SEULE)).hasSize(1);

        Long id = roleAdminService.listerParRole().get(RoleMetier.LECTURE_SEULE).get(0).getId();
        roleAdminService.retirer(id);
        assertThat(roleAdminService.listerParRole().get(RoleMetier.LECTURE_SEULE)).isEmpty();
    }
}
