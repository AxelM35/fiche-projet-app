package fr.collegesthelier.ficheprojet.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReaffectationFormDTO {

    @NotBlank(message = "Le nom du nouvel organisateur est obligatoire.")
    private String organisateurNom;

    @NotBlank(message = "L'email du nouvel organisateur est obligatoire.")
    @Email(message = "L'email n'est pas valide.")
    private String organisateurEmail;
}
