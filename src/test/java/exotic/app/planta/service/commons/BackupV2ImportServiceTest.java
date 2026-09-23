package exotic.app.planta.service.commons;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.config.StorageProperties;
import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import exotic.app.planta.service.commons.BackupV2ArchiveService.PoeFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackupV2ImportServiceTest {
    @TempDir Path directory;

    @Test
    void blocksTheWorkerBeforeReadingTheArchiveInProduction() {
        DangerousOperationGuard guard = productionGuard();
        BackupV2ArchiveService archives = mock(BackupV2ArchiveService.class);
        DataSource dataSource = mock(DataSource.class);
        BackupV2ImportService service = service(archives, guard, dataSource);

        assertThrows(UnsupportedOperationException.class,
                () -> service.restore(directory.resolve("not-read.zip"), "job", message -> { }));
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
    void bothImportEntryPointsRejectProductionBeforeReadingAnUpload() {
        DangerousOperationGuard guard = productionGuard();
        BackupTotalImportService jobs = new BackupTotalImportService(mock(DataSource.class), "jdbc:postgresql://localhost/test",
                "test", "", mock(PgDumpExecutableResolver.class), guard, mock(DatabasePurgeService.class),
                mock(ImportedPasswordSanitizationService.class), mock(BackupV2ImportService.class));
        try {
            assertThrows(UnsupportedOperationException.class, () -> jobs.createJob(null, null));
            assertThrows(UnsupportedOperationException.class, () -> jobs.createJob(null, null, true));
        } finally {
            jobs.shutdown();
        }
    }

    private BackupV2ImportService service(BackupV2ArchiveService archives, DangerousOperationGuard guard, DataSource dataSource) {
        return new BackupV2ImportService(archives, mock(PgDumpExecutableResolver.class), guard, dataSource,
                new ObjectMapper(), "jdbc:postgresql://localhost/test", "test", "");
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
