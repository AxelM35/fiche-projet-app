package fr.collegesthelier.voyages.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class NotificationToggleServiceTest {

    @Autowired
    private NotificationToggleService notificationToggleService;

    private void connecterEnTantQue(String email, String role) {
        Authentication authentication = new TestingAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void nettoyer() {
        // Remet l'etat par defaut pour ne pas impacter d'autres tests (bean singleton).
        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        notificationToggleService.activer();
        SecurityContextHolder.clearContext();
    }

    @Test
    void unNonAdminNePeutPasBasculerLinterrupteur() {
        connecterEnTantQue("prof@college-sthelier.fr", "ROLE_PROF");
        assertThatThrownBy(notificationToggleService::desactiver).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(notificationToggleService::activer).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unAdminPeutDesactiverPuisReactiverLesNotifications() {
        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        assertThat(notificationToggleService.sontActives()).isTrue();

        notificationToggleService.desactiver();
        assertThat(notificationToggleService.sontActives()).isFalse();

        notificationToggleService.activer();
        assertThat(notificationToggleService.sontActives()).isTrue();
    }
}
