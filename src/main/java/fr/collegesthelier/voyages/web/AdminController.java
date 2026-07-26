package fr.collegesthelier.voyages.web;

import fr.collegesthelier.voyages.config.RolesProperties;
import fr.collegesthelier.voyages.dto.RoleAttributionFormDTO;
import fr.collegesthelier.voyages.model.RoleMetier;
import fr.collegesthelier.voyages.service.RoleAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard admin : gestion des attributions de roles. Reserve a ROLE_ADMIN
 * au niveau du controleur (et non seulement du service) car il expose la
 * liste des emails par role a toute personne qui en devinerait l'URL,
 * contrairement au reste de l'application ou les donnees des dossiers sont
 * volontairement consultables par tout utilisateur authentifie.
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final RoleAdminService roleAdminService;
    private final RolesProperties rolesProperties;

    @GetMapping("/admin/roles")
    public String rolesAdmin(Model model) {
        model.addAttribute("attributionsParRole", roleAdminService.listerParRole());
        model.addAttribute("emailsEnvParRole", emailsEnvParRole());
        model.addAttribute("nouvelleAttribution", new RoleAttributionFormDTO());
        model.addAttribute("roles", RoleMetier.values());
        return "admin-roles";
    }

    @PostMapping("/admin/roles")
    public String ajouterAttribution(@Valid @ModelAttribute("nouvelleAttribution") RoleAttributionFormDTO dto,
                                      BindingResult bindingResult, Model model,
                                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("attributionsParRole", roleAdminService.listerParRole());
            model.addAttribute("emailsEnvParRole", emailsEnvParRole());
            model.addAttribute("roles", RoleMetier.values());
            return "admin-roles";
        }

        roleAdminService.ajouter(dto.getEmail(), dto.getRole());
        redirectAttributes.addFlashAttribute("messageSucces",
                dto.getEmail() + " a bien recu le role " + dto.getRole() + ".");
        return "redirect:/admin/roles";
    }

    @PostMapping("/admin/roles/{id}/supprimer")
    public String retirerAttribution(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        roleAdminService.retirer(id);
        redirectAttributes.addFlashAttribute("messageSucces", "L'attribution de role a ete retiree.");
        return "redirect:/admin/roles";
    }

    private Map<RoleMetier, List<String>> emailsEnvParRole() {
        Map<RoleMetier, List<String>> parRole = new EnumMap<>(RoleMetier.class);
        parRole.put(RoleMetier.COMPTA, rolesProperties.getCompta());
        parRole.put(RoleMetier.VIESCO, rolesProperties.getViesco());
        parRole.put(RoleMetier.DIRECTION, rolesProperties.getDirection());
        parRole.put(RoleMetier.ADMIN, rolesProperties.getAdmin());
        parRole.put(RoleMetier.LECTURE_SEULE, rolesProperties.getLectureSeule());
        return parRole;
    }
}
