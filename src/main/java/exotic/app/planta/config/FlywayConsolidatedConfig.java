package exotic.app.planta.config;

import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Keeps validation strict for the consolidated migration line, including migrations
 * from a different history that Flyway would otherwise classify as future versions.
 */
@Configuration(proxyBeanMethods = false)
@Profile("schema-v1")
public class FlywayConsolidatedConfig {

    private static final MigrationVersion ROOT_VERSION = MigrationVersion.fromVersion("1");
    private static final String ROOT_SCRIPT = "V001__esquema_actual_consolidado.sql";

    @Bean
    FlywayConfigurationCustomizer consolidatedFlywayValidation() {
        // Spring Boot 3.2.5 does not pass an empty YAML ignore-pattern list to Flyway.
        return configuration -> {
            configuration.ignoreMigrationPatterns(new String[0]);
        };
    }

    @Bean
    FlywayMigrationStrategy consolidatedFlywayMigrationStrategy() {
        return flyway -> {
            MigrationInfo[] appliedMigrations = flyway.info().applied();
            boolean hasConsolidatedRoot = false;
            boolean hasBaselineMarker = false;

            for (MigrationInfo migration : appliedMigrations) {
                if (CoreMigrationType.BASELINE.equals(migration.getType())) {
                    hasBaselineMarker = true;
                }
                if (CoreMigrationType.SQL.equals(migration.getType())
                        && migration.getVersion() != null
                        && migration.getVersion().compareTo(ROOT_VERSION) == 0
                        && ROOT_SCRIPT.equals(migration.getScript())) {
                    hasConsolidatedRoot = true;
                }
            }

            if (appliedMigrations.length > 0 && (!hasConsolidatedRoot || hasBaselineMarker)) {
                throw new IllegalStateException(
                        "El perfil schema-v1 encontro un historial Flyway incompatible. "
                                + "La base debe ser nueva o contener la migracion SQL V1 "
                                + ROOT_SCRIPT + " sin marcadores BASELINE heredados.");
            }

            flyway.migrate();
        };
    }
}
