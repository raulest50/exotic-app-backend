package exotic.app.planta.service.commons;

import exotic.app.planta.service.commons.BackupV2ArchiveService.SchemaMigration;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compatibility checks use the running artifact, never a hard-coded schema version. */
@Service
public class BackupSchemaCompatibilityService {
    private static final long MAX_HISTORY_BYTES = 16L * 1024 * 1024;
    private static final int MAX_HISTORY_ROWS = 100_000;
    private static final int MAX_LINE_LENGTH = 65_536;
    private static final String HISTORY_COPY = "COPY public.flyway_schema_history "
            + "(installed_rank, version, description, type, script, checksum, installed_by, "
            + "installed_on, execution_time, success) FROM stdin;";

    private final Flyway flyway;
    private final ObjectProvider<FlywayMigrationStrategy> migrationStrategies;
    private final EntityManagerFactory entityManagerFactory;
    private final PgDumpExecutableResolver executables;

    public BackupSchemaCompatibilityService(Flyway flyway,
                                             ObjectProvider<FlywayMigrationStrategy> migrationStrategies,
                                             EntityManagerFactory entityManagerFactory,
                                             PgDumpExecutableResolver executables) {
        this.flyway = flyway;
        this.migrationStrategies = migrationStrategies;
        this.entityManagerFactory = entityManagerFactory;
        this.executables = executables;
    }

    public record SchemaPlan(String sourceVersion, String targetVersion, List<SchemaMigration> sourceMigrations) {
        public SchemaPlan {
            sourceMigrations = List.copyOf(sourceMigrations);
        }
    }

    /** No connection to the destination is passed to pg_restore during inspection. */
    public SchemaPlan inspect(Path dump, Path workspace, List<SchemaMigration> manifestMigrations) throws Exception {
        if (dump == null || workspace == null || !Files.isRegularFile(dump) || !Files.isDirectory(workspace)) {
            throw incompatible("No se encuentra disponible el respaldo que debe comprobarse.");
        }
        Path history = null;
        try {
            history = Files.createTempFile(workspace, "flyway-history-", ".sql");
            BackupProcessRunner.run(List.of(executables.resolveRestoreExecutable(),
                            "--data-only", "--no-owner", "--no-privileges", "--schema=public",
                            "--table=flyway_schema_history", "--file=-", dump.toAbsolutePath().toString()),
                    null, Duration.ofMinutes(2), history, MAX_HISTORY_BYTES);
            return planFor(parseHistoryCopy(history), manifestMigrations);
        } catch (IOException exception) {
            // Tool output and I/O messages may contain restored data or private paths.
            throw incompatible("No fue posible comprobar el historial del respaldo antes de restaurar.");
        } finally {
            if (history != null) {
                try {
                    Files.deleteIfExists(history);
                } catch (IOException ignored) {
                    // The enclosing import workspace also owns cleanup of this bounded file.
                }
            }
        }
    }

    SchemaPlan planFor(List<HistoryRow> source, List<SchemaMigration> manifestMigrations) {
        Flyway strict = configuredFlyway(false);
        List<HistoryRow> target = validatedHistory(strict, false);
        return compatiblePlan(source, target, manifestMigrations);
    }

    /** Checks the committed restore before any pending migration is executed. */
    public void verifyRestoredHistory(SchemaPlan plan) {
        requirePlan(plan);
        List<HistoryRow> actual = validatedHistory(configuredFlyway(true), true);
        if (!sameMigrations(plan.sourceMigrations(), migrations(actual))) {
            throw new IllegalStateException("El historial restaurado no corresponde al respaldo comprobado.");
        }
    }

    /** Reuses the active strategy, including the consolidated-line root guard when selected. */
    public int migrate(SchemaPlan plan) {
        verifyRestoredHistory(plan);
        try {
            Flyway strict = configuredFlyway(false);
            int before = strict.info().applied().length;
            FlywayMigrationStrategy strategy = migrationStrategies.getIfAvailable();
            if (strategy == null) {
                strict.migrate();
            } else {
                strategy.migrate(strict);
            }
            List<HistoryRow> applied = validatedHistory(strict, false);
            requireTarget(plan, applied);
            return Math.max(0, strict.info().applied().length - before);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("No fue posible completar las migraciones del artefacto activo. "
                    + "La importacion requiere recuperacion antes de volver a usar la base.");
        }
    }

    public void validateSchema(SchemaPlan plan) {
        requirePlan(plan);
        List<HistoryRow> applied = validatedHistory(configuredFlyway(false), false);
        requireTarget(plan, applied);
        try {
            SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
            sessionFactory.getSchemaManager().validateMappedObjects();
            // The restore bypasses ORM writes. Invalidate entity/collection/natural-id/query regions
            // before sanitization opens a new persistence context against the restored rows.
            sessionFactory.getCache().evictAllRegions();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("No fue posible validar el esquema e invalidar las caches de persistencia. "
                    + "La importacion requiere recuperacion antes de volver a usar la base.");
        }
    }

    private Flyway configuredFlyway(boolean allowPending) {
        try {
            String defaultSchema = flyway.getConfiguration().getDefaultSchema();
            if (flyway.getConfiguration().isSkipExecutingMigrations()
                    || !"flyway_schema_history".equals(flyway.getConfiguration().getTable())
                    || (defaultSchema != null && !"public".equals(defaultSchema))) {
                throw new IllegalStateException();
            }
            for (String schema : flyway.getConfiguration().getSchemas()) {
                if (!"public".equals(schema)) throw new IllegalStateException();
            }
            // Copy the active locations, target, classloader, datasource and callbacks. This does not
            // change the application's Flyway bean or the user's environment configuration.
            return Flyway.configure(flyway.getConfiguration().getClassLoader())
                    .configuration(flyway.getConfiguration())
                    .ignoreMigrationPatterns(allowPending ? new String[]{"*:pending"} : new String[0])
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("No fue posible preparar la validacion estricta de Flyway.");
        }
    }

    private static List<HistoryRow> validatedHistory(Flyway strict, boolean allowPending) {
        try {
            try (var connection = strict.getConfiguration().getDataSource().getConnection()) {
                BackupV2ArchiveService.requirePublicSchema(connection);
            }
            MigrationInfoService info = strict.info();
            for (MigrationInfo migration : info.all()) {
                MigrationState state = migration.getState();
                // Explicitly reject future/missing/failed/deleted/ignored/out-of-order migrations;
                // never rely on Flyway's default *:future ignore pattern.
                boolean allowed = state == MigrationState.SUCCESS || state == MigrationState.BASELINE
                        || state == MigrationState.BELOW_BASELINE || state == MigrationState.BASELINE_IGNORED
                        || state == MigrationState.ABOVE_TARGET || (allowPending && state == MigrationState.PENDING);
                if (!allowed) throw new IllegalStateException();
            }
            if (!strict.validateWithResult().validationSuccessful) throw new IllegalStateException();
            List<HistoryRow> applied = new ArrayList<>();
            for (MigrationInfo migration : info.applied()) {
                if (migration.getVersion() == null || migration.getInstalledRank() == null) {
                    throw new IllegalStateException();
                }
                applied.add(new HistoryRow(migration.getInstalledRank(), migration.getVersion().getVersion(),
                        migration.getType().name(), migration.getScript(), migration.getChecksum(), true));
            }
            return checkedRows(applied);
        } catch (Exception exception) {
            throw new IllegalStateException("El historial de la base no es compatible con el artefacto activo "
                    + "o tiene migraciones pendientes, desconocidas o fallidas.");
        }
    }

    private static void requireTarget(SchemaPlan plan, List<HistoryRow> applied) {
        if (version(last(applied).version()).compareTo(version(plan.targetVersion())) != 0) {
            throw new IllegalStateException("La base no alcanzo la version prevista del artefacto activo.");
        }
    }

    private static void requirePlan(SchemaPlan plan) {
        if (plan == null || plan.sourceMigrations() == null || plan.sourceMigrations().isEmpty()) {
            throw new IllegalArgumentException("Falta el plan de compatibilidad del respaldo.");
        }
    }

    /** Pure seam for compatibility tests; rows are ordered by installed_rank, never lexical version. */
    static SchemaPlan compatiblePlan(List<HistoryRow> sourceRows, List<HistoryRow> targetRows,
                                     List<SchemaMigration> manifestMigrations) {
        List<HistoryRow> source = checkedRows(sourceRows);
        List<HistoryRow> target = checkedRows(targetRows);
        if (source.size() > target.size()
                || version(last(source).version()).compareTo(version(last(target).version())) > 0) {
            throw incompatible("El respaldo pertenece a una version posterior al artefacto activo.");
        }
        for (int index = 0; index < source.size(); index++) {
            HistoryRow incoming = source.get(index);
            HistoryRow expected = target.get(index);
            if (version(incoming.version()).compareTo(version(expected.version())) != 0
                    || !incoming.type().equals(expected.type()) || !incoming.script().equals(expected.script())
                    || !Objects.equals(incoming.checksum(), expected.checksum())) {
                throw incompatible("El respaldo contiene un historial diferente de la linea activa "
                        + "o checksums incompatibles. No se modifico la base.");
            }
        }
        List<SchemaMigration> sourceMigrations = migrations(source);
        if (manifestMigrations != null && !sameMigrations(sourceMigrations, manifestMigrations)) {
            throw incompatible("El historial del dump no corresponde al manifiesto del paquete V2.");
        }
        return new SchemaPlan(last(source).version(), last(target).version(), sourceMigrations);
    }

    private static List<SchemaMigration> migrations(List<HistoryRow> rows) {
        return rows.stream().map(row -> new SchemaMigration(row.version(), row.checksum())).toList();
    }

    private static boolean sameMigrations(List<SchemaMigration> expected, List<SchemaMigration> actual) {
        if (expected == null || actual == null || expected.size() != actual.size()) return false;
        try {
            List<SchemaMigration> sortedExpected = orderedMigrations(expected);
            List<SchemaMigration> sortedActual = orderedMigrations(actual);
            for (int index = 0; index < sortedExpected.size(); index++) {
                SchemaMigration left = sortedExpected.get(index);
                SchemaMigration right = sortedActual.get(index);
                if (version(left.version()).compareTo(version(right.version())) != 0
                        || !Objects.equals(left.checksum(), right.checksum())) return false;
            }
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static List<SchemaMigration> orderedMigrations(List<SchemaMigration> rows) {
        Set<MigrationVersion> versions = new HashSet<>();
        for (SchemaMigration row : rows) {
            if (row == null || !versions.add(version(row.version()))) {
                throw incompatible("El historial del manifiesto no es valido.");
            }
        }
        return rows.stream().sorted(Comparator.comparing(row -> version(row.version()))).toList();
    }

    private static List<HistoryRow> checkedRows(List<HistoryRow> rows) {
        if (rows == null || rows.isEmpty() || rows.size() > MAX_HISTORY_ROWS) {
            throw incompatible("El respaldo no contiene un historial Flyway completo y reconocido.");
        }
        Set<Integer> ranks = new HashSet<>();
        Set<MigrationVersion> versions = new HashSet<>();
        int baselines = 0;
        for (HistoryRow row : rows) {
            if (row == null || row.installedRank() <= 0 || !ranks.add(row.installedRank())
                    || !row.success() || !versions.add(version(row.version())) || row.script() == null
                    || row.script().isBlank() || row.script().chars().anyMatch(Character::isISOControl)) {
                throw incompatible("El respaldo contiene un historial Flyway incompleto, fallido o no reconocido.");
            }
            if ("BASELINE".equals(row.type())) {
                baselines++;
                if (row.checksum() != null) throw incompatible("El marcador historico de Flyway no es valido.");
            } else if (!"SQL".equals(row.type()) || row.checksum() == null) {
                throw incompatible("El respaldo contiene tipos de migracion no admitidos por esta importacion.");
            }
        }
        List<HistoryRow> ordered = rows.stream().sorted(Comparator.comparingInt(HistoryRow::installedRank)).toList();
        if (baselines > 1 || (baselines == 1 && !"BASELINE".equals(ordered.get(0).type()))) {
            throw incompatible("El respaldo mezcla marcadores o lineas incompatibles de Flyway.");
        }
        for (int index = 1; index < ordered.size(); index++) {
            if (version(ordered.get(index - 1).version()).compareTo(version(ordered.get(index).version())) >= 0) {
                throw incompatible("El orden historico de las migraciones del respaldo no es compatible.");
            }
        }
        return ordered;
    }

    private static HistoryRow last(List<HistoryRow> rows) {
        return rows.get(rows.size() - 1);
    }

    private static MigrationVersion version(String text) {
        if (text == null || !text.matches("[0-9]+(?:[._][0-9]+)*")) {
            throw incompatible("El historial contiene una version Flyway no reconocida.");
        }
        try {
            return MigrationVersion.fromVersion(text);
        } catch (RuntimeException exception) {
            throw incompatible("El historial contiene una version Flyway no reconocida.");
        }
    }

    record HistoryRow(int installedRank, String version, String type, String script, Integer checksum, boolean success) { }

    /** Reads only the COPY data block emitted by our pg_dump exports; never executes its SQL. */
    static List<HistoryRow> parseHistoryCopy(Path sql) throws IOException {
        if (!Files.isRegularFile(sql) || Files.size(sql) > MAX_HISTORY_BYTES) {
            throw incompatible("El historial del respaldo no esta disponible o excede el limite permitido.");
        }
        List<HistoryRow> rows = new ArrayList<>();
        boolean found = false;
        boolean inCopy = false;
        boolean completed = false;
        try (BufferedReader reader = Files.newBufferedReader(sql, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > MAX_LINE_LENGTH) throw incompatible("El historial del respaldo excede el formato admitido.");
                if (!inCopy) {
                    if (line.startsWith("COPY ")) {
                        if (found || !HISTORY_COPY.equals(line)) {
                            throw incompatible("El respaldo no utiliza el formato COPY de historial admitido.");
                        }
                        found = true;
                        inCopy = true;
                    }
                    continue;
                }
                if ("\\.".equals(line)) {
                    inCopy = false;
                    completed = true;
                    continue;
                }
                if (rows.size() >= MAX_HISTORY_ROWS) throw incompatible("El historial del respaldo excede el limite permitido.");
                String[] columns = line.split("\t", -1);
                if (columns.length != 10) throw incompatible("El historial del respaldo tiene una estructura no reconocida.");
                try {
                    String[] decoded = new String[columns.length];
                    for (int index = 0; index < columns.length; index++) decoded[index] = copyText(columns[index]);
                    if (decoded[2] == null || decoded[6] == null || decoded[7] == null
                            || decoded[8] == null || Integer.parseInt(decoded[8]) < 0
                            || !("t".equals(decoded[9]) || "f".equals(decoded[9]))) {
                        throw new IllegalArgumentException();
                    }
                    rows.add(new HistoryRow(Integer.parseInt(decoded[0]), decoded[1], decoded[3], decoded[4],
                            decoded[5] == null ? null : Integer.valueOf(decoded[5]), "t".equals(decoded[9])));
                } catch (IllegalArgumentException exception) {
                    throw incompatible("El historial del respaldo contiene valores no reconocidos.");
                }
            }
        }
        if (!found || !completed || inCopy) {
            throw incompatible("El respaldo no contiene un bloque COPY completo de historial Flyway.");
        }
        return checkedRows(rows);
    }

    private static String copyText(String value) {
        if ("\\N".equals(value)) return null;
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '\\') {
                result.append(character);
                continue;
            }
            if (++index >= value.length()) throw new IllegalArgumentException();
            char escaped = value.charAt(index);
            result.append(switch (escaped) {
                case '\\' -> '\\';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'v' -> '\u000b';
                default -> throw new IllegalArgumentException();
            });
        }
        return result.toString();
    }

    private static IllegalArgumentException incompatible(String message) {
        return new IllegalArgumentException(message);
    }
}
