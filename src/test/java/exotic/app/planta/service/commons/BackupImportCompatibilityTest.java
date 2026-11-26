package exotic.app.planta.service.commons;

import exotic.app.planta.service.commons.BackupSchemaCompatibilityService.HistoryRow;
import exotic.app.planta.service.commons.BackupV2ArchiveService.SchemaMigration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Compatibility expectations are independent of the version currently deployed or stored in the destination. */
class BackupImportCompatibilityTest {
    private static final String COPY_HEADER = "COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;\n";
    private static final List<HistoryRow> TARGET = List.of(
            row(1, "1", "V001__fixture.sql", 101),
            row(2, "2", "V002__fixture.sql", -202),
            row(3, "10", "V010__fixture.sql", 303));

    @TempDir Path directory;

    @Test
    void acceptsAnEqualHistoryAndUsesFlywayVersionOrdering() {
        var plan = BackupSchemaCompatibilityService.compatiblePlan(TARGET, TARGET, metadata(TARGET));

        assertEquals("10", plan.sourceVersion());
        assertEquals("10", plan.targetVersion());
        assertEquals(metadata(TARGET), plan.sourceMigrations());
    }

    @Test
    void acceptsAnOldPrefixAndKeepsItsSourceHistoryDistinctFromTheTarget() {
        List<HistoryRow> source = TARGET.subList(0, 1);

        var v1 = BackupSchemaCompatibilityService.compatiblePlan(source, TARGET, null);
        var v2 = BackupSchemaCompatibilityService.compatiblePlan(source, TARGET, metadata(source));

        assertEquals(v1, v2);
        assertEquals("1", v1.sourceVersion());
        assertEquals("10", v1.targetVersion());
        assertEquals(List.of(new SchemaMigration("1", 101)), v1.sourceMigrations());
    }

    @Test
    void rejectsAFutureMigrationAndAnIncompletePrefix() {
        List<HistoryRow> future = List.of(TARGET.get(0), TARGET.get(1), TARGET.get(2),
                row(4, "11", "V011__future.sql", 404));
        List<HistoryRow> missingMiddle = List.of(TARGET.get(0), TARGET.get(2));

        assertThrows(IllegalArgumentException.class,
                () -> BackupSchemaCompatibilityService.compatiblePlan(future, TARGET, null));
        assertThrows(IllegalArgumentException.class,
                () -> BackupSchemaCompatibilityService.compatiblePlan(missingMiddle, TARGET, null));
    }

    @Test
    void rejectsChangedChecksumsAndADifferentMigrationLineEvenWhenVersionsMatch() {
        List<HistoryRow> changedChecksum = List.of(row(1, "1", "V001__fixture.sql", 999));
        List<HistoryRow> differentLine = List.of(row(1, "1", "V001__esquema_actual_consolidado.sql", 101));

        assertThrows(IllegalArgumentException.class,
                () -> BackupSchemaCompatibilityService.compatiblePlan(changedChecksum, TARGET, null));
        assertThrows(IllegalArgumentException.class,
                () -> BackupSchemaCompatibilityService.compatiblePlan(differentLine, TARGET, null));
    }

    @Test
    void rejectsFailedEntriesDuplicatesAndABaselineFromAnotherLine() {
        HistoryRow failed = new HistoryRow(1, "1", "SQL", "V001__fixture.sql", 101, false);
        HistoryRow baseline = new HistoryRow(1, "1", "BASELINE", "<< Flyway Baseline >>", null, true);

        for (List<HistoryRow> source : List.of(List.of(failed), List.of(baseline),
                List.of(TARGET.get(0), TARGET.get(0)))) {
            assertThrows(IllegalArgumentException.class,
                    () -> BackupSchemaCompatibilityService.compatiblePlan(source, TARGET, null));
        }
    }

    @Test
    void acceptsAMatchingLegacyBaselineWithoutConfusingItWithTheConsolidatedSqlRoot() {
        HistoryRow baseline = new HistoryRow(1, "1", "BASELINE", "<< Flyway Baseline >>", null, true);
        List<HistoryRow> legacyTarget = List.of(baseline, TARGET.get(1));

        var plan = BackupSchemaCompatibilityService.compatiblePlan(List.of(baseline), legacyTarget,
                List.of(new SchemaMigration("1", null)));

        assertEquals("1", plan.sourceVersion());
        assertEquals("2", plan.targetVersion());
        assertThrows(IllegalArgumentException.class,
                () -> BackupSchemaCompatibilityService.compatiblePlan(List.of(baseline), TARGET, null));
    }

    @Test
    void rejectsAManifestThatDoesNotDescribeTheActualDumpHistory() {
        List<HistoryRow> source = TARGET.subList(0, 1);

        assertThrows(IllegalArgumentException.class,
                () -> BackupSchemaCompatibilityService.compatiblePlan(source, TARGET, metadata(TARGET)));
        assertThrows(IllegalArgumentException.class,
                () -> BackupSchemaCompatibilityService.compatiblePlan(source, TARGET,
                        List.of(new SchemaMigration("1", 999))));
    }

    @Test
    void readsPgDumpCopyDataWithoutInterpretingDescriptionsAsSql() throws Exception {
        Path sql = write("history.sql", "-- PostgreSQL database dump\n" + COPY_HEADER
                + copyRow("1", "1", "SELECT pg_sleep(999);", "SQL", "V001__fixture.sql", "101", "t")
                + "\\.\n-- PostgreSQL database dump complete\n");

        assertEquals(List.of(TARGET.get(0)), BackupSchemaCompatibilityService.parseHistoryCopy(sql));
    }

    @Test
    void rejectsAbsentMalformedDuplicateAndUnterminatedCopyHistories() throws Exception {
        String valid = COPY_HEADER + copyRow("1", "1", "fixture", "SQL", "V001__fixture.sql", "101", "t") + "\\.\n";
        List<String> invalid = List.of(
                "SELECT 'COPY public.flyway_schema_history' ;\n",
                COPY_HEADER + "1\t1\tonly-three-columns\n\\.\n",
                COPY_HEADER + copyRow("1", "1", "fixture", "SQL", "V001__fixture.sql", "not-a-checksum", "t") + "\\.\n",
                COPY_HEADER + copyRow("1", "1", "fixture", "SQL", "V001__fixture.sql", "101", "not-a-boolean") + "\\.\n",
                COPY_HEADER + copyRow("1", "1", "fixture", "SQL", "V001__fixture.sql", "101", "t"),
                valid + valid,
                "COPY public.flyway_schema_history FROM PROGRAM 'untrusted-command';\n");

        for (int index = 0; index < invalid.size(); index++) {
            Path sql = write("invalid-" + index + ".sql", invalid.get(index));
            assertThrows(IllegalArgumentException.class,
                    () -> BackupSchemaCompatibilityService.parseHistoryCopy(sql), "Malformed fixture " + index);
        }
    }

    private Path write(String name, String content) throws Exception {
        return Files.writeString(directory.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static String copyRow(String rank, String version, String description, String type,
                                  String script, String checksum, String success) {
        return String.join("\t", rank, version, description, type, script, checksum,
                "fixture", "2026-11-25 12:00:00", "1", success) + "\n";
    }

    private static HistoryRow row(int rank, String version, String script, Integer checksum) {
        return new HistoryRow(rank, version, "SQL", script, checksum, true);
    }

    private static List<SchemaMigration> metadata(List<HistoryRow> rows) {
        return rows.stream().map(row -> new SchemaMigration(row.version(), row.checksum())).toList();
    }
}
