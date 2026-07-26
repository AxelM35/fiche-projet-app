package fr.collegesthelier.ficheprojet.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AnneeScolaireUtilTest {

    @Test
    void uneDateEnSeptembreDemarreLaNouvelleAnneeScolaire() {
        assertThat(AnneeScolaireUtil.calculer(LocalDateTime.of(2025, 9, 1, 0, 0))).isEqualTo("2025-2026");
    }

    @Test
    void uneDateEnAoutAppartientEncoreALanneeScolairePrecedente() {
        assertThat(AnneeScolaireUtil.calculer(LocalDateTime.of(2026, 8, 31, 23, 59))).isEqualTo("2025-2026");
    }

    @Test
    void uneDateEnJanvierAppartientALanneeScolaireEnCours() {
        assertThat(AnneeScolaireUtil.calculer(LocalDateTime.of(2026, 1, 15, 12, 0))).isEqualTo("2025-2026");
    }

    @Test
    void uneDateNulleNeCalculeAucuneAnnee() {
        assertThat(AnneeScolaireUtil.calculer(null)).isNull();
    }
}
