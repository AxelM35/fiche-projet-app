package fr.collegesthelier.voyages.web;

import fr.collegesthelier.voyages.config.RolesProperties;
import fr.collegesthelier.voyages.dto.RoleAttributionFormDTO;
import fr.collegesthelier.voyages.model.Projet;
import fr.collegesthelier.voyages.model.RoleMetier;
import fr.collegesthelier.voyages.model.StatutProjet;
import fr.collegesthelier.voyages.service.JournalService;
import fr.collegesthelier.voyages.service.ProjetService;
import fr.collegesthelier.voyages.service.RoleAdminService;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard admin : gestion des attributions de roles et des dossiers
 * archives. Reserve a ROLE_ADMIN au niveau du controleur (et non seulement
 * du service) car il expose des donnees (emails par role, dossiers retires
 * du tableau de bord) a toute personne qui en devinerait l'URL, contrairement
 * au reste de l'application ou les donnees des dossiers actifs sont
 * volontairement consultables par tout utilisateur authentifie.
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final RoleAdminService roleAdminService;
    private final RolesProperties rolesProperties;
    private final ProjetService projetService;
    private final JournalService journalService;

    @GetMapping("/admin/archives")
    public String archives(Model model) {
        model.addAttribute("projetsArchives", projetService.listerArchives());
        return "admin-archives";
    }

    @GetMapping("/admin/dossiers-bloques")
    public String dossiersBloques(Model model) {
        model.addAttribute("dossiersBloques", projetService.listerDossiersBloques());
        return "admin-dossiers-bloques";
    }

    @GetMapping("/admin/journal")
    public String journal(Model model) {
        model.addAttribute("entrees", journalService.listerRecentes());
        return "admin-journal";
    }

    @GetMapping("/admin/recherche")
    public String recherche(@RequestParam(required = false) String nom,
                             @RequestParam(required = false) String organisateur,
                             @RequestParam(required = false) String classe,
                             @RequestParam(required = false) String statut,
                             @RequestParam(required = false) String archive,
                             Model model) {
        model.addAttribute("resultats", projetService.rechercherPourAdmin(
                nom, organisateur, classe, statutOuNull(statut), archiveOuNull(archive)));
        model.addAttribute("nom", nom);
        model.addAttribute("organisateur", organisateur);
        model.addAttribute("classe", classe);
        model.addAttribute("statut", statut);
        model.addAttribute("archive", archive);
        model.addAttribute("statuts", StatutProjet.values());
        return "admin-recherche";
    }

    @GetMapping("/admin/recherche/export.csv")
    public void exporterCsv(@RequestParam(required = false) String nom,
                             @RequestParam(required = false) String organisateur,
                             @RequestParam(required = false) String classe,
                             @RequestParam(required = false) String statut,
                             @RequestParam(required = false) String archive,
                             HttpServletResponse response) throws IOException {
        List<Projet> resultats = projetService.rechercherPourAdmin(
                nom, organisateur, classe, statutOuNull(statut), archiveOuNull(archive));

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=projets.csv");
        PrintWriter writer = response.getWriter();
        writer.write("\uFEFF"); // BOM UTF-8, pour qu'Excel detecte correctement l'encodage.
        writer.println(String.join(",", champsCsv("Id", "Nom", "Statut", "Archive", "Date depart",
                "Date retour", "Organisateur", "Email organisateur", "Classes", "Effectif", "Cout global")));
        for (Projet projet : resultats) {
            writer.println(String.join(",", champsCsv(
                    String.valueOf(projet.getId()),
                    projet.getNomProjet(),
                    projet.getStatut().name(),
                    projet.isArchive() ? "oui" : "non",
                    String.valueOf(projet.getDateDepart()),
                    String.valueOf(projet.getDateRetour()),
                    projet.getOrganisateurNom(),
                    projet.getOrganisateurEmail(),
                    projet.getClassesConcernees(),
                    String.valueOf(projet.getEffectif()),
                    String.valueOf(projet.getCoutGlobal()))));
        }
    }

    private StatutProjet statutOuNull(String statut) {
        return (statut == null || statut.isBlank()) ? null : StatutProjet.valueOf(statut);
    }

    private Boolean archiveOuNull(String archive) {
        if (archive == null || archive.isBlank()) {
            return null;
        }
        return "archive".equals(archive);
    }

    private String[] champsCsv(String... valeurs) {
        String[] echappes = new String[valeurs.length];
        for (int i = 0; i < valeurs.length; i++) {
            String valeur = valeurs[i] == null ? "" : valeurs[i];
            if (valeur.contains(",") || valeur.contains("\"") || valeur.contains("\n")) {
                valeur = "\"" + valeur.replace("\"", "\"\"") + "\"";
            }
            echappes[i] = valeur;
        }
        return echappes;
    }

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
