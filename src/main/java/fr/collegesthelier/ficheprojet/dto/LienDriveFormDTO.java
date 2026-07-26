package fr.collegesthelier.ficheprojet.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LienDriveFormDTO {

    /**
     * Facultatif (vide pour retirer le lien). Restreint aux URL Drive/Docs
     * Google pour eviter qu'un lien arbitraire (potentiellement malveillant,
     * ex. javascript:) ne soit stocke puis rendu cliquable a tout utilisateur
     * consultant la fiche.
     */
    @Size(max = 500, message = "Le lien est trop long.")
    @Pattern(regexp = "^$|^https://(drive|docs)\\.google\\.com/.*$",
            message = "Le lien doit etre une URL Google Drive (https://drive.google.com/...).")
    private String lienDrive;
}
