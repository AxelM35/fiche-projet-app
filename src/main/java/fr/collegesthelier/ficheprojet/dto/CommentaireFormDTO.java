package fr.collegesthelier.ficheprojet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO du formulaire d'ajout/modification d'un commentaire sur un dossier.
 */
@Getter
@Setter
public class CommentaireFormDTO {

    @NotBlank(message = "Le commentaire ne peut pas être vide.")
    @Size(max = 4000, message = "Le commentaire ne doit pas dépasser 4000 caractères.")
    private String texte;
}
