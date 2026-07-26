package fr.collegesthelier.voyages.model;

/**
 * Roles metier attribuables dynamiquement par un Admin (voir RoleAttribution).
 * ROLE_PROF n'y figure pas : c'est le role de base, attribue automatiquement
 * a tout utilisateur du domaine autorise qui n'est pas en lecture seule
 * (CustomOAuth2UserService), jamais une attribution explicite.
 */
public enum RoleMetier {
    COMPTA,
    VIESCO,
    DIRECTION,
    ADMIN,
    LECTURE_SEULE
}
