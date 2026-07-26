package fr.collegesthelier.ficheprojet.dto;

import fr.collegesthelier.ficheprojet.model.StatutProjet;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Vue en lecture seule d'un projet (utilisee pour les dossiers VALIDE, et
 * pour n'importe quel statut consulte par un utilisateur ROLE_LECTURE_SEULE).
 * Contrairement a ProjetFormDTO, jamais lie a un formulaire entrant : il peut
 * donc exposer sans risque les champs d'audit du workflow (statut, dates de
 * validation, motif de refus) qui n'ont rien a faire dans un objet bindable
 * depuis une requete POST.
 */
public record ProjetConsultationDTO(
        Long id,
        String nomProjet,
        String description,
        LocalDateTime dateDepart,
        LocalDateTime dateRetour,
        String lieuDepart,
        String lieuRetour,
        String transport,
        String organismeNom,
        String organismeTelephone,
        String organismeEmail,
        String organisateurNom,
        String organisateurEmail,
        String telephoneOrganisateur,
        String classesConcernees,
        Integer effectif,
        List<String> accompagnateurs,
        BigDecimal coutGlobal,
        BigDecimal coutParEleve,
        BigDecimal montantSubvention,
        boolean eligiblePassCulture,
        String commentaire,
        String lienDrive,
        StatutProjet statut,
        String motifRefus,
        LocalDateTime dateValidationProf,
        LocalDateTime dateValidationCompta,
        LocalDateTime dateValidationVieScolaire,
        LocalDateTime dateValidationDirection) {
}
