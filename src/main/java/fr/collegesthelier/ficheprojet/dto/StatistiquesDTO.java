package fr.collegesthelier.ficheprojet.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Statistiques consolidees pour le dashboard Admin (/admin/statistiques),
 * calculees uniquement sur les dossiers actifs (hors archives).
 */
public record StatistiquesDTO(
        List<Repartition> budgetParAnneeScolaire,
        List<Repartition> budgetParClasse,
        List<TauxRefusParEtape> tauxDeRefusParEtape,
        List<DelaiParEtape> delaiMoyenDeTraitementParEtape) {

    public record Repartition(String libelle, BigDecimal montant) {
    }

    public record TauxRefusParEtape(String etape, long nombreValidations, long nombreRefus, double tauxRefusPourcent) {
    }

    public record DelaiParEtape(String etape, long nombreDossiersMesures, double delaiMoyenJours) {
    }
}
