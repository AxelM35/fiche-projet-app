package fr.ficheprojet.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Interrupteur temporaire des notifications email, pilotable par un Admin
 * depuis le dashboard (utile en période de demo/test). Volontairement en
 * mémoire (pas de persistance) : redémarrer l'application réactive les
 * notifications par défaut, c'est le comportement attendu pour un réglage
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
