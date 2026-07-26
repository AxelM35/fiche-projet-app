package fr.collegesthelier.ficheprojet.service;

import fr.collegesthelier.ficheprojet.dto.ProjetFormDTO;
import fr.collegesthelier.ficheprojet.model.Commentaire;
import fr.collegesthelier.ficheprojet.model.Projet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifie le fil de commentaires d'un dossier : autorisation d'ajout (meme
 * perimetre que la gestion du lien Drive), propriete du commentaire pour la
 * modification/suppression, et ordre chronologique du fil.
 */
@SpringBootTest
@ActiveProfiles("test")
class CommentaireServiceTest {

    @Autowired
    private ProjetService projetService;

    @Autowired
    private CommentaireService commentaireService;

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

    private Long creerProjet() {
        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        return projetService.creerProjet(dtoValide()).getId();
    }

    @Test
    void lorganisateurPeutAjouterUnCommentaireEtLeFilResteChronologique() {
        Long id = creerProjet();

        Commentaire premier = commentaireService.ajouter(id, "Premier message");

        connecterEnTantQue("direction@college-sthelier.fr", "ROLE_DIRECTION");
        Commentaire second = commentaireService.ajouter(id, "Second message");

        List<Commentaire> fil = commentaireService.lister(id);
        assertThat(fil).extracting(Commentaire::getId).containsExactly(premier.getId(), second.getId());
        assertThat(fil.get(1).getAuteurRole()).isEqualTo("Direction");
    }

    @Test
    void unProfSansLienAvecLeDossierNePeutPasCommenter() {
        Long id = creerProjet();

        connecterEnTantQue("autreprof@college-sthelier.fr", "ROLE_PROF");
        assertThatThrownBy(() -> commentaireService.ajouter(id, "Je n'ai rien a voir avec ce dossier"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unValideurPeutCommenterMemeSansEtreOrganisateur() {
        Long id = creerProjet();

        connecterEnTantQue("compta@college-sthelier.fr", "ROLE_COMPTA");
        Commentaire commentaire = commentaireService.ajouter(id, "Il manque une pièce budgétaire");

        assertThat(commentaire.getAuteurRole()).isEqualTo("Comptabilité");
        assertThat(commentaireService.lister(id)).extracting(Commentaire::getId).contains(commentaire.getId());
    }

    @Test
    void seulLauteurPeutModifierSonCommentaire() {
        Long id = creerProjet();
        Commentaire commentaire = commentaireService.ajouter(id, "Texte initial");

        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        assertThatThrownBy(() -> commentaireService.modifier(commentaire.getId(), "Modifié par un autre"))
                .isInstanceOf(AccessDeniedException.class);

        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        commentaireService.modifier(commentaire.getId(), "Texte corrigé");

        Commentaire misAJour = commentaireService.lister(id).get(0);
        assertThat(misAJour.getTexte()).isEqualTo("Texte corrigé");
        assertThat(misAJour.getDateModification()).isNotNull();
    }

    @Test
    void seulLauteurPeutSupprimerSonCommentaire() {
        Long id = creerProjet();
        Commentaire commentaire = commentaireService.ajouter(id, "A supprimer");

        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        assertThatThrownBy(() -> commentaireService.supprimer(commentaire.getId()))
                .isInstanceOf(AccessDeniedException.class);

        connecterEnTantQue("martin@college-sthelier.fr", "ROLE_PROF");
        commentaireService.supprimer(commentaire.getId());

        assertThat(commentaireService.lister(id)).isEmpty();
    }

    @Test
    void supprimerDefinitivementLeDossierSupprimeAussiSesCommentaires() {
        Long id = creerProjet();
        commentaireService.ajouter(id, "Un commentaire");

        connecterEnTantQue("amorvan@college-sthelier.fr", "ROLE_ADMIN");
        projetService.supprimerDefinitivement(id);

        assertThat(commentaireService.lister(id)).isEmpty();
    }
}
