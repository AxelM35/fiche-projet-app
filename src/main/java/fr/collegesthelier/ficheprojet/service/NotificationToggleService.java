package fr.collegesthelier.ficheprojet.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Interrupteur temporaire des notifications email, pilotable par un Admin
 * depuis le dashboard (utile en periode de demo/test). Volontairement en
 * memoire (pas de persistance) : redemarrer l'application reactive les
 * notifications par defaut, c'est le comportement attendu pour un reglage
 * "temporaire".
 */
@Service
public class NotificationToggleService {

    private volatile boolean actives = true;

    public boolean sontActives() {
        return actives;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void activer() {
        actives = true;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void desactiver() {
        actives = false;
    }
}
