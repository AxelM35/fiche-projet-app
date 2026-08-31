package fr.ficheprojet.service;

import fr.ficheprojet.config.NotificationProperties;
import fr.ficheprojet.config.RelanceProperties;
import fr.ficheprojet.config.RolesProperties;
import fr.ficheprojet.dto.ProjetBloqueDTO;
import fr.ficheprojet.model.JournalEntree;
import fr.ficheprojet.model.StatutProjet;
import fr.ficheprojet.repository.JournalEntreeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Relance automatique par email des dossiers bloqués depuis trop longtemps
 * à une étape de validation (Compta/Vie Scolaire/Direction). S'appuie sur
 * ProjetService.listerDossiersBloques() (même logique que la page admin
 * "Dossiers bloqués") et sur le journal d'audit pour savoir si/quand une
 * relance a déjà été envoyée durant le blocage courant : pas de nouveau
 * champ sur Projet, la dernière entrée "Relance" postérieure à la date
 * d'entrée dans le statut fait foi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelanceService {

    private static final String ACTION_RELANCE = "Relance";
    private static final String AUTEUR_SYSTEME = "Système (relance automatique)";

    private final ProjetService projetService;
    private final JournalEntreeRepository journalEntreeRepository;
    private final JournalService journalService;
    private final RolesProperties rolesProperties;
    private final NotificationProperties notificationProperties;
    private final NotificationToggleService notificationToggleService;
    private final RelanceProperties relanceProperties;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 8 * * *")
    public void relancerDossiersBloques() {
        if (!notificationToggleService.sontActives()) {
            log.info("Notifications desactivees (interrupteur admin) : relances automatiques non envoyees.");
            return;
        }

        for (ProjetBloqueDTO dossier : projetService.listerDossiersBloques()) {
            relancerSiNecessaire(dossier);
        }
    }

    private void relancerSiNecessaire(ProjetBloqueDTO dossier) {
        if (dossier.joursEnAttente() < relanceProperties.getSeuilJours()) {
            return;
        }

        Optional<JournalEntree> derniereRelance = journalEntreeRepository
                .findTopByProjetIdAndActionAndDateEvenementAfterOrderByDateEvenementDesc(
                        dossier.id(), ACTION_RELANCE, dossier.enAttenteDepuis());

        if (derniereRelance.isPresent()) {
            long joursDepuisDerniereRelance = Duration.between(derniereRelance.get().getDateEvenement(), LocalDateTime.now()).toDays();
            if (joursDepuisDerniereRelance < relanceProperties.getPeriodeJours()) {
                return;
            }
        }

        List<String> destinataires = destinatairesPourStatut(dossier.statut());
        if (destinataires == null || destinataires.isEmpty()) {
            log.warn("Aucun destinataire configure pour relancer le dossier {} ({})", dossier.id(), dossier.statut());
            return;
        }

        String sujet = "Rappel : dossier en attente depuis " + dossier.joursEnAttente() + " jours - " + dossier.nomProjet();
        String message = "Le dossier \"" + dossier.nomProjet() + "\" (organisateur : " + dossier.organisateurNom()
                + ") attend votre validation depuis " + dossier.joursEnAttente() + " jours.";
        String lienDossier = notificationProperties.getUrlApplication() + "/projets/" + dossier.id();

        notificationService.notifier(destinataires, sujet, message, null, lienDossier);
        journalService.enregistrer(ACTION_RELANCE, dossier.id(), dossier.nomProjet(),
                "Relance après " + dossier.joursEnAttente() + " jours d'attente (" + dossier.statut() + ")", AUTEUR_SYSTEME);
    }

    private List<String> destinatairesPourStatut(StatutProjet statut) {
        return switch (statut) {
            case EN_ATTENTE_COMPTA -> rolesProperties.getCompta();
            case EN_ATTENTE_VIE_SCOLAIRE -> rolesProperties.getViesco();
            case EN_ATTENTE_DIRECTION -> rolesProperties.getDirection();
            default -> List.of();
        };
    }
}
