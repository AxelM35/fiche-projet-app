package fr.ficheprojet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Attribution d'un rôle métier à un email, gérée dynamiquement par un Admin
 * depuis le dashboard admin. S'ajoute (sans jamais les remplacer) aux listes
 * d'emails configurées en variables d'environnement (RolesProperties) :
 * CustomOAuth2UserService vérifie l'union des deux sources pour chaque rôle,
 * de sorte qu'une erreur de manipulation ici ne puisse jamais retirer
 * l'accès attribué via .env.
 */
@Entity
@Table(name = "role_attributions", uniqueConstraints = @UniqueConstraint(columnNames = {"email", "role"}))
@Getter
@Setter
@NoArgsConstructor
public class RoleAttribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleMetier role;

    @Column(nullable = false)
    private LocalDateTime dateAjout = LocalDateTime.now();

    public RoleAttribution(String email, RoleMetier role) {
        this.email = email;
        this.role = role;
    }
}
