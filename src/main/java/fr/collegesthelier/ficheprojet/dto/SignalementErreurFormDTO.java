package fr.collegesthelier.ficheprojet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO du formulaire de signalement depuis une page d'erreur (403/404/500,
 * voir fragments/signalement-erreur.html) : statutHttp et cheminOrigine sont
 * portes par des champs caches remplis automatiquement depuis les attributs
 * de modele "status"/"path" fournis par Spring Boot, pas saisis par
 * l'utilisateur.
 */
@Getter
@Setter
public class SignalementErreurFormDTO {

    @NotBlank(message = "Merci d'indiquer ce que vous étiez en train de faire avant d'envoyer.")
    @Size(max = 2000, message = "Le message ne doit pas dépasser 2000 caractères.")
    private String message;

    private Integer statutHttp;

    private String cheminOrigine;
}
