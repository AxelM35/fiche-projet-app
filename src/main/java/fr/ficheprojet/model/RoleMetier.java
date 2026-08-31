package fr.ficheprojet.model;

/**
 * Rôles métier attribuables dynamiquement par un Admin (voir RoleAttribution).
 * ROLE_PROF n'y figure pas : c'est le rôle de base, attribué automatiquement
 * à tout utilisateur du domaine autorisé qui n'est pas en lecture seule
 * (CustomOAuth2UserService), jamais une attribution explicite.
 */
public enum RoleMetier {
    COMPTA,
    VIESCO,
    DIRECTION,
    ADMIN,
    LECTURE_SEULE
}
