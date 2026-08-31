package fr.ficheprojet.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Statistiques consolidées pour le dashboard Admin (/admin/statistiques),
 * calculées uniquement sur les dossiers actifs (hors archivés).
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
