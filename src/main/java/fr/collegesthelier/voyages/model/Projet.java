package fr.collegesthelier.voyages.model;

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
 * Fiche projet d'un voyage scolaire. Le champ "version" implemente le
 * verrouillage optimiste JPA : toute mise a jour concurrente sur une version
 * perimee leve une ObjectOptimisticLockingFailureException, geree par
 * ProjetController pour eviter qu'une validation n'ecrase silencieusement
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

    // --- Identite ---
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

    // --- Organisme ou referent externe (optionnel) ---
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
    // supprimer, independamment de son statut de workflow. Reversible
    // (desarchiver), contrairement a une suppression definitive.
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
