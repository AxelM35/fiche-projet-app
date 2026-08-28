package fr.ficheprojet.dto;

import java.math.BigDecimal;

/**
 * Chiffres clés affichés en haut du tableau de bord Kanban (tuiles de
 * statistiques). Pur objet d'affichage : jamais lié à un formulaire, donc
 * pas de validation Jakarta nécessaire ici.
 */
public record TableauDeBordStatsDTO(
        long totalProjets,
        long projetsValides,
        long projetsEnAttenteDeValidation,
        BigDecimal budgetTotalEngage) {
}
