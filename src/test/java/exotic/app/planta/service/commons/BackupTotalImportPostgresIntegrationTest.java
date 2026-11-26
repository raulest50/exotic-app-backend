package exotic.app.planta.service.commons;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.config.StorageProperties;
import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import exotic.app.planta.model.commons.dto.importacion.BackupTotalImportJobResponseDTO;
import exotic.app.planta.model.users.User;
import exotic.app.planta.service.commons.ImportedPasswordSanitizationService.PasswordSanitizationResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Real dump/restore/Flyway/Hibernate against a disposable PostgreSQL container.
 * No Spring context, application properties, application schema or external database is loaded.
 * PostgreSQL clients must be on PATH or supplied through backup.import.test.pgDump/pgRestore/psql.
 * An explicitly supplied backup.import.test.hostPort uses host networking for restricted test daemons.
 */
@Tag("backup-import-postgres")
@Testcontainers
class BackupTotalImportPostgresIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = postgresFixture();
    private static final String OWNER = "backup-fixture-user";
    private static final String MIGRATION_FAILURE_VALUE = "restore-causes-migration-failure";

    @TempDir Path directory;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private PgDumpExecutableResolver executables;
    private Path migrations;
    private SessionFactory entityManagerFactory;
    private BackupTotalImportService jobs;
    private DatabaseImportMaintenanceService maintenance;
    private ImportedPasswordSanitizationService sanitization;

    private static PostgreSQLContainer<?> postgresFixture() {
        String image = System.getProperty("backup.import.test.postgresImage", "postgres:18.1");
        String requestedPort = System.getProperty("backup.import.test.hostPort");
        if (requestedPort == null) return new PostgreSQLContainer<>(image);
        final int hostPort;
        try {
            hostPort = Integer.parseInt(requestedPort);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("backup.import.test.hostPort must be an integer from 1024 to 65535");
        }
        if (hostPort < 1024 || hostPort > 65535) {
            throw new IllegalArgumentException("backup.import.test.hostPort must be an integer from 1024 to 65535");
        }
        return new HostNetworkPostgres(image, hostPort)
                .withNetworkMode("host").withExposedPorts()
                .withCommand("postgres", "-p", Integer.toString(hostPort));
    }

    private static final class HostNetworkPostgres extends PostgreSQLContainer<HostNetworkPostgres> {
        private final int hostPort;

        private HostNetworkPostgres(String image, int hostPort) {
            super(image);
            this.hostPort = hostPort;
        }

        @Override public Integer getMappedPort(int originalPort) {
            return originalPort == 5432 ? hostPort : super.getMappedPort(originalPort);
        }
    }

    @BeforeEach
    void createAnIsolatedFixtureWithNoApplicationConfiguration() throws Exception {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        resetFixtureSchema();
        migrations = Files.createDirectory(directory.resolve("migrations"));
        Files.writeString(migrations.resolve("V001__backup_fixture.sql"), """
                CREATE TABLE backup_import_fixture (id BIGINT PRIMARY KEY, name VARCHAR(80) NOT NULL);
                CREATE TABLE proceso_produccion_documento_version (
                    id BIGINT PRIMARY KEY, proceso_id INTEGER NOT NULL, version INTEGER NOT NULL,
                    storage_key VARCHAR(255) NOT NULL, tamano_bytes BIGINT NOT NULL,
                    sha256 VARCHAR(64) NOT NULL, storage_provider VARCHAR(30) NOT NULL
                );
                """);
        Files.writeString(migrations.resolve("V002__backup_fixture_validation.sql"), """
                ALTER TABLE backup_import_fixture ADD COLUMN validation_marker VARCHAR(80);
                ALTER TABLE backup_import_fixture ADD CONSTRAINT backup_fixture_valid_name
                    CHECK (name <> 'restore-causes-migration-failure');
                """);
        executables = mock(PgDumpExecutableResolver.class);
        when(executables.resolveExecutable()).thenReturn(System.getProperty("backup.import.test.pgDump", "pg_dump"));
        when(executables.resolveRestoreExecutable()).thenReturn(System.getProperty("backup.import.test.pgRestore", "pg_restore"));
        when(executables.resolvePsqlExecutable()).thenReturn(System.getProperty("backup.import.test.psql", "psql"));
        sanitization = mock(ImportedPasswordSanitizationService.class);
        when(sanitization.sanitizeImportedUserPasswords()).thenReturn(new PasswordSanitizationResult(0, 0, 0));
        maintenance = new DatabaseImportMaintenanceService(directory.resolve("import-maintenance.marker"), true);
    }

    @AfterEach
    void closeOnlyTheDisposableFixture() {
        if (jobs != null) jobs.shutdown();
        if (entityManagerFactory != null) entityManagerFactory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2"})
    void v1RestoresSameAndOlderSchemasAndNeverReportsReadyBeforeValidation(String sourceVersion) throws Exception {
        Path dump = sourceDump(sourceVersion, "archived-value");
        BackupSchemaCompatibilityService schemas = spy(prepareCurrentDestination());
        CountDownLatch validationEntered = new CountDownLatch(1);
        CountDownLatch allowValidation = new CountDownLatch(1);
        doAnswer(invocation -> {
            validationEntered.countDown();
            if (!allowValidation.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture validation was not released");
            invocation.callRealMethod();
            return null;
        }).when(schemas).validateSchema(any());
        startJobs(schemas);

        try {
            var started = jobs.createJob(owner(), upload(dump, "fixture.dump"));
            assertTrue(validationEntered.await(30, TimeUnit.SECONDS), "The real restore and migration must reach validation");
            var validating = jobs.getJob(OWNER, started.jobId());
            assertEquals("VERIFICANDO", validating.estado());
            assertTrue(maintenance.isBlocked(), "Business access remains blocked while validation is pending");
            verifyNoInteractions(sanitization);

            allowValidation.countDown();
            var result = awaitTerminal(started.jobId());

            assertEquals("LISTO", result.estado(), result.errorCode());
            assertVersion(sourceVersion, result.sourceSchemaVersion());
            assertVersion("2", result.targetSchemaVersion());
            assertVersion("2", result.finalSchemaVersion());
            assertEquals(Integer.valueOf(sourceVersion.equals("1") ? 1 : 0), result.migrationsApplied());
            assertFalse(result.requiresRecovery());
            assertFalse(maintenance.isBlocked());
            assertRestoredAndValidated("archived-value");
            verify(sanitization).sanitizeImportedUserPasswords();
        } finally {
            allowValidation.countDown();
        }
    }

    @Test
    void v2RestoresAnOlderDatabaseAndItsActualPoeBeforeMigrating() throws Exception {
        flyway("1").migrate();
        jdbc.update("INSERT INTO backup_import_fixture (id, name) VALUES (1, ?)", "v2-archived-value");
        Path sourceStorage = Files.createDirectory(directory.resolve("source-storage"));
        String storageKey = "procesos-produccion/1/documentos/fixture.docx";
        Path poe = sourceStorage.resolve(storageKey);
        Files.createDirectories(poe.getParent());
        byte[] contents = "fictitious-poe-version".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(poe, contents);
        jdbc.update("""
                INSERT INTO proceso_produccion_documento_version
                    (id, proceso_id, version, storage_key, tamano_bytes, sha256, storage_provider)
                VALUES (1, 1, 1, ?, ?, ?, 'LOCAL_DISK')
                """, storageKey, contents.length, BackupV2ArchiveService.sha256(poe));
        Path zip = directory.resolve("fixture.zip");
        archiveService(sourceStorage).exportArchive(zip, this::dump);
        BackupSchemaCompatibilityService schemas = prepareCurrentDestination();
        startJobs(schemas);

        var started = jobs.createJob(owner(), upload(zip, "fixture.zip"), true);
        var result = awaitTerminal(started.jobId());

        assertEquals("LISTO", result.estado(), result.errorCode());
        assertVersion("1", result.sourceSchemaVersion());
        assertVersion("2", result.finalSchemaVersion());
        assertEquals(Integer.valueOf(1), result.migrationsApplied());
        assertArrayEquals(contents, Files.readAllBytes(directory.resolve("destination-storage").resolve(storageKey)));
        assertEquals(storageKey, jdbc.queryForObject("SELECT storage_key FROM proceso_produccion_documento_version WHERE id=1", String.class));
        assertRestoredAndValidated("v2-archived-value");
        assertFalse(result.requiresRecovery());
        assertFalse(maintenance.isBlocked());
    }

    @Test
    void aRealRestoreFailureRollsBackTheResetAndNeverPublishesReady() throws Exception {
        flyway("1").migrate();
        jdbc.update("INSERT INTO backup_import_fixture (id, name) VALUES (1, 'archived-value')");
        jdbc.execute("CREATE SCHEMA backup_external_fixture");
        try {
            jdbc.execute("CREATE SEQUENCE backup_external_fixture.required_sequence");
            jdbc.execute("CREATE TABLE restore_dependency_fixture (id BIGINT DEFAULT nextval('backup_external_fixture.required_sequence'))");
            Path dump = directory.resolve("missing-external-dependency.dump");
            dump(dump, null);
            jdbc.execute("DROP SCHEMA backup_external_fixture CASCADE");
            startJobs(prepareCurrentDestination());

            var started = jobs.createJob(owner(), upload(dump, "fixture.dump"));
            var result = awaitTerminal(started.jobId());

            assertEquals("ERROR", result.estado());
            assertTrue(result.requiresRecovery(), "A failed replacement is never reopened automatically");
            assertTrue(maintenance.isBlocked());
            assertEquals("destination-before", jdbc.queryForObject("SELECT name FROM backup_import_fixture WHERE id=1", String.class),
                    "Reset and restore must share a transaction, preserving the destination on failure");
            assertEquals(MigrationVersion.fromVersion("2"), flyway("2").info().current().getVersion());
            verifyNoInteractions(sanitization);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS backup_external_fixture CASCADE");
        }
    }

    @Test
    void aRealPendingMigrationFailureKeepsTheRestoredDatabaseUnavailable() throws Exception {
        Path dump = sourceDump("1", MIGRATION_FAILURE_VALUE);
        startJobs(prepareCurrentDestination());

        var started = jobs.createJob(owner(), upload(dump, "fixture.dump"));
        var result = awaitTerminal(started.jobId());

        assertEquals("ERROR", result.estado());
        assertTrue(result.requiresRecovery());
        assertTrue(maintenance.isBlocked());
        assertEquals(MIGRATION_FAILURE_VALUE, jdbc.queryForObject("SELECT name FROM backup_import_fixture WHERE id=1", String.class));
        assertEquals(MigrationVersion.fromVersion("1"), flyway("2").info().current().getVersion(), "Failed PostgreSQL DDL must not mark V2 successful");
        verifyNoInteractions(sanitization);
    }

    @Test
    void aMatchingHistoryDoesNotHideAPhysicalSchemaValidationFailure() throws Exception {
        flyway("2").migrate();
        jdbc.update("INSERT INTO backup_import_fixture (id, name) VALUES (1, 'archived-value')");
        jdbc.execute("ALTER TABLE backup_import_fixture DROP COLUMN validation_marker");
        Path dump = directory.resolve("missing-mapped-column.dump");
        dump(dump, null);
        startJobs(prepareCurrentDestination());

        var started = jobs.createJob(owner(), upload(dump, "fixture.dump"));
        var result = awaitTerminal(started.jobId());

        assertEquals("ERROR", result.estado());
        assertTrue(result.requiresRecovery());
        assertTrue(maintenance.isBlocked());
        assertEquals(MigrationVersion.fromVersion("2"), flyway("2").info().current().getVersion());
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='backup_import_fixture' AND column_name='validation_marker'
                """, Integer.class));
        verifyNoInteractions(sanitization);
    }

    @Test
    void aRealDumpWithADifferentChecksumIsRejectedWithoutReplacingTheDestination() throws Exception {
        flyway("1").migrate();
        jdbc.update("INSERT INTO backup_import_fixture (id, name) VALUES (1, 'incompatible-archived-value')");
        // Deliberately corrupt only this disposable fixture's metadata to exercise the rejection path.
        assertEquals(1, jdbc.update("UPDATE flyway_schema_history SET checksum = checksum # 1 WHERE installed_rank = 1"));
        Path dump = directory.resolve("incompatible-checksum.dump");
        dump(dump, null);
        startJobs(prepareCurrentDestination());

        var started = jobs.createJob(owner(), upload(dump, "fixture.dump"));
        var result = awaitTerminal(started.jobId());

        assertEquals("ERROR", result.estado());
        assertFalse(result.requiresRecovery());
        assertFalse(maintenance.isBlocked());
        assertRestoredAndValidated("destination-before");
        verifyNoInteractions(sanitization);
    }

    @Test
    void sanitizationFailureDoesNotPublishAValidatedSchemaAsReady() throws Exception {
        Path dump = sourceDump("1", "archived-value");
        startJobs(prepareCurrentDestination());
        when(sanitization.sanitizeImportedUserPasswords()).thenThrow(new IllegalStateException("fictitious sanitization failure"));

        var started = jobs.createJob(owner(), upload(dump, "fixture.dump"));
        var result = awaitTerminal(started.jobId());

        assertEquals("ERROR", result.estado());
        assertEquals("PASSWORD_SANITIZATION_FAILED", result.errorCode());
        assertVersion("2", result.finalSchemaVersion());
        assertRestoredAndValidated("archived-value");
        assertTrue(result.requiresRecovery());
        assertTrue(maintenance.isBlocked());
    }

    private Path sourceDump(String version, String value) throws Exception {
        flyway(version).migrate();
        jdbc.update("INSERT INTO backup_import_fixture (id, name) VALUES (1, ?)", value);
        Path dump = directory.resolve("source-" + version + ".dump");
        dump(dump, null);
        return dump;
    }

    private BackupSchemaCompatibilityService prepareCurrentDestination() {
        resetFixtureSchema();
        Flyway flyway = flyway("2");
        flyway.migrate();
        jdbc.update("INSERT INTO backup_import_fixture (id, name) VALUES (1, 'destination-before')");
        Configuration configuration = new Configuration().addAnnotatedClass(FixtureRecord.class);
        configuration.getProperties().put("hibernate.connection.datasource", dataSource);
        configuration.setProperty("hibernate.hbm2ddl.auto", "validate");
        entityManagerFactory = configuration.buildSessionFactory();
        return new BackupSchemaCompatibilityService(flyway,
                new StaticListableBeanFactory().getBeanProvider(FlywayMigrationStrategy.class), entityManagerFactory, executables);
    }

    private void startJobs(BackupSchemaCompatibilityService schemas) throws Exception {
        DangerousOperationGuard guard = new DangerousOperationGuard(new ApplicationRuntimeEnvironmentResolver(
                new MockEnvironment().withProperty("app.runtime-environment", "staging")));
        BackupV2ImportService v2 = new BackupV2ImportService(archiveService(directory.resolve("destination-storage")),
                executables, guard, dataSource, new ObjectMapper(), POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword(), schemas);
        jobs = new BackupTotalImportService(dataSource, POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(),
                executables, guard, sanitization, v2, schemas, maintenance);
        ReflectionTestUtils.setField(jobs, "importDir", Files.createDirectory(directory.resolve("imports")));
    }

    private BackupV2ArchiveService archiveService(Path storagePath) {
        StorageProperties storage = mock(StorageProperties.class);
        when(storage.getUPLOAD_DIR()).thenReturn(storagePath.toString());
        return new BackupV2ArchiveService(dataSource, new ObjectMapper(), storage);
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + migrations.toAbsolutePath().toString().replace('\\', '/'))
                .target(target).baselineOnMigrate(false).cleanDisabled(true).validateOnMigrate(true)
                .validateMigrationNaming(true).load();
    }

    private void resetFixtureSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
    }

    private void dump(Path path, String snapshot) throws Exception {
        List<String> command = new ArrayList<>(List.of(executables.resolveExecutable(), "--format=custom", "--schema=public",
                "--no-owner", "--no-privileges", "--file=" + path.toAbsolutePath(), "--host=" + POSTGRES.getHost(),
                "--port=" + POSTGRES.getMappedPort(5432), "--username=" + POSTGRES.getUsername(), "--dbname=" + POSTGRES.getDatabaseName()));
        if (snapshot != null) command.add("--snapshot=" + snapshot);
        BackupProcessRunner.run(command, POSTGRES.getPassword(), Duration.ofSeconds(30));
    }

    private MockMultipartFile upload(Path file, String name) throws Exception {
        return new MockMultipartFile("file", name, "application/octet-stream", Files.readAllBytes(file));
    }

    private User owner() {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn(OWNER);
        return user;
    }

    private BackupTotalImportJobResponseDTO awaitTerminal(String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        BackupTotalImportJobResponseDTO job;
        do {
            job = jobs.getJob(OWNER, id);
            if (Set.of("LISTO", "ERROR", "EXPIRADO").contains(job.estado())) return job;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Import fixture did not reach a terminal state: " + job.estado());
    }

    private void assertRestoredAndValidated(String value) {
        assertEquals(value, jdbc.queryForObject("SELECT name FROM backup_import_fixture WHERE id=1", String.class));
        assertEquals(MigrationVersion.fromVersion("2"), flyway("2").info().current().getVersion());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class));
        flyway("2").validate();
        entityManagerFactory.getSchemaManager().validateMappedObjects();
    }

    private void assertVersion(String expected, String actual) {
        assertNotNull(actual);
        assertEquals(MigrationVersion.fromVersion(expected), MigrationVersion.fromVersion(actual));
    }

    @Entity(name = "BackupImportFixture")
    @Table(name = "backup_import_fixture")
    private static class FixtureRecord {
        @Id private Long id;
        @Column(name = "name", length = 80, nullable = false) private String name;
        @Column(name = "validation_marker", length = 80) private String validationMarker;
    }
}
