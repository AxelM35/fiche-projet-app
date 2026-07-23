package fr.collegesthelier.voyages.model;

/**
 * Etapes du workflow lineaire de validation d'un projet de voyage scolaire :
 * BROUILLON -> EN_ATTENTE_COMPTA -> EN_ATTENTE_VIE_SCOLAIRE
 *           -> EN_ATTENTE_DIRECTION -> VALIDE
 * A_CORRIGER est un aiguillage de retour vers le professeur en cas de refus,
 * quelle que soit l'etape de blocage.
 */
public enum StatutProjet {
    BROUILLON,
    EN_ATTENTE_COMPTA,
    EN_ATTENTE_VIE_SCOLAIRE,
    EN_ATTENTE_DIRECTION,
    VALIDE,
    A_CORRIGER
}
