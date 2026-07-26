package fr.collegesthelier.ficheprojet.service;

import fr.collegesthelier.ficheprojet.event.CommentaireEvent;
import fr.collegesthelier.ficheprojet.exception.CommentaireNotFoundException;
import fr.collegesthelier.ficheprojet.model.Commentaire;
import fr.collegesthelier.ficheprojet.model.Projet;
import fr.collegesthelier.ficheprojet.repository.CommentaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fil de commentaires d'un dossier : echanges libres entre l'organisateur et
 * les valideurs (ex. la Direction demande une precision sans forcement
 * refuser), independants du motif de refus. Disponible quel que soit le
 * statut du workflow.
 * <p>
 * Autorisation d'ajouter un commentaire : exactement le meme perimetre que
 * ProjetService.peutGererLienDrive (organisateur du dossier ou n'importe
 * quel role de validation, ROLE_LECTURE_SEULE volontairement exclu). La
 * modification/suppression, elle, ne depend que de la propriete du
 * commentaire (son auteur), quels que soient ses roles actuels.
 */
@Service
@RequiredArgsConstructor
public class CommentaireService {

    private final CommentaireRepository commentaireRepository;
    private final ProjetService projetService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<Commentaire> lister(Long projetId) {
        return commentaireRepository.findByProjetIdOrderByDateCreationAsc(projetId);
    }

    @Transactional
    public Commentaire ajouter(Long projetId, String texte) {
        Projet projet = projetService.trouverParId(projetId);
        if (!projetService.peutGererLienDrive(projet)) {
            throw new AccessDeniedException("Vous n'êtes pas autorisé à commenter ce dossier.");
        }

        List<Commentaire> existants = commentaireRepository.findByProjetIdOrderByDateCreationAsc(projetId);
        String emailAuteur = emailUtilisateurConnecte();

        Commentaire commentaire = new Commentaire();
        commentaire.setProjetId(projetId);
        commentaire.setAuteurEmail(emailAuteur);
        commentaire.setAuteurRole(libelleRoleConnecte());
        commentaire.setTexte(texte);
        commentaire.setDateCreation(LocalDateTime.now());
        Commentaire enregistre = commentaireRepository.save(commentaire);

        List<String> destinataires = existants.stream()
                .map(Commentaire::getAuteurEmail)
                .filter(email -> email != null && !email.equalsIgnoreCase(emailAuteur))
                .distinct()
                .toList();
        if (!destinataires.isEmpty()) {
            eventPublisher.publishEvent(new CommentaireEvent(projetId, projet.getNomProjet(), emailAuteur, destinataires));
        }
        return enregistre;
    }

    @Transactional
    public void modifier(Long commentaireId, String texte) {
        Commentaire commentaire = trouverParId(commentaireId);
        verifierAuteur(commentaire);
        commentaire.setTexte(texte);
        commentaire.setDateModification(LocalDateTime.now());
        commentaireRepository.save(commentaire);
    }

    @Transactional
    public void supprimer(Long commentaireId) {
        Commentaire commentaire = trouverParId(commentaireId);
        verifierAuteur(commentaire);
        commentaireRepository.delete(commentaire);
    }

    private Commentaire trouverParId(Long id) {
        return commentaireRepository.findById(id)
                .orElseThrow(() -> new CommentaireNotFoundException(id));
    }

    private void verifierAuteur(Commentaire commentaire) {
        String emailConnecte = emailUtilisateurConnecte();
        if (emailConnecte == null || !emailConnecte.equalsIgnoreCase(commentaire.getAuteurEmail())) {
            throw new AccessDeniedException("Vous ne pouvez modifier ou supprimer que vos propres commentaires.");
        }
    }

    private String emailUtilisateurConnecte() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    private String libelleRoleConnecte() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (possedeRole(authentication, "ROLE_ADMIN")) {
            return "Admin";
        }
        if (possedeRole(authentication, "ROLE_DIRECTION")) {
            return "Direction";
        }
        if (possedeRole(authentication, "ROLE_VIESCO")) {
            return "Vie Scolaire";
        }
        if (possedeRole(authentication, "ROLE_COMPTA")) {
            return "Comptabilité";
        }
        if (possedeRole(authentication, "ROLE_PROF")) {
            return "Professeur";
        }
        return null;
    }

    private boolean possedeRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
