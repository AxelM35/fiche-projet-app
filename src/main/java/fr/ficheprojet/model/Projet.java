package fr.ficheprojet.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fiche projet d'un voyage scolaire. Le champ "version" implémente le
 * verrouillage optimiste JPA : toute mise à jour concurrente sur une version
 * périmée lève une ObjectOptimisticLockingFailureException, gérée par
 * ProjetController pour éviter qu'une validation n'écrasé silencieusement
 * une autre.
 */
@Entity
@Table(name = "projets")
@Getter
@Setter
@NoArgsConstructor
public class Projet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    // --- Identité ---
    @Column(nullable = false)
    private String nomProjet;

    @Column(columnDefinition = "TEXT")
    private String description;

    // --- Dates et lieux ---
    private LocalDateTime dateDepart;
    private LocalDateTime dateRetour;
    private String lieuDepart;
    private String lieuRetour;
    private String transport;

    // --- Organisme ou référent externe (optionnel) ---
    private String organismeNom;
    private String organismeTelephone;
    private String organismeEmail;

    // --- Organisateur ---
    private String organisateurNom;
    private String organisateurEmail;
    private String telephoneOrganisateur;

    // --- Groupe ---
    private String classesConcernees;
    private Integer effectif;

    @ElementCollection
    @CollectionTable(name = "projet_accompagnateurs", joinColumns = @JoinColumn(name = "projet_id"))
    @Column(name = "nom_accompagnateur")
    private List<String> accompagnateurs = new ArrayList<>();

    // --- Budget ---
    @Column(precision = 10, scale = 2)
    private BigDecimal coutGlobal;

    @Column(precision = 10, scale = 2)
    private BigDecimal coutParEleve;

    @Column(precision = 10, scale = 2)
    private BigDecimal montantSubvention;

    private Boolean eligiblePassCulture = Boolean.FALSE;

    // --- Commentaire libre ---
    @Column(columnDefinition = "TEXT")
    private String commentaire;

    // --- Pièces jointes (MVP) : simple lien vers un dossier Google Drive
    // géré en dehors de l'application (créé/partagé à la main), pas
    // d'intégration API Drive pour l'instant. Modifiable indépendamment du
    // reste du formulaire (voir ProjetService.modifierLienDrive), y compris
    // par les rôles de validation pendant l'instruction du dossier.
    @Column(name = "lien_drive", length = 500)
    private String lienDrive;

    // --- Workflow (audit) ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatutProjet statut = StatutProjet.BROUILLON;

    @Column(columnDefinition = "TEXT")
    private String motifRefus;

    private LocalDateTime dateValidationProf;
    private LocalDateTime dateValidationCompta;
    private LocalDateTime dateValidationVieScolaire;
    private LocalDateTime dateValidationDirection;

    // --- Archivage (Admin) : retire le dossier du tableau de bord sans le
    // supprimer, indépendamment de son statut de workflow. Réversible
    // (desarchiver), contrairement à une suppression définitive.
    @Column(nullable = false)
    private boolean archive = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Projet autre)) {
            return false;
        }
        return id != null && id.equals(autre.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
