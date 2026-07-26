package fr.collegesthelier.ficheprojet.service;

import fr.collegesthelier.ficheprojet.dto.ProjetFormDTO;
import fr.collegesthelier.ficheprojet.dto.StatistiquesDTO;
import fr.collegesthelier.ficheprojet.model.Projet;
import fr.collegesthelier.ficheprojet.repository.ProjetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Verifie les statistiques consolidees (/admin/statistiques), calculees sur
 * l'ensemble des dossiers actifs de la base H2 partagee entre tests : les
 * assertions sur le taux de refus et le delai moyen comparent donc un
 * "avant/apres" (delta) plutot que des valeurs absolues, pour rester
 * fiables quel que soit ce que les autres tests ont deja cree. Le budget
 * par annee/classe utilise a l'inverse une annee et une classe tres
 * improbables ailleurs, pour pouvoir verifier une valeur exacte.
 */
@SpringBootTest
@ActiveProfiles("test")
class StatistiquesServiceTest {

    @Autowired
    private ProjetService projetService;

    @Autowired
    private ProjetRepository projetRepository;

    @Autowired
    private StatistiquesService statistiquesService;

    private ProjetFormDTO dtoValide() {
        ProjetFormDTO dto = new ProjetFormDTO();
        dto.setNomProjet("Voyage a Rome");
        dto.setDateDepart(LocalDateTime.now().plusMonths(2));
        dto.setDateRetour(LocalDateTime.now().plusMonths(2).plusDays(4));
        dto.setLieuDepart("College");
        dto.setLieuRetour("College");
        dto.setTransport("Avion");
        dto.setOrganisateurNom("Mme Martin");
        dto.setOrganisateurEmail("martin@college-sthelier.fr");
        dto.setTelephoneOrganisateur("0102030405");
        dto.setClassesConcernees("4A");
        dto.setEffectif(25);
        dto.setCoutGlobal(new BigDecimal("2500"));
        dto.setCoutParEleve(new BigDecimal("100"));
        dto.setMontantSubvention(BigDecimal.ZERO);
        return dto;
    }

    private void connecterEnTantQue(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        Authentication authentication = new TestingAuthenticationToken(email, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void nettoyerContexteSecurite() {
        SecurityContextHolder.clearContext();
    }

    private Long validerCompletement(ProjetFormDTO dto) {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dto).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);

        connecterEnTantQue("viesco@college-sthelier.fr", "ROLE_VIESCO");
        projetService.validerVieScolaire(id);

        connecterEnTantQue("direction@college-sthelier.fr", "ROLE_DIRECTION");
        projetService.validerDirection(id);
        return id;
    }

    private Optional<StatistiquesDTO.Repartition> trouver(List<StatistiquesDTO.Repartition> repartitions, String libelle) {
        return repartitions.stream().filter(r -> r.libelle().equals(libelle)).findFirst();
    }

    private StatistiquesDTO.TauxRefusParEtape tauxPourEtape(StatistiquesDTO stats, String etape) {
        return stats.tauxDeRefusParEtape().stream().filter(t -> t.etape().equals(etape)).findFirst().orElseThrow();
    }

    private StatistiquesDTO.DelaiParEtape delaiPourEtape(StatistiquesDTO stats, String etape) {
        return stats.delaiMoyenDeTraitementParEtape().stream().filter(d -> d.etape().equals(etape)).findFirst().orElseThrow();
    }

    @Test
    void leBudgetEstRegroupeParAnneeScolaireEtParClasseEtNeCompteQueLesDossiersValides() {
        ProjetFormDTO dto = dtoValide();
        dto.setClassesConcernees("ZZ-TEST-STATS");
        dto.setDateDepart(LocalDateTime.of(2099, 10, 10, 8, 0));
        dto.setDateRetour(LocalDateTime.of(2099, 10, 14, 18, 0));
        dto.setCoutGlobal(new BigDecimal("3000"));
        Long idValide = validerCompletement(dto);

        // Un brouillon avec la meme classe ne doit pas etre compte.
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        ProjetFormDTO brouillon = dtoValide();
        brouillon.setClassesConcernees("ZZ-TEST-STATS");
        brouillon.setDateDepart(LocalDateTime.of(2099, 10, 10, 8, 0));
        brouillon.setCoutGlobal(new BigDecimal("9999"));
        projetService.creerProjet(brouillon);

        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        StatistiquesDTO stats = statistiquesService.calculer();

        assertThat(trouver(stats.budgetParAnneeScolaire(), "2099-2100"))
                .hasValueSatisfying(r -> assertThat(r.montant()).isEqualByComparingTo("3000"));
        assertThat(trouver(stats.budgetParClasse(), "ZZ-TEST-STATS"))
                .hasValueSatisfying(r -> assertThat(r.montant()).isEqualByComparingTo("3000"));

        // Une fois archive, le dossier valide sort des statistiques.
        projetService.archiver(idValide);
        StatistiquesDTO statsApresArchivage = statistiquesService.calculer();
        assertThat(trouver(statsApresArchivage.budgetParAnneeScolaire(), "2099-2100")).isEmpty();
        assertThat(trouver(statsApresArchivage.budgetParClasse(), "ZZ-TEST-STATS")).isEmpty();
    }

    @Test
    void leTauxDeRefusParEtapeCompteLesValidationsEtLesRefusDeCetteEtape() {
        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        StatistiquesDTO avant = statistiquesService.calculer();
        StatistiquesDTO.TauxRefusParEtape baseline = tauxPourEtape(avant, "Comptabilité");

        // Un dossier valide par la Comptabilite...
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long idValide = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(idValide);
        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.validerCompta(idValide);

        // ...et un dossier refuse par la Comptabilite.
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long idRefuse = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(idRefuse);
        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.refuser(idRefuse, "Devis manquant.");

        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        StatistiquesDTO.TauxRefusParEtape apres = tauxPourEtape(statistiquesService.calculer(), "Comptabilité");

        assertThat(apres.nombreValidations()).isEqualTo(baseline.nombreValidations() + 1);
        assertThat(apres.nombreRefus()).isEqualTo(baseline.nombreRefus() + 1);
        double tauxAttendu = 100.0 * apres.nombreRefus() / (apres.nombreValidations() + apres.nombreRefus());
        assertThat(apres.tauxRefusPourcent()).isEqualTo(tauxAttendu, within(0.001));

        // Le refus d'un dossier archive ne doit plus compter dans les stats.
        projetService.archiver(idRefuse);
        StatistiquesDTO.TauxRefusParEtape apresArchivage = tauxPourEtape(statistiquesService.calculer(), "Comptabilité");
        assertThat(apresArchivage.nombreRefus()).isEqualTo(baseline.nombreRefus());
    }

    @Test
    void leDelaiMoyenDeTraitementEstCalculeADepuisLesDatesDeValidation() {
        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        StatistiquesDTO avant = statistiquesService.calculer();
        StatistiquesDTO.DelaiParEtape baseline = delaiPourEtape(avant, "Comptabilité");

        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        // Recule artificiellement la date de soumission de 4 jours pour
        // obtenir un delai de traitement mesurable et precis, sans dependre
        // du temps reel ecoule pendant le test.
        Projet projet = projetRepository.findById(id).orElseThrow();
        LocalDateTime dateSoumissionReculee = projet.getDateValidationProf().minusDays(4);
        projet.setDateValidationProf(dateSoumissionReculee);
        projetRepository.save(projet);

        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);

        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        StatistiquesDTO.DelaiParEtape apres = delaiPourEtape(statistiquesService.calculer(), "Comptabilité");

        assertThat(apres.nombreDossiersMesures()).isEqualTo(baseline.nombreDossiersMesures() + 1);
        double moyenneAttendue = (baseline.delaiMoyenJours() * baseline.nombreDossiersMesures() + 4.0) / apres.nombreDossiersMesures();
        assertThat(apres.delaiMoyenJours()).isEqualTo(moyenneAttendue, within(0.01));
    }
}
