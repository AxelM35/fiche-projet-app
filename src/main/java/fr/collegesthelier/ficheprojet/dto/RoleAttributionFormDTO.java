package fr.collegesthelier.ficheprojet.dto;

import fr.collegesthelier.ficheprojet.model.RoleMetier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleAttributionFormDTO {

    @NotBlank(message = "L'email est obligatoire.")
    @Email(message = "L'email n'est pas valide.")
    private String email;

    @NotNull(message = "Le role est obligatoire.")
    private RoleMetier role;
}
