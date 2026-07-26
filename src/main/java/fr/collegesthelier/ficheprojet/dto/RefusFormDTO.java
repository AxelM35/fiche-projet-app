package fr.collegesthelier.ficheprojet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de la fenetre modale "Refuser" : ne transporte que le motif saisi.
 */
@Getter
@Setter
public class RefusFormDTO {

    @NotBlank(message = "Le motif de refus est obligatoire.")
    @Size(max = 2000, message = "Le motif ne doit pas depasser 2000 caracteres.")
    private String motifRefus;
}
