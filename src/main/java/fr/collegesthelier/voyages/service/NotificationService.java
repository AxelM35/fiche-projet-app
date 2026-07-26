package fr.collegesthelier.voyages.service;

import fr.collegesthelier.voyages.config.NotificationProperties;
import fr.collegesthelier.voyages.config.RolesProperties;
import fr.collegesthelier.voyages.event.ProjetEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Envoie les emails de notification a chaque changement de statut.
 * <p>
 * - @TransactionalEventListener(AFTER_COMMIT) : l'email ne part que si la
 *   transaction qui a change le statut a bien ete validee (pas de
 *   notification pour un changement finalement annule / rollback).
 * - @Async("mailExecutor") : l'envoi (I/O reseau potentiellement lent) est
 *   delegue a un pool de threads dedie et ne bloque jamais le thread de la
 *   requete web qui a declenche l'action.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final RolesProperties rolesProperties;
    private final NotificationProperties notificationProperties;
    private final NotificationToggleService notificationToggleService;

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surChangementDeStatut(ProjetEvent evenement) {
        if (!notificationToggleService.sontActives()) {
            log.info("Notifications desactivees (interrupteur admin) : email non envoye pour le projet {}",
                    evenement.getProjetId());
            return;
        }
        try {
            switch (evenement.getNouveauStatut()) {
                case EN_ATTENTE_COMPTA -> notifier(rolesProperties.getCompta(),
                        "Nouveau dossier a valider : " + evenement.getNomProjet(),
                        "Le dossier \"" + evenement.getNomProjet() + "\" attend votre validation comptable."
                                + lienDossier(evenement.getProjetId()));
                case EN_ATTENTE_VIE_SCOLAIRE -> notifier(rolesProperties.getViesco(),
                        "Nouveau dossier a valider : " + evenement.getNomProjet(),
                        "Le dossier \"" + evenement.getNomProjet() + "\" a ete valide par la comptabilite et attend votre validation."
                                + lienDossier(evenement.getProjetId()));
                case EN_ATTENTE_DIRECTION -> notifier(rolesProperties.getDirection(),
                        "Nouveau dossier a valider : " + evenement.getNomProjet(),
                        "Le dossier \"" + evenement.getNomProjet() + "\" attend la validation finale de la direction."
                                + lienDossier(evenement.getProjetId()));
                case VALIDE -> notifier(List.of(evenement.getOrganisateurEmail()),
                        "Dossier valide : " + evenement.getNomProjet(),
                        "Bonne nouvelle : votre dossier \"" + evenement.getNomProjet() + "\" a ete valide par la direction."
                                + lienDossier(evenement.getProjetId()));
                case A_CORRIGER -> notifier(List.of(evenement.getOrganisateurEmail()),
                        "Dossier a corriger : " + evenement.getNomProjet(),
                        "Votre dossier \"" + evenement.getNomProjet() + "\" a ete refuse et necessite des corrections.\n"
                                + "Motif : " + evenement.getMotifRefus()
                                + lienDossier(evenement.getProjetId()));
                default -> log.debug("Aucune notification prevue pour le statut {}", evenement.getNouveauStatut());
            }
        } catch (RuntimeException e) {
            // Un incident d'envoi d'email ne doit jamais faire echouer le workflow
            // metier : le changement de statut est deja valide et persiste au
            // moment ou ce listener s'execute (phase AFTER_COMMIT).
            log.error("Echec de l'envoi de la notification pour le projet {}", evenement.getProjetId(), e);
        }
    }

    private void notifier(List<String> destinataires, String sujet, String corps) {
        if (destinataires == null || destinataires.isEmpty()) {
            log.warn("Aucun destinataire configure pour la notification : {}", sujet);
            return;
        }

        mailSender.send(construireMessage(destinataires, sujet, corps));
    }

    /**
     * Envoi synchrone (pas @Async, pas de catch) declenche depuis le
     * dashboard admin pour verifier la configuration SMTP : contrairement a
     * notifier(), l'appelant doit voir immediatement si l'envoi a echoue.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void envoyerEmailTest(String destinataire) {
        mailSender.send(construireMessage(List.of(destinataire), "Email de test - Voyages Scolaires",
                "Ceci est un email de test envoye depuis le dashboard admin de l'application "
                        + "Voyages Scolaires, pour verifier la configuration SMTP."));
    }

    private SimpleMailMessage construireMessage(List<String> destinataires, String sujet, String corps) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(notificationProperties.getEmailExpediteur());
        message.setTo(destinataires.toArray(new String[0]));
        message.setSubject(sujet);
        message.setText(corps);
        return message;
    }

    private String lienDossier(Long projetId) {
        return "\n\nConsulter le dossier : " + notificationProperties.getUrlApplication() + "/projets/" + projetId;
    }
}
