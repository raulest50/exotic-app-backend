package exotic.app.planta.service.commons;

import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import exotic.app.planta.model.commons.dto.eliminaciones.PurgaBaseDatosResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DatabasePurgeService {

    private static final String OPERATION_NAME = "La purga total de base de datos";
    private static final List<String> PRESERVED_TABLES = List.of(
            "flyway_schema_history",
            "users",
            "modulo_accesos",
            "tab_accesos",
            "super_master_config",
            "master_directive"
    );
    private static final Set<String> PRESERVED_USERS = Set.of("master", "super_master");

    private final JdbcTemplate jdbcTemplate;
    private final DangerousOperationGuard dangerousOperationGuard;
    private final ApplicationRuntimeEnvironmentResolver applicationRuntimeEnvironmentResolver;

    @Transactional
    public PurgaBaseDatosResultDTO purgeDatabaseKeepingMasterLikeAccess() {
        dangerousOperationGuard.assertLocalOrStagingOnly(OPERATION_NAME);

        List<PreservedUserRow> preservedUsers = loadPreservedUsers();
        if (preservedUsers.size() != PRESERVED_USERS.size()) {
            throw new IllegalStateException("No se puede ejecutar la purga total: master y super_master deben existir antes de purgar.");
        }

        String currentSchema = jdbcTemplate.queryForObject("select current_schema()", String.class);
        List<String> baseTables = jdbcTemplate.query(
                """
                select table_name
                from information_schema.tables
                where table_schema = ?
                  and table_type = 'BASE TABLE'
                order by table_name
                """,
                (rs, rowNum) -> rs.getString("table_name"),
                currentSchema
        );

        List<String> truncatedTables = baseTables.stream()
                .filter(tableName -> !PRESERVED_TABLES.contains(tableName))
                .toList();

        if (!truncatedTables.isEmpty()) {
            String truncateSql = buildTruncateSql(currentSchema, truncatedTables);
            log.warn("[PURGA_TOTAL_BD] Ejecutando truncado total. environment={}, schema={}, truncatedTables={}",
                    applicationRuntimeEnvironmentResolver.getCurrentEnvironment().value(),
                    currentSchema,
                    truncatedTables);
            jdbcTemplate.execute(truncateSql);
        } else {
            log.warn("[PURGA_TOTAL_BD] No se encontraron tablas truncables. environment={}, schema={}",
                    applicationRuntimeEnvironmentResolver.getCurrentEnvironment().value(),
                    currentSchema);
        }

        cleanupPreservedUserTables(preservedUsers.stream().map(PreservedUserRow::id).toList());

        PurgaBaseDatosResultDTO result = new PurgaBaseDatosResultDTO();
        result.setPermitted(true);
        result.setExecuted(true);
        result.setMessage("Purga total ejecutada correctamente. Se preservaron master y super_master.");
        result.setEnvironment(applicationRuntimeEnvironmentResolver.getCurrentEnvironment().value());
        result.setTruncatedTablesCount(truncatedTables.size());
        result.setTruncatedTables(truncatedTables);
        result.setPreservedTables(PRESERVED_TABLES);
        result.setPreservedUsers(loadPreservedUsernames());
        return result;
    }

    public void resetCurrentSchemaForFullRestore() {
        dangerousOperationGuard.assertLocalOrStagingOnly("La importacion total de base de datos");

        String currentSchema = jdbcTemplate.queryForObject("select current_schema()", String.class);
        if (currentSchema == null || currentSchema.isBlank()) {
            throw new IllegalStateException("No fue posible identificar el esquema actual para la restauracion total.");
        }

        String quotedSchema = quoteIdentifier(currentSchema);
        log.warn("[RESTORE_TOTAL_BD] Reiniciando esquema completo. environment={}, schema={}",
                applicationRuntimeEnvironmentResolver.getCurrentEnvironment().value(),
                currentSchema);

        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + quotedSchema + " CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA " + quotedSchema);
    }

    private List<PreservedUserRow> loadPreservedUsers() {
        return jdbcTemplate.query(
                """
                select id, username
                from users
                where lower(username) in ('master', 'super_master')
                order by username asc
                """,
                (rs, rowNum) -> new PreservedUserRow(rs.getLong("id"), rs.getString("username"))
        );
    }

    private void cleanupPreservedUserTables(List<Long> preservedUserIds) {
        String userIdListSql = preservedUserIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));

        jdbcTemplate.update("""
                delete from tab_accesos
                where modulo_acceso_id in (
                    select id
                    from modulo_accesos
                    where user_id not in (%s)
                )
                """.formatted(userIdListSql));

        jdbcTemplate.update("delete from modulo_accesos where user_id not in (%s)".formatted(userIdListSql));
        jdbcTemplate.update("delete from users where id not in (%s)".formatted(userIdListSql));
    }

    private List<String> loadPreservedUsernames() {
        return jdbcTemplate.query(
                """
                select username
                from users
                where lower(username) in ('master', 'super_master')
                order by username asc
                """,
                (rs, rowNum) -> rs.getString("username")
        );
    }

    private String buildTruncateSql(String schemaName, List<String> truncatedTables) {
        String qualifiedTables = truncatedTables.stream()
                .map(tableName -> quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName))
                .collect(Collectors.joining(", "));
        return "TRUNCATE TABLE " + qualifiedTables + " RESTART IDENTITY CASCADE";
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record PreservedUserRow(Long id, String username) {
    }
}
