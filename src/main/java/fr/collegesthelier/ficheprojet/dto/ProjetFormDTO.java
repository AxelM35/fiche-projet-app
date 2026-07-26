package fr.collegesthelier.ficheprojet.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Objet de transfert utilise par le formulaire web. Le controleur ne
 * manipule jamais directement l'entite Projet (protection contre le Mass
 * Assignment) : le mapping DTO <-> Entite est effectue dans ProjetService.
 */
@Getter
@Setter
public class ProjetFormDTO {

    /** Null lors de la creation, renseigne lors d'une modification. */
    private Long id;

    /** Utilise par la vue pour l'optimistic locking (verifie cote service). */
    private Long version;

    // --- Identite ---
    @NotBlank(message = "Le nom du projet est obligatoire.")
    @Size(max = 255, message = "Le nom du projet ne doit pas depasser 255 caracteres.")
    private String nomProjet;

    @Size(max = 5000, message = "La description ne doit pas depasser 5000 caracteres.")
    private String description;

    // --- Dates et lieux ---
    @NotNull(message = "La date de depart est obligatoire.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime dateDepart;

    @NotNull(message = "La date de retour est obligatoire.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime dateRetour;

    @NotBlank(message = "Le lieu de depart est obligatoire.")
    private String lieuDepart;

    @NotBlank(message = "Le lieu de retour est obligatoire.")
    private String lieuRetour;

    @NotBlank(message = "Le moyen de transport est obligatoire.")
    private String transport;

    // --- Organisme ou referent externe (facultatif) ---
    @Size(max = 255, message = "Le nom de l'organisme ne doit pas depasser 255 caracteres.")
    private String organismeNom;

    @Pattern(regexp = "^$|^[0-9+ .-]{6,20}$", message = "Le format du telephone est invalide.")
    private String organismeTelephone;

    @Email(message = "L'email de l'organisme doit etre une adresse valide.")
    @Size(max = 255, message = "L'email de l'organisme ne doit pas depasser 255 caracteres.")
    private String organismeEmail;

    // --- Organisateur ---
    @NotBlank(message = "Le nom de l'organisateur est obligatoire.")
    private String organisateurNom;

    @NotBlank(message = "L'email de l'organisateur est obligatoire.")
    @Email(message = "L'email de l'organisateur doit etre une adresse valide.")
    private String organisateurEmail;

    @NotBlank(message = "Le telephone de l'organisateur est obligatoire.")
    @Pattern(regexp = "^[0-9+ .-]{6,20}$", message = "Le format du telephone est invalide.")
    private String telephoneOrganisateur;

    // --- Groupe ---
    @NotBlank(message = "Les classes concernees sont obligatoires.")
    private String classesConcernees;

    @NotNull(message = "L'effectif est obligatoire.")
    @Positive(message = "L'effectif doit etre superieur a zero.")
    private Integer effectif;

    /**
     * Lie directement depuis plusieurs champs &lt;input name="accompagnateurs"&gt;
     * du formulaire : le data binder Spring MVC assemble automatiquement une
     * List&lt;String&gt; a partir de parametres de requete repetes.
     */
    private List<String> accompagnateurs = new ArrayList<>();

    // --- Budget ---
    @NotNull(message = "Le cout global est obligatoire.")
    @PositiveOrZero(message = "Le cout global doit etre positif ou nul.")
    private BigDecimal coutGlobal;

    @NotNull(message = "Le cout par eleve est obligatoire.")
    @PositiveOrZero(message = "Le cout par eleve doit etre positif ou nul.")
    private BigDecimal coutParEleve;

    @PositiveOrZero(message = "Le montant de la subvention doit etre positif ou nul.")
    private BigDecimal montantSubvention = BigDecimal.ZERO;

    private boolean eligiblePassCulture;

    // --- Commentaire libre (facultatif) ---
    @Size(max = 5000, message = "Le commentaire ne doit pas depasser 5000 caracteres.")
    private String commentaire;

    /**
     * Affichage seulement : modifie via LienDriveFormDTO/modifierLienDrive,
     * jamais reecrit par la soumission du formulaire principal (voir
     * ProjetService.copierDtoVersEntite, qui ne le touche pas).
     */
    private String lienDrive;
}
