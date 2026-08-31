package fr.ficheprojet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de la fenêtre modale "Refuser" : ne transporte que le motif saisi.
 */
@Getter
@Setter
public class RefusFormDTO {

    @NotBlank(message = "Le motif de refus est obligatoire.")
    @Size(max = 2000, message = "Le motif ne doit pas dépasser 2000 caractères.")
    private String motifRefus;
}
