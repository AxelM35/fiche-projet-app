package fr.collegesthelier.ficheprojet.model;

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
 * Attribution d'un role metier a un email, geree dynamiquement par un Admin
 * depuis le dashboard admin. S'ajoute (sans jamais les remplacer) aux listes
 * d'emails configurees en variables d'environnement (RolesProperties) :
 * CustomOAuth2UserService verifie l'union des deux sources pour chaque role,
 * de sorte qu'une erreur de manipulation ici ne puisse jamais retirer
 * l'acces attribue via .env.
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
