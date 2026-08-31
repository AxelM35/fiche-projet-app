package fr.ficheprojet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identité de l'établissement qui déploie l'application (transmise via la
 * variable d'environnement ETABLISSEMENT_NOM).
 *
 * <p>L'application est prévue pour être déployée telle quelle par n'importe
 * quel établissement : aucun nom n'est écrit en dur dans le code ou les
 * gabarits. Le nom configuré ici apparaît dans la barre de navigation, sur la
 * page de connexion, dans les emails de notification et sur l'export PDF.
 *
 * <p>Vide par défaut : les vues masquent alors simplement la mention plutôt
 * que d'afficher un nom d'exemple à des utilisateurs réels.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ficheprojet.etablissement")
public class EtablissementProperties {

    private String nom = "";
}
