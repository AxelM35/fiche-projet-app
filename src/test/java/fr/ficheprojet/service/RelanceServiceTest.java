package fr.ficheprojet.service;

import fr.ficheprojet.dto.ProjetFormDTO;
import fr.ficheprojet.model.JournalEntree;
import fr.ficheprojet.model.Projet;
import fr.ficheprojet.repository.JournalEntreeRepository;
import fr.ficheprojet.repository.ProjetRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Relances automatiques (RelanceService) : le seuil et la période par
 * défaut sont 7 jours (voir application-test.properties non surchargé,
 * RelanceProperties). "En attente depuis" est simulé en ré-écrivant
 * directement dateValidationProf via le repository (aucune API publique ne
 * permet d'antidater une soumission).
 */
@SpringBootTest
@ActiveProfiles("test")
class RelanceServiceTest {

    @Autowired
    private RelanceService relanceService;

    @Autowired
    private ProjetService projetService;

    @Autowired
    private ProjetRepository projetRepository;

    @Autowired
    private JournalEntreeRepository journalEntreeRepository;

    private void connecterEnTantQue(String email, String role) {
        Authentication authentication = new TestingAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void nettoyerContexteSecurite() {
        SecurityContextHolder.clearContext();
    }

    private Long creerDossierEnAttenteComptaDepuis(long jours) {
        connecterEnTantQue("prof@exemple.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoBase()).getId();
        projetService.soumettre(id);

        Projet projet = projetRepository.findById(id).orElseThrow();
        projet.setDateValidationProf(LocalDateTime.now().minusDays(jours));
        projetRepository.save(projet);
        return id;
    }

    private boolean relanceJournaliseePour(Long projetId) {
        return journalEntreeRepository.findTop200ByOrderByDateEvenementDesc().stream()
                .anyMatch(entree -> "Relance".equals(entree.getAction()) && projetId.equals(entree.getProjetId()));
    }

    @Test
    void relanceLeDossierBloqueDepuisPlusDuSeuil() {
        Long id = creerDossierEnAttenteComptaDepuis(10);

        relanceService.relancerDossiersBloques();

        assertThat(relanceJournaliseePour(id)).isTrue();
    }

    @Test
    void neRelancePasAvantLeSeuil() {
        Long id = creerDossierEnAttenteComptaDepuis(2);

        relanceService.relancerDossiersBloques();

        assertThat(relanceJournaliseePour(id)).isFalse();
    }

    @Test
    void neRelancePasDeuxFoisAvantLaPeriodeDeRepetition() {
        Long id = creerDossierEnAttenteComptaDepuis(10);

        relanceService.relancerDossiersBloques();
        relanceService.relancerDossiersBloques();

        long nombreDeRelances = journalEntreeRepository.findTop200ByOrderByDateEvenementDesc().stream()
                .filter(entree -> "Relance".equals(entree.getAction()) && id.equals(entree.getProjetId()))
                .count();
        assertThat(nombreDeRelances).isEqualTo(1);
    }

    @Test
    void relanceDeNouveauApresLaPeriodeDeRepetition() {
        Long id = creerDossierEnAttenteComptaDepuis(20);
        relanceService.relancerDossiersBloques();
        assertThat(relanceJournaliseePour(id)).isTrue();

        // Antidate la relance qui vient d'être journalisée pour simuler
        // qu'elle a été envoyée il y a plus longtemps que la période de
        // répétition (7 jours par défaut).
        JournalEntree derniereRelance = journalEntreeRepository.findTop200ByOrderByDateEvenementDesc().stream()
                .filter(entree -> "Relance".equals(entree.getAction()) && id.equals(entree.getProjetId()))
                .findFirst().orElseThrow();
        derniereRelance.setDateEvenement(LocalDateTime.now().minusDays(9));
        journalEntreeRepository.save(derniereRelance);

        relanceService.relancerDossiersBloques();

        long nombreDeRelances = journalEntreeRepository.findTop200ByOrderByDateEvenementDesc().stream()
                .filter(entree -> "Relance".equals(entree.getAction()) && id.equals(entree.getProjetId()))
                .count();
        assertThat(nombreDeRelances).isEqualTo(2);
    }

    private ProjetFormDTO dtoBase() {
        ProjetFormDTO dto = new ProjetFormDTO();
        dto.setNomProjet("Voyage a Rome");
        dto.setDateDepart(LocalDateTime.now().plusMonths(1));
        dto.setDateRetour(LocalDateTime.now().plusMonths(1).plusDays(3));
        dto.setLieuDepart("College");
        dto.setLieuRetour("College");
        dto.setTransport("Car");
        dto.setOrganisateurNom("M. Prof");
        dto.setOrganisateurEmail("prof@exemple.fr");
        dto.setTelephoneOrganisateur("0102030405");
        dto.setClassesConcernees("6A");
        dto.setEffectif(28);
        dto.setCoutGlobal(new BigDecimal("1500"));
        dto.setCoutParEleve(new BigDecimal("50"));
        dto.setMontantSubvention(BigDecimal.ZERO);
        return dto;
    }
}
