package fr.collegesthelier.voyages.service;

import fr.collegesthelier.voyages.dto.ProjetFormDTO;
import fr.collegesthelier.voyages.exception.TransitionInvalideException;
import fr.collegesthelier.voyages.model.Projet;
import fr.collegesthelier.voyages.model.StatutProjet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifie le workflow lineaire complet ainsi que les deux garde-fous de
 * concurrence/securite : verrouillage optimiste et controle de propriete
 * d'un dossier. Le contexte de securite est bascule manuellement d'un role
 * a l'autre au sein d'un meme test (@WithMockUser ne s'applique qu'au
 * demarrage d'une methode @Test, pas a des appels internes).
 */
@SpringBootTest
@ActiveProfiles("test")
class ProjetServiceTest {

    @Autowired
    private ProjetService projetService;

    private ProjetFormDTO dtoValide() {
        ProjetFormDTO dto = new ProjetFormDTO();
        dto.setNomProjet("Voyage a Rome");
        dto.setDescription("Voyage culturel");
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

    private void connecterEnTantQue(String email, String role) {
        Authentication authentication = new TestingAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void nettoyerContexteSecurite() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leWorkflowLineaireCompletFaitProgresserLeStatutEtHorodateChaqueEtape() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Projet projet = projetService.creerProjet(dtoValide());
        assertThat(projet.getStatut()).isEqualTo(StatutProjet.BROUILLON);

        projetService.soumettre(projet.getId());
        Projet apresSoumission = projetService.trouverParId(projet.getId());
        assertThat(apresSoumission.getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_COMPTA);
        assertThat(apresSoumission.getDateValidationProf()).isNotNull();
    }

    @Test
    void laValidationCompletePasseParTousLesStatutsJusquaValide() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);
        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_VIE_SCOLAIRE);

        connecterEnTantQue("viesco@college-sthelier.fr", "ROLE_VIESCO");
        projetService.validerVieScolaire(id);
        assertThat(projetService.trouverParId(id).getStatut()).isEqualTo(StatutProjet.EN_ATTENTE_DIRECTION);

        connecterEnTantQue("direction@college-sthelier.fr", "ROLE_DIRECTION");
        projetService.validerDirection(id);
        Projet valide = projetService.trouverParId(id);
        assertThat(valide.getStatut()).isEqualTo(StatutProjet.VALIDE);
        assertThat(valide.getDateValidationCompta()).isNotNull();
        assertThat(valide.getDateValidationVieScolaire()).isNotNull();
        assertThat(valide.getDateValidationDirection()).isNotNull();
    }

    @Test
    void unRefusEffaceLesDatesDeValidationEtRepasseEnACorriger() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        projetService.soumettre(id);

        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        projetService.validerCompta(id);
        projetService.refuser(id, "Budget trop eleve, merci de revoir le devis.");

        Projet refuse = projetService.trouverParId(id);
        assertThat(refuse.getStatut()).isEqualTo(StatutProjet.A_CORRIGER);
        assertThat(refuse.getMotifRefus()).isEqualTo("Budget trop eleve, merci de revoir le devis.");
        assertThat(refuse.getDateValidationProf()).isNull();
        assertThat(refuse.getDateValidationCompta()).isNull();
    }

    @Test
    void uneTransitionHorsSequenceEstRejetee() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();

        // Le projet est encore en BROUILLON : la validation comptable directe
        // (par un utilisateur qui a pourtant bien le role COMPTA) doit echouer
        // sur la regle metier, pas sur les droits d'acces.
        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        assertThatThrownBy(() -> projetService.validerCompta(id)).isInstanceOf(TransitionInvalideException.class);
    }

    @Test
    void unProfNePeutPasModifierLeDossierDunCollegue() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Long id = projetService.creerProjet(dtoValide()).getId();
        Projet projet = projetService.trouverParId(id);

        connecterEnTantQue("autre.prof@college-sthelier.fr", "ROLE_PROF");
        ProjetFormDTO dto = dtoValide();
        dto.setVersion(projet.getVersion());
        assertThatThrownBy(() -> projetService.modifierProjet(id, dto))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void uneModificationAvecUneVersionPerimeeEstRejetee() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        Projet projet = projetService.creerProjet(dtoValide());
        Long id = projet.getId();

        // Le professeur ouvre le formulaire (version courante), puis une
        // premiere modification aboutit et fait progresser la version...
        ProjetFormDTO premiereModification = dtoValide();
        premiereModification.setVersion(projet.getVersion());
        premiereModification.setNomProjet("Voyage a Rome (premiere modification)");
        projetService.modifierProjet(id, premiereModification);

        // ...puis une seconde soumission, restee sur l'ancienne version
        // (onglet du navigateur reste ouvert sur le formulaire d'origine),
        // doit etre rejetee au lieu d'ecraser silencieusement le premier
        // changement.
        ProjetFormDTO secondeModificationPerimee = dtoValide();
        secondeModificationPerimee.setVersion(projet.getVersion());
        secondeModificationPerimee.setNomProjet("Voyage a Rome (ecrasement non souhaite)");

        assertThatThrownBy(() -> projetService.modifierProjet(id, secondeModificationPerimee))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
