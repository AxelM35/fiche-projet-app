package fr.ficheprojet.service;

import java.time.LocalDateTime;

/**
 * Calcule l'année scolaire (ex. "2025-2026") à laquelle rattacher un projet,
 * à partir de sa date de départ : pas de nouveau champ sur Projet, l'année
 * scolaire va de septembre (mois >= 9) à août inclus de l'année suivante.
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
