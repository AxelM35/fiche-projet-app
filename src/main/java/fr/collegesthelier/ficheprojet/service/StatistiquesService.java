package fr.collegesthelier.ficheprojet.service;

import fr.collegesthelier.ficheprojet.dto.StatistiquesDTO;
import fr.collegesthelier.ficheprojet.model.JournalEntree;
import fr.collegesthelier.ficheprojet.model.Projet;
import fr.collegesthelier.ficheprojet.model.StatutProjet;
import fr.collegesthelier.ficheprojet.repository.JournalEntreeRepository;
import fr.collegesthelier.ficheprojet.repository.ProjetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Statistiques consolidees du dashboard Admin (/admin/statistiques),
 * calculees uniquement sur les dossiers actifs (les dossiers archives sont
 * exclus, y compris de l'historique de refus - voir filtrage sur
 * projetIdsActifs ci-dessous).
 */
@Service
@RequiredArgsConstructor
public class StatistiquesService {

    private static final List<String> ETAPES = List.of("Comptabilité", "Vie Scolaire", "Direction");

    private final ProjetRepository projetRepository;
    private final JournalEntreeRepository journalEntreeRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public StatistiquesDTO calculer() {
        List<Projet> actifs = projetRepository.findAll().stream()
                .filter(p -> !p.isArchive())
                .toList();

        return new StatistiquesDTO(
                budgetParAnneeScolaire(actifs),
                budgetParClasse(actifs),
                tauxDeRefusParEtape(actifs),
                delaiMoyenDeTraitementParEtape(actifs));
    }

    /**
     * Budget "engage" : cout global des dossiers VALIDE uniquement (meme
     * definition que la tuile "Budget total engagé" du tableau de bord).
     */
    private List<StatistiquesDTO.Repartition> budgetParAnneeScolaire(List<Projet> actifs) {
        return regrouperBudget(actifs, p -> AnneeScolaireUtil.calculer(p.getDateDepart()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByKey().reversed())
                .map(entree -> new StatistiquesDTO.Repartition(entree.getKey(), entree.getValue()))
                .toList();
    }

    private List<StatistiquesDTO.Repartition> budgetParClasse(List<Projet> actifs) {
        return regrouperBudget(actifs, Projet::getClassesConcernees)
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entree -> new StatistiquesDTO.Repartition(entree.getKey(), entree.getValue()))
                .toList();
    }

    private Map<String, BigDecimal> regrouperBudget(List<Projet> actifs, Function<Projet, String> cleDeGroupement) {
        Map<String, BigDecimal> parGroupe = new LinkedHashMap<>();
        actifs.stream()
                .filter(p -> p.getStatut() == StatutProjet.VALIDE)
                .forEach(p -> {
                    String cle = cleDeGroupement.apply(p);
                    if (cle == null || cle.isBlank()) {
                        return;
                    }
                    BigDecimal cout = p.getCoutGlobal() != null ? p.getCoutGlobal() : BigDecimal.ZERO;
                    parGroupe.merge(cle, cout, BigDecimal::add);
                });
        return parGroupe;
    }

    /**
     * Taux de refus par etape = nb de refus survenus a cette etape / (nb de
     * validations + nb de refus a cette etape), a partir du journal d'audit
     * (voir ProjetService.refuser, action "Refus (Etape)"). Les dossiers
     * archives (ou definitivement supprimes) sont exclus en filtrant sur les
     * projetId encore actifs.
     */
    private List<StatistiquesDTO.TauxRefusParEtape> tauxDeRefusParEtape(List<Projet> actifs) {
        Set<Long> projetIdsActifs = actifs.stream().map(Projet::getId).collect(Collectors.toSet());
        List<JournalEntree> journalActif = journalEntreeRepository.findAll().stream()
                .filter(entree -> projetIdsActifs.contains(entree.getProjetId()))
                .toList();

        return ETAPES.stream()
                .map(etape -> {
                    long nbValidations = compterAction(journalActif, actionValidation(etape));
                    long nbRefus = compterAction(journalActif, "Refus (" + etape + ")");
                    long total = nbValidations + nbRefus;
                    double taux = total == 0 ? 0.0 : (100.0 * nbRefus / total);
                    return new StatistiquesDTO.TauxRefusParEtape(etape, nbValidations, nbRefus, taux);
                })
                .toList();
    }

    private long compterAction(List<JournalEntree> journal, String action) {
        return journal.stream().filter(entree -> action.equals(entree.getAction())).count();
    }

    private String actionValidation(String etape) {
        return switch (etape) {
            case "Comptabilité" -> "Validation Comptabilité";
            case "Vie Scolaire" -> "Validation Vie Scolaire";
            case "Direction" -> "Validation Direction (finale)";
            default -> throw new IllegalArgumentException("Étape inconnue : " + etape);
        };
    }

    /**
     * Delai moyen entre l'entree dans l'etape et sa validation, calcule
     * directement a partir des dates de validation deja presentes sur
     * Projet (pas besoin du journal) : couvre aussi bien les dossiers deja
     * VALIDE que ceux encore en cours d'instruction ayant deja franchi
     * l'etape mesuree.
     */
    private List<StatistiquesDTO.DelaiParEtape> delaiMoyenDeTraitementParEtape(List<Projet> actifs) {
        return List.of(
                delaiEtape("Comptabilité", actifs, Projet::getDateValidationProf, Projet::getDateValidationCompta),
                delaiEtape("Vie Scolaire", actifs, Projet::getDateValidationCompta, Projet::getDateValidationVieScolaire),
                delaiEtape("Direction", actifs, Projet::getDateValidationVieScolaire, Projet::getDateValidationDirection));
    }

    private StatistiquesDTO.DelaiParEtape delaiEtape(String etape, List<Projet> actifs,
                                                      Function<Projet, LocalDateTime> dateEntree,
                                                      Function<Projet, LocalDateTime> dateSortie) {
        List<Double> delaisEnJours = actifs.stream()
                .map(p -> {
                    LocalDateTime entree = dateEntree.apply(p);
                    LocalDateTime sortie = dateSortie.apply(p);
                    if (entree == null || sortie == null || sortie.isBefore(entree)) {
                        return null;
                    }
                    return Duration.between(entree, sortie).toHours() / 24.0;
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        double moyenne = delaisEnJours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return new StatistiquesDTO.DelaiParEtape(etape, delaisEnJours.size(), moyenne);
    }
}
