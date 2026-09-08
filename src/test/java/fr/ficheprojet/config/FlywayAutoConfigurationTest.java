package fr.ficheprojet.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Garde-fou sur la presence effective de Flyway au demarrage.
 * <p>
 * Le schema est cree et fait evoluer par Flyway (db/migration), Hibernate
 * etant en ddl-auto=validate : si Flyway ne s'execute pas, rien ne cree les
 * tables et l'application echoue au demarrage sur une base vierge
 * ("Schema validation: missing table"), sans qu'aucune ligne de log ne
 * signale l'absence de Flyway.
 * <p>
 * C'est arrive lors de la montee en Spring Boot 4 : les auto-configurations,
 * jusque-la toutes portees par spring-boot-autoconfigure, sont desormais
 * decoupees en un module par technologie. flyway-core est reste au classpath
 * mais son auto-configuration, elle, avait disparu. Les tests d'integration
 * tournant sur H2 avec spring.flyway.enabled=false, rien ne l'avait detecte.
 * <p>
 * Ce test verifie donc les deux moities du dispositif : le module
 * d'auto-configuration, et les migrations qu'il doit appliquer.
 */
class FlywayAutoConfigurationTest {

    @Test
    void lAutoConfigurationFlywayEstAuClasspath() {
        assertThatCode(() -> Class.forName(
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"))
                .as("le module org.springframework.boot:spring-boot-flyway doit figurer dans pom.xml : "
                        + "sans lui, Flyway est present mais n'est jamais execute au demarrage")
                .doesNotThrowAnyException();
    }

    @Test
    void lesMigrationsSontEmbarqueesDansLApplication() {
        assertThat(new ClassPathResource("db/migration/V1__init.sql").exists())
                .as("la baseline du schema doit etre livree dans le jar")
                .isTrue();
    }
}
