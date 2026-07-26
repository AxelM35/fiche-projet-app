package fr.collegesthelier.ficheprojet.service;

import java.time.LocalDateTime;

/**
 * Calcule l'annee scolaire (ex. "2025-2026") a laquelle rattacher un projet,
 * a partir de sa date de depart : pas de nouveau champ sur Projet, l'annee
 * scolaire va de septembre (mois >= 9) a aout inclus de l'annee suivante.
 */
public final class AnneeScolaireUtil {

    private AnneeScolaireUtil() {
    }

    public static String calculer(LocalDateTime dateDepart) {
        if (dateDepart == null) {
            return null;
        }
        int anneeDebut = dateDepart.getMonthValue() >= 9 ? dateDepart.getYear() : dateDepart.getYear() - 1;
        return anneeDebut + "-" + (anneeDebut + 1);
    }
}
