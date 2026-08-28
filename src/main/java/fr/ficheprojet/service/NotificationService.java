package fr.ficheprojet.service;

import fr.ficheprojet.config.NotificationProperties;
import fr.ficheprojet.config.RolesProperties;
import fr.ficheprojet.event.CommentaireEvent;
import fr.ficheprojet.event.ProjetEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

/**
 * Envoie les emails de notification à chaque changement de statut, au
 * format HTML (template Thymeleaf email/notification.html) avec repli en
 * texte brut pour les clients mail qui ne rendent pas le HTML.
 * <p>
 * - @TransactionalEventListener(AFTER_COMMIT) : l'email ne part que si la
 *   transaction qui a changé le statut a bien été validée (pas de
 *   notification pour un changement finalement annulé / rollback).
 * - @Async("mailExecutor") : l'envoi (I/O réseau potentiellement lent) est
 *   délégué à un pool de threads dédié et ne bloque jamais le thread de la
 *   requête web qui a déclenché l'action.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final RolesProperties rolesProperties;
    private final NotificationProperties notificationProperties;
    private final NotificationToggleService notificationToggleService;
    private final TemplateEngine templateEngine;

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surChangementDeStatut(ProjetEvent evenement) {
        if (!notificationToggleService.sontActives()) {
            log.info("Notifications desactivees (interrupteur admin) : email non envoye pour le projet {}",
                    evenement.getProjetId());
            return;
        }
        switch (evenement.getNouveauStatut()) {
            case EN_ATTENTE_COMPTA -> notifier(rolesProperties.getCompta(),
                    "Nouveau dossier à valider : " + evenement.getNomProjet(),
                    "Le dossier \"" + evenement.getNomProjet() + "\" attend votre validation comptable.",
                    null, urlDossier(evenement.getProjetId()));
            case EN_ATTENTE_VIE_SCOLAIRE -> {
                notifier(rolesProperties.getViesco(),
                        "Nouveau dossier à valider : " + evenement.getNomProjet(),
                        "Le dossier \"" + evenement.getNomProjet() + "\" a été validé par la comptabilité et attend votre validation.",
                        null, urlDossier(evenement.getProjetId()));
                notifier(List.of(evenement.getOrganisateurEmail()),
                        "Dossier en cours : " + evenement.getNomProjet(),
                        "Votre dossier \"" + evenement.getNomProjet() + "\" a été validé par la Comptabilité. "
                                + "Il est maintenant en attente de validation par la Vie Scolaire.",
                        null, urlDossier(evenement.getProjetId()));
            }
            case EN_ATTENTE_DIRECTION -> {
                notifier(rolesProperties.getDirection(),
                        "Nouveau dossier à valider : " + evenement.getNomProjet(),
                        "Le dossier \"" + evenement.getNomProjet() + "\" attend la validation finale de la direction.",
                        null, urlDossier(evenement.getProjetId()));
                notifier(List.of(evenement.getOrganisateurEmail()),
                        "Dossier en cours : " + evenement.getNomProjet(),
                        "Votre dossier \"" + evenement.getNomProjet() + "\" a été validé par la Vie Scolaire. "
                                + "Il est maintenant en attente de validation par la Direction.",
                        null, urlDossier(evenement.getProjetId()));
            }
            case VALIDE -> notifier(List.of(evenement.getOrganisateurEmail()),
                    "Dossier validé : " + evenement.getNomProjet(),
                    "Bonne nouvelle : votre dossier \"" + evenement.getNomProjet() + "\" a été validé par la direction.",
                    null, urlDossier(evenement.getProjetId()));
            case A_CORRIGER -> notifier(List.of(evenement.getOrganisateurEmail()),
                    "Dossier à corriger : " + evenement.getNomProjet(),
                    "Votre dossier \"" + evenement.getNomProjet() + "\" a été refusé et nécessite des corrections.",
                    evenement.getMotifRefus(), urlDossier(evenement.getProjetId()));
            default -> log.debug("Aucune notification prevue pour le statut {}", evenement.getNouveauStatut());
        }
    }

    /**
     * Notifie les autres participants du fil de commentaires (voir
     * CommentaireService.ajouter) qu'un nouveau message a été posté :
     * uniquement ceux qui ont déjà écrit dans ce fil, jamais l'auteur du
     * nouveau commentaire lui-même (déjà filtré en amont).
     */
    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surNouveauCommentaire(CommentaireEvent evenement) {
        if (!notificationToggleService.sontActives()) {
            log.info("Notifications desactivees (interrupteur admin) : email non envoye pour le commentaire sur le projet {}",
                    evenement.getProjetId());
            return;
        }
        notifier(evenement.getDestinataires(),
                "Nouveau commentaire : " + evenement.getNomProjet(),
                evenement.getAuteurEmail() + " a ajouté un commentaire sur le dossier \"" + evenement.getNomProjet() + "\".",
                null, urlDossier(evenement.getProjetId()));
    }

    /**
     * Chaque appel est indépendant des autres (certains statuts notifient à
     * la fois le valideur suivant et l'organisateur, voir EN_ATTENTE_VIE_SCOLAIRE
     * / EN_ATTENTE_DIRECTION ci-dessus) : un incident d'envoi pour l'un des
     * deux destinataires ne doit ni empêcher l'autre, ni faire échouer le
     * workflow métier (le changement de statut est déjà validé et persiste
     * au moment où ce listener s'exécute, phase AFTER_COMMIT).
     * <p>
     * Visibilité package (pas private) : réutilise telle quelle par
     * RelanceService pour les relances automatiques, même construction
     * d'email (HTML + repli texte) et même résilience aux échecs d'envoi.
     */
    void notifier(List<String> destinataires, String sujet, String message, String motifRefus, String lienDossier) {
        if (destinataires == null || destinataires.isEmpty()) {
            log.warn("Aucun destinataire configure pour la notification : {}", sujet);
            return;
        }

        try {
            mailSender.send(construireMessage(destinataires, sujet, message, motifRefus, lienDossier));
        } catch (RuntimeException e) {
            log.error("Echec de l'envoi de la notification \"{}\" a {}", sujet, destinataires, e);
        }
    }

    /**
     * Envoi synchrone (pas @Async, pas de catch) déclenché depuis le
     * dashboard admin pour vérifier la configuration SMTP : contrairement à
     * notifier(), l'appelant doit voir immédiatement si l'envoi a échoué.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void envoyerEmailTest(String destinataire) {
        mailSender.send(construireMessage(List.of(destinataire), "Email de test - Fiche Projet numérique",
                "Ceci est un email de test envoyé depuis le dashboard admin de l'application "
                        + "Fiche Projet numérique, pour vérifier la configuration SMTP.",
                null, null));
    }

    /**
     * Signalement volontaire depuis une page d'erreur (403/404/500, voir
     * SignalementErreurController et fragments/signalement-erreur.html) :
     * transmis aux administrateurs configurés (rolesProperties.getAdmin(),
     * même source que les autres notifications de ce service) avec le
     * contexte technique déjà connu (chemin d'origine, code HTTP) en plus du
     * message libre de l'utilisateur.
     */
    @Async("mailExecutor")
    public void signalerErreur(String emailUtilisateur, Integer statutHttp, String cheminOrigine, String messageUtilisateur) {
        if (!notificationToggleService.sontActives()) {
            log.info("Notifications désactivées (interrupteur admin) : signalement d'erreur non transmis ({})",
                    cheminOrigine);
            return;
        }
        String sujet = "Signalement depuis une page d'erreur"
                + (statutHttp != null ? " (" + statutHttp + ")" : "");
        String corps = "Utilisateur : " + emailUtilisateur
                + "\nPage à l'origine : " + (cheminOrigine != null && !cheminOrigine.isBlank() ? cheminOrigine : "inconnue")
                + "\nCode HTTP : " + (statutHttp != null ? statutHttp : "inconnu")
                + "\n\nMessage :\n" + messageUtilisateur;
        notifier(rolesProperties.getAdmin(), sujet, corps, null, null);
    }

    private MimeMessage construireMessage(List<String> destinataires, String sujet, String message,
                                           String motifRefus, String lienDossier) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(notificationProperties.getEmailExpediteur());
            helper.setTo(destinataires.toArray(new String[0]));
            helper.setSubject(sujet);
            helper.setText(construireTexteBrut(message, motifRefus, lienDossier),
                    construireHtml(sujet, message, motifRefus, lienDossier));
            return mimeMessage;
        } catch (MessagingException e) {
            // MimeMessageHelper lève une exception checked que JavaMailSender.send(...)
            // n'attend pas : on la convertit en MailException (comme le fait déjà
            // Spring en interne) pour que les catch existants (AdminController,
            // surChangementDeStatut ci-dessus) continuent de fonctionner sans changement.
            throw new MailPreparationException("Échec de la préparation de l'email : " + sujet, e);
        }
    }

    private String construireHtml(String titre, String message, String motifRefus, String lienDossier) {
        Context contexte = new Context(Locale.FRENCH);
        contexte.setVariable("titre", titre);
        contexte.setVariable("message", message);
        contexte.setVariable("motifRefus", motifRefus);
        contexte.setVariable("lienDossier", lienDossier);
        return templateEngine.process("email/notification", contexte);
    }

    private String construireTexteBrut(String message, String motifRefus, String lienDossier) {
        StringBuilder texte = new StringBuilder(message);
        if (motifRefus != null) {
            texte.append("\n\nMotif : ").append(motifRefus);
        }
        if (lienDossier != null) {
            texte.append("\n\nConsulter le dossier : ").append(lienDossier);
        }
        return texte.toString();
    }

    private String urlDossier(Long projetId) {
        return notificationProperties.getUrlApplication() + "/projets/" + projetId;
    }
}
