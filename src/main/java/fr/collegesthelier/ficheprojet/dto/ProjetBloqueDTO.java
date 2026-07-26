package fr.collegesthelier.ficheprojet.dto;

import fr.collegesthelier.ficheprojet.model.StatutProjet;

import java.time.LocalDateTime;

/**
 * Ligne de la vue admin "Dossiers bloques" : un projet en attente de
 * validation depuis un certain temps, pour permettre une relance manuelle
 * en attendant d'eventuelles relances automatiques.
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
