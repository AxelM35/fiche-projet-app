package fr.ficheprojet.service;

import fr.ficheprojet.model.JournalEntree;
import fr.ficheprojet.repository.JournalEntreeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Journal d'audit du dashboard admin. Chaque entrée est écrite de manière
 * synchrone, dans la même transaction que l'action qu'elle documente (pas de
 * @TransactionalEventListener/@Async comme NotificationService) : l'auteur
 * vient du SecurityContextHolder du thread courant, qui ne serait plus
 * disponible dans un thread de pool asynchrone, et un rollback de l'action
 * doit aussi annuler l'entrée de journal correspondante.
 */
@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalEntreeRepository journalEntreeRepository;

    @Transactional
    public void enregistrer(String action, Long projetId, String projetNom, String detail) {
        enregistrer(action, projetId, projetNom, detail, emailUtilisateurConnecte());
    }

    /**
     * Variante pour les actions déclenchées hors d'une requête authentifiée
     * (ex. RelanceService, tâche planifiée) : SecurityContextHolder n'a
     * alors aucun utilisateur connecté à journaliser comme auteur.
     */
    @Transactional
    public void enregistrer(String action, Long projetId, String projetNom, String detail, String auteurEmail) {
        JournalEntree entree = new JournalEntree(auteurEmail, action, projetId, projetNom, detail);
        journalEntreeRepository.save(entree);
    }

    @Transactional(readOnly = true)
    public List<JournalEntree> listerRecentes() {
        return journalEntreeRepository.findTop200ByOrderByDateEvenementDesc();
    }

    private String emailUtilisateurConnecte() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }
}
