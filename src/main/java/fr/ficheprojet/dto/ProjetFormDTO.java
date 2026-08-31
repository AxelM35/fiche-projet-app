package fr.ficheprojet.dto;

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
 * Objet de transfert utilisé par le formulaire web. Le contrôleur ne
 * manipule jamais directement l'entité Projet (protection contre le Mass
 * Assignment) : le mapping DTO <-> Entité est effectué dans ProjetService.
 */
@Getter
@Setter
public class ProjetFormDTO {

    /** Null lors de la création, renseigné lors d'une modification. */
    private Long id;

    /** Utilisé par la vue pour l'optimistic locking (vérifié côté service). */
    private Long version;

    // --- Identité ---
    @NotBlank(message = "Le nom du projet est obligatoire.")
    @Size(max = 255, message = "Le nom du projet ne doit pas dépasser 255 caractères.")
    private String nomProjet;

    @Size(max = 5000, message = "La description ne doit pas dépasser 5000 caractères.")
    private String description;

    // --- Dates et lieux ---
    @NotNull(message = "La date de départ est obligatoire.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime dateDepart;

    @NotNull(message = "La date de retour est obligatoire.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime dateRetour;

    @NotBlank(message = "Le lieu de départ est obligatoire.")
    private String lieuDepart;

    @NotBlank(message = "Le lieu de retour est obligatoire.")
    private String lieuRetour;

    @NotBlank(message = "Le moyen de transport est obligatoire.")
    private String transport;

    // --- Organisme ou référent externe (facultatif) ---
    @Size(max = 255, message = "Le nom de l'organisme ne doit pas dépasser 255 caractères.")
    private String organismeNom;

    @Pattern(regexp = "^$|^[0-9+ .-]{6,20}$", message = "Le format du téléphone est invalide.")
    private String organismeTelephone;

    @Email(message = "L'email de l'organisme doit être une adresse valide.")
    @Size(max = 255, message = "L'email de l'organisme ne doit pas dépasser 255 caractères.")
    private String organismeEmail;

    // --- Organisateur ---
    @NotBlank(message = "Le nom de l'organisateur est obligatoire.")
    private String organisateurNom;

    @NotBlank(message = "L'email de l'organisateur est obligatoire.")
    @Email(message = "L'email de l'organisateur doit être une adresse valide.")
    private String organisateurEmail;

    // Pattern tolérant au vide (comme organismeTelephone) bien que le champ
    // soit obligatoire : @NotBlank porte déjà le message "obligatoire" pour
    // un champ vide, la regex ne doit alors pas en ajouter un second sur le
    // format en même temps (redondant, voir audit UX S4bis).
    @NotBlank(message = "Le téléphone de l'organisateur est obligatoire.")
    @Pattern(regexp = "^$|^[0-9+ .-]{6,20}$", message = "Le format du téléphone est invalide.")
    private String telephoneOrganisateur;

    // --- Groupe ---
    @NotBlank(message = "Les classes concernées sont obligatoires.")
    private String classesConcernees;

    @NotNull(message = "L'effectif est obligatoire.")
    @Positive(message = "L'effectif doit être supérieur à zéro.")
    private Integer effectif;

    /**
     * Lié directement depuis plusieurs champs &lt;input name="accompagnateurs"&gt;
     * du formulaire : le data binder Spring MVC assemble automatiquement une
     * List&lt;String&gt; à partir de paramètres de requête répétés.
     */
    private List<String> accompagnateurs = new ArrayList<>();

    // --- Budget ---
    // coutGlobal/coutParEleve obligatoires sauf si budgetInconnu est coché :
    // pas de @NotNull ici, vérifié de façon conditionnelle par
    // ProjetController.validerCoherenceBudget (équivalent à
    // validerCoherenceDates, même fichier).
    /**
     * Case "Je ne connais pas encore le budget" du formulaire : permet de
     * laisser coutGlobal/coutParEleve vides (l'organisateur planifie souvent
     * un projet avant d'avoir les chiffres). Transitoire, jamais persisté :
     * après enregistrement, coutGlobal == null porte à lui seul exactement
     * la même information (voir ProjetService.versDTO/completerBudget).
     */
    private boolean budgetInconnu;

    @PositiveOrZero(message = "Le coût global doit être positif ou nul.")
    private BigDecimal coutGlobal;

    @PositiveOrZero(message = "Le coût par élève doit être positif ou nul.")
    private BigDecimal coutParEleve;

    // Pas de valeur par défaut (contrairement aux autres montants, déjà
    // nuls tant que non saisis) : un "0" pré-rempli silencieusement se
    // distinguait mal d'une subvention réellement décidée à 0€ (voir audit
    // UX S4bis). La colonne montant_subvention est nullable en base
    // (V1__init.sql), aucune migration nécessaire.
    @PositiveOrZero(message = "Le montant de la subvention doit être positif ou nul.")
    private BigDecimal montantSubvention;

    private boolean eligiblePassCulture;

    // --- Commentaire libre (facultatif) ---
    @Size(max = 5000, message = "Le commentaire ne doit pas dépasser 5000 caractères.")
    private String commentaire;

    /**
     * Affichage seulement : modifié via LienDriveFormDTO/modifierLienDrive,
     * jamais reecrit par la soumission du formulaire principal (voir
     * ProjetService.copierDtoVersEntite, qui ne le touche pas).
     */
    private String lienDrive;
}
