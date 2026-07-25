package fr.collegesthelier.voyages.dto;

import java.math.BigDecimal;

/**
 * Chiffres cles affiches en haut du tableau de bord Kanban (tuiles de
 * statistiques). Pur objet d'affichage : jamais lie a un formulaire, donc
 * pas de validation Jakarta necessaire ici.
 */
public record TableauDeBordStatsDTO(
        long totalProjets,
        long projetsValides,
        long projetsEnAttenteDeValidation,
        BigDecimal budgetTotalEngage) {
}
