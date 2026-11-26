package exotic.app.planta.service.commons;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.config.StorageProperties;
import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import exotic.app.planta.service.commons.BackupV2ArchiveService.DumpFile;
import exotic.app.planta.service.commons.BackupV2ArchiveService.Manifest;
import exotic.app.planta.service.commons.BackupV2ArchiveService.PoeFile;
import exotic.app.planta.service.commons.BackupV2ArchiveService.SchemaMigration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackupV2ImportServiceTest {
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({
            "18,18,18,true,OK",
            "17,18,18,false,PG_RESTORE_VERSION_INCOMPATIBLE",
            "19,18,18,false,PG_RESTORE_VERSION_INCOMPATIBLE",
            "18,17,18,false,PSQL_VERSION_INCOMPATIBLE"
    })
    void requiresBothRestoreToolsToMatchTheServerMajorVersion(int restoreMajor, int psqlMajor, int serverMajor,
                                                             boolean compatible, String expectedCode) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseMajorVersion()).thenReturn(serverMajor);
        PgDumpExecutableResolver executables = mock(PgDumpExecutableResolver.class);
        when(executables.resolvePsqlExecutable()).thenReturn("fixture-psql");
        BackupTotalImportService jobs = new BackupTotalImportService(dataSource, "jdbc:postgresql://localhost/test",
                "test", "", executables, mock(DangerousOperationGuard.class),
                mock(ImportedPasswordSanitizationService.class), mock(BackupV2ImportService.class),
                mock(BackupSchemaCompatibilityService.class), mock(DatabaseImportMaintenanceService.class));

        try (MockedStatic<BackupProcessRunner> tools = mockStatic(BackupProcessRunner.class)) {
            tools.when(() -> BackupProcessRunner.run(List.of("fixture-pg-restore", "--version"), null, Duration.ofMinutes(1)))
                    .thenReturn("pg_restore (PostgreSQL) " + restoreMajor + ".6");
            tools.when(() -> BackupProcessRunner.run(List.of("fixture-psql", "--version"), null, Duration.ofMinutes(1)))
                    .thenReturn("psql (PostgreSQL) " + psqlMajor + ".6");

            var validation = jobs.validatePgRestoreRuntime("fixture-pg-restore");

            assertEquals(compatible, validation.compatible());
            if (compatible) assertNull(validation.errorCode());
            else assertEquals(expectedCode, validation.errorCode());
            verify(connection).close();
        } finally {
            jobs.shutdown();
        }
    }

    @Test
    void blocksTheWorkerBeforeReadingTheArchiveInProduction() {
        DangerousOperationGuard guard = productionGuard();
        BackupV2ArchiveService archives = mock(BackupV2ArchiveService.class);
        DataSource dataSource = mock(DataSource.class);
        BackupV2ImportService service = service(archives, guard, dataSource);

        assertThrows(UnsupportedOperationException.class,
                () -> service.restore(directory.resolve("not-read.zip"), "job", message -> { }, plan -> { }, () -> { }));
        verifyNoInteractions(archives, dataSource);
    }

    @Test
    void rejectsAStorageKeyThatCurrentlyReferencesDifferentContentBeforeWriting() throws Exception {
        BackupV2ArchiveService archives = archives();
        BackupV2ImportService service = service(archives, mock(DangerousOperationGuard.class), mock(DataSource.class));
        PoeFile incoming = poe("a".repeat(64));
        PoeFile current = poe("b".repeat(64));

        assertThrows(IllegalArgumentException.class, () -> service.validateDestinations(List.of(incoming), List.of(current)));
        assertFalse(Files.exists(directory.resolve("storage")));
    }

    @Test
    void acceptsMissingFilesWhenTheirCurrentDatabaseReferenceMatches() throws Exception {
        BackupV2ArchiveService archives = archives();
        BackupV2ImportService service = service(archives, mock(DangerousOperationGuard.class), mock(DataSource.class));
        PoeFile incoming = poe("a".repeat(64));
        assertDoesNotThrow(() -> service.validateDestinations(List.of(incoming), List.of(incoming)));
        assertFalse(Files.exists(archives.resolveDocument(incoming)));
    }

    @Test
    void rejectsAConflictingReferenceWithoutChangingTheCurrentPoeFile() throws Exception {
        BackupV2ArchiveService archives = archives();
        BackupV2ImportService service = service(archives, mock(DangerousOperationGuard.class), mock(DataSource.class));
        PoeFile incoming = poe("a".repeat(64));
        PoeFile current = poe("b".repeat(64));
        Path currentFile = archives.resolveDocument(current);
        Files.createDirectories(currentFile.getParent());
        byte[] original = "current-poe".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(currentFile, original);

        assertThrows(IllegalArgumentException.class,
                () -> service.validateDestinations(List.of(incoming), List.of(current)));

        assertArrayEquals(original, Files.readAllBytes(currentFile));
    }

    @Test
    void rejectsIncompatibleHistoryBeforeMaintenanceDatabaseAccessOrLiveFiles() throws Exception {
        BackupV2ArchiveService archives = mock(BackupV2ArchiveService.class);
        DataSource dataSource = mock(DataSource.class);
        PgDumpExecutableResolver executables = mock(PgDumpExecutableResolver.class);
        when(executables.resolveRestoreExecutable()).thenReturn("fixture-pg-restore");
        when(executables.resolvePsqlExecutable()).thenReturn("fixture-psql");
        BackupSchemaCompatibilityService schemas = mock(BackupSchemaCompatibilityService.class);
        List<SchemaMigration> incoming = List.of(new SchemaMigration("99", 999));
        Manifest manifest = new Manifest("exotic-backup", 2, "2026-11-25", "public", 18,
                new DumpFile(10, "a".repeat(64)), List.of(), incoming);
        when(archives.extractValidated(any(Path.class), any(Path.class))).thenReturn(manifest);
        when(schemas.inspect(any(Path.class), any(Path.class), eq(incoming)))
                .thenThrow(new IllegalArgumentException("incompatible fixture history"));
        BackupV2ImportService service = new BackupV2ImportService(archives, executables,
                mock(DangerousOperationGuard.class), dataSource, new ObjectMapper(),
                "jdbc:postgresql://localhost/test", "test", "", schemas);
        BackupV2ImportService.ImportPreparation maintenance = mock(BackupV2ImportService.ImportPreparation.class);
        Runnable mutation = mock(Runnable.class);
        Path zip = Files.writeString(directory.resolve("fixture.zip"), "archive handled by the fixture extractor");

        try (MockedStatic<BackupProcessRunner> tools = mockStatic(BackupProcessRunner.class)) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.restore(zip, "fixture-job", message -> { }, maintenance, mutation));
        }

        verify(schemas).inspect(any(Path.class), any(Path.class), eq(incoming));
        verifyNoInteractions(dataSource, maintenance, mutation);
        verify(archives, never()).storageRoot();
        assertFalse(Files.exists(directory.resolve("storage")));
    }

    @Test
    void bothImportEntryPointsRejectProductionBeforeReadingAnUpload() {
        DangerousOperationGuard guard = productionGuard();
        BackupTotalImportService jobs = new BackupTotalImportService(mock(DataSource.class), "jdbc:postgresql://localhost/test",
                "test", "", mock(PgDumpExecutableResolver.class), guard,
                mock(ImportedPasswordSanitizationService.class), mock(BackupV2ImportService.class),
                mock(BackupSchemaCompatibilityService.class), mock(DatabaseImportMaintenanceService.class));
        try {
            assertThrows(UnsupportedOperationException.class, () -> jobs.createJob(null, null));
            assertThrows(UnsupportedOperationException.class, () -> jobs.createJob(null, null, true));
        } finally {
            jobs.shutdown();
        }
    }

    private BackupV2ImportService service(BackupV2ArchiveService archives, DangerousOperationGuard guard, DataSource dataSource) {
        return new BackupV2ImportService(archives, mock(PgDumpExecutableResolver.class), guard, dataSource,
                new ObjectMapper(), "jdbc:postgresql://localhost/test", "test", "", mock(BackupSchemaCompatibilityService.class));
    }

    private BackupV2ArchiveService archives() {
        StorageProperties storage = mock(StorageProperties.class);
        when(storage.getUPLOAD_DIR()).thenReturn(directory.resolve("storage").toString());
        return new BackupV2ArchiveService(mock(DataSource.class), new ObjectMapper(), storage);
    }

    private PoeFile poe(String sha) {
        return new PoeFile(1, 1, 1, "procesos-produccion/1/documentos/test.docx", 10, sha, "LOCAL_DISK");
    }

    private DangerousOperationGuard productionGuard() {
        return new DangerousOperationGuard(new ApplicationRuntimeEnvironmentResolver(
                new MockEnvironment().withProperty("app.runtime-environment", "production")));
    }
}
