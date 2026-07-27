package fr.collegesthelier.ficheprojet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO du formulaire "Compléter le budget" (voir ProjetService.completerBudget) :
 * contrairement a ProjetFormDTO.coutGlobal/coutParEleve, obligatoires ici -
 * c'est precisement le formulaire qui sert a les renseigner.
 */
@Getter
@Setter
public class CompleterBudgetFormDTO {

    @NotNull(message = "Le coût global est obligatoire.")
    @PositiveOrZero(message = "Le coût global doit être positif ou nul.")
    private BigDecimal coutGlobal;

    @NotNull(message = "Le coût par élève est obligatoire.")
    @PositiveOrZero(message = "Le coût par élève doit être positif ou nul.")
    private BigDecimal coutParEleve;

    @PositiveOrZero(message = "Le montant de la subvention doit être positif ou nul.")
    private BigDecimal montantSubvention;
}
