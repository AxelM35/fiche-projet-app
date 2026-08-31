package fr.ficheprojet.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LienDriveFormDTO {

    /**
     * Facultatif (vide pour retirer le lien). Restreint aux URL Drive/Docs
     * Google pour éviter qu'un lien arbitraire (potentiellement malveillant,
     * ex. javascript:) ne soit stocké puis rendu cliquable à tout utilisateur
     * consultant la fiche.
     */
    @Size(max = 500, message = "Le lien est trop long.")
    @Pattern(regexp = "^$|^https://(drive|docs)\\.google\\.com/.*$",
            message = "Le lien doit être une URL Google Drive (https://drive.google.com/...).")
    private String lienDrive;
}
