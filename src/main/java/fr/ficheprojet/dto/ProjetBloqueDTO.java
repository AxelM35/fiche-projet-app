package fr.ficheprojet.dto;

import fr.ficheprojet.model.StatutProjet;

import java.time.LocalDateTime;

/**
 * Ligne de la vue admin "Dossiers bloqués" : un projet en attente de
 * validation depuis un certain temps, pour permettre une relance manuelle
 * en attendant d'éventuelles relances automatiques.
 */
public record ProjetBloqueDTO(
        Long id,
        String nomProjet,
        StatutProjet statut,
        LocalDateTime enAttenteDepuis,
        long joursEnAttente,
        String organisateurNom,
        String organisateurEmail) {
}
