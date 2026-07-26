package fr.collegesthelier.ficheprojet.service;

import fr.collegesthelier.ficheprojet.config.NotificationProperties;
import fr.collegesthelier.ficheprojet.config.RolesProperties;
import fr.collegesthelier.ficheprojet.event.CommentaireEvent;
import fr.collegesthelier.ficheprojet.event.ProjetEvent;
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
 * Envoie les emails de notification a chaque changement de statut, au
 * format HTML (template Thymeleaf email/notification.html) avec repli en
 * texte brut pour les clients mail qui ne rendent pas le HTML.
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
     * CommentaireService.ajouter) qu'un nouveau message a ete poste :
     * uniquement ceux qui ont deja ecrit dans ce fil, jamais l'auteur du
     * nouveau commentaire lui-meme (deja filtre en amont).
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
     * Chaque appel est independant des autres (certains statuts notifient a
     * la fois le valideur suivant et l'organisateur, voir EN_ATTENTE_VIE_SCOLAIRE
     * / EN_ATTENTE_DIRECTION ci-dessus) : un incident d'envoi pour l'un des
     * deux destinataires ne doit ni empecher l'autre, ni faire echouer le
     * workflow metier (le changement de statut est deja valide et persiste
     * au moment ou ce listener s'execute, phase AFTER_COMMIT).
     * <p>
     * Visibilite package (pas private) : reutilise telle quelle par
     * RelanceService pour les relances automatiques, meme construction
     * d'email (HTML + repli texte) et meme resilience aux echecs d'envoi.
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
     * Envoi synchrone (pas @Async, pas de catch) declenche depuis le
     * dashboard admin pour verifier la configuration SMTP : contrairement a
     * notifier(), l'appelant doit voir immediatement si l'envoi a echoue.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void envoyerEmailTest(String destinataire) {
        mailSender.send(construireMessage(List.of(destinataire), "Email de test - Fiche Projet numérique",
                "Ceci est un email de test envoyé depuis le dashboard admin de l'application "
                        + "Fiche Projet numérique, pour vérifier la configuration SMTP.",
                null, null));
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
            // MimeMessageHelper leve une exception checked que JavaMailSender.send(...)
            // n'attend pas : on la convertit en MailException (comme le fait deja
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
