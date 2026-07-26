package fr.collegesthelier.voyages.service;

import fr.collegesthelier.voyages.dto.ProjetFormDTO;
import fr.collegesthelier.voyages.model.JournalEntree;
import fr.collegesthelier.voyages.model.Projet;
import fr.collegesthelier.voyages.repository.JournalEntreeRepository;
import fr.collegesthelier.voyages.repository.ProjetRepository;
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
 * Relances automatiques (RelanceService) : le seuil et la periode par
 * defaut sont 7 jours (voir application-test.properties non surcharge,
 * RelanceProperties). "En attente depuis" est simule en re-ecrivant
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
        connecterEnTantQue("prof@college-sthelier.fr", "ROLE_PROF");
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

        // Antidate la relance qui vient d'etre journalisee pour simuler
        // qu'elle a ete envoyee il y a plus longtemps que la periode de
        // repetition (7 jours par defaut).
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
        dto.setOrganisateurEmail("prof@college-sthelier.fr");
        dto.setTelephoneOrganisateur("0102030405");
        dto.setClassesConcernees("6A");
        dto.setEffectif(28);
        dto.setCoutGlobal(new BigDecimal("1500"));
        dto.setCoutParEleve(new BigDecimal("50"));
        dto.setMontantSubvention(BigDecimal.ZERO);
        return dto;
    }
}
