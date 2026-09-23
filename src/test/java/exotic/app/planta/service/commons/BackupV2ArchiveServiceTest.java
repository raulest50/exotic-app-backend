package exotic.app.planta.service.commons;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.config.StorageProperties;
import exotic.app.planta.service.commons.BackupV2ArchiveService.DumpFile;
import exotic.app.planta.service.commons.BackupV2ArchiveService.Manifest;
import exotic.app.planta.service.commons.BackupV2ArchiveService.PoeFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackupV2ArchiveServiceTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final byte[] dump = "PGDMP-test-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private final byte[] document = "POE-version-original".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private BackupV2ArchiveService service;

    @BeforeEach
    void setup() {
        StorageProperties storage = mock(StorageProperties.class);
        when(storage.getUPLOAD_DIR()).thenReturn(directory.resolve("storage").toString());
        service = new BackupV2ArchiveService(mock(DataSource.class), mapper, storage);
    }

    @Test
    void apachePoiCanStillWriteAndReadExcelWithTheSharedCompressDependency() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Datos").createRow(0).createCell(0).setCellValue("Exotic");
            workbook.write(output);
        }
        try (XSSFWorkbook restored = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals("Exotic", restored.getSheet("Datos").getRow(0).getCell(0).getStringCellValue());
        }
    }

    @Test
    void extractsEveryVersionAndDatabaseWithoutWritingLiveStorage() throws Exception {
        PoeFile first = poe(10, 1, "a.docx");
        PoeFile second = poe(11, 2, "b.docx");
        Manifest manifest = manifest(List.of(first, second));
        Path zip = archive(manifest, List.of(first, second), document, null, false);
        Path work = Files.createDirectory(directory.resolve("work"));

        Manifest restored = service.extractValidated(zip, work);

        assertEquals(manifest, restored);
        assertArrayEquals(dump, Files.readAllBytes(work.resolve("database.dump")));
        assertArrayEquals(document, Files.readAllBytes(BackupV2ArchiveService.stagedDocument(work, first)));
        assertArrayEquals(document, Files.readAllBytes(BackupV2ArchiveService.stagedDocument(work, second)));
        assertFalse(Files.exists(directory.resolve("storage")));
    }

    @Test
    void allowsAnEmptyPoeInventory() throws Exception {
        Path zip = archive(manifest(List.of()), List.of(), document, null, false);
        assertTrue(service.extractValidated(zip, Files.createDirectory(directory.resolve("work"))).poes().isEmpty());
    }

    @Test
    void rejectsChangedContentEvenWhenTheZipItselfIsValid() throws Exception {
        PoeFile file = poe(10, 1, "a.docx");
        byte[] changed = document.clone();
        changed[0] ^= 1;
        Path zip = archive(manifest(List.of(file)), List.of(file), changed, null, false);
        assertThrows(IllegalArgumentException.class,
                () -> service.extractValidated(zip, Files.createDirectory(directory.resolve("work"))));
    }

    @Test
    void rejectsMissingAndUnlistedFiles() throws Exception {
        PoeFile file = poe(10, 1, "a.docx");
        Path missing = archive(manifest(List.of(file)), List.of(), document, null, false);
        assertThrows(IllegalArgumentException.class,
                () -> service.extractValidated(missing, Files.createDirectory(directory.resolve("missing"))));
        Path extra = archive(manifest(List.of()), List.of(), document, "../../outside.docx", false);
        assertThrows(IllegalArgumentException.class,
                () -> service.extractValidated(extra, Files.createDirectory(directory.resolve("extra"))));
        assertFalse(Files.exists(directory.resolve("outside.docx")));
    }

    @Test
    void rejectsDuplicateZipNamesAndSymlinkEntries() throws Exception {
        Path duplicate = archive(manifest(List.of()), List.of(), document, "database.dump", false);
        assertThrows(IllegalArgumentException.class,
                () -> service.extractValidated(duplicate, Files.createDirectory(directory.resolve("duplicate"))));
        Path symlink = archive(manifest(List.of()), List.of(), document, "symlink", true);
        assertThrows(IllegalArgumentException.class,
                () -> service.extractValidated(symlink, Files.createDirectory(directory.resolve("symlink-work"))));
    }

    @Test
    void rejectsTraversalAndOversizedFilesInManifest() throws Exception {
        PoeFile traversal = new PoeFile(10, 1, 1, "procesos-produccion/1/documentos/../../outside.docx",
                document.length, sha(document), "LOCAL_DISK");
        assertThrows(IllegalArgumentException.class, () -> BackupV2ArchiveService.validateManifest(manifest(List.of(traversal))));
        PoeFile huge = new PoeFile(10, 1, 1, "procesos-produccion/1/documentos/a.docx",
                BackupV2ArchiveService.MAX_POE_BYTES + 1, sha(document), "LOCAL_DISK");
        assertThrows(IllegalArgumentException.class, () -> BackupV2ArchiveService.validateManifest(manifest(List.of(huge))));
    }

    @Test
    void rejectsAnUnknownPackageVersion() throws Exception {
        Manifest invalid = new Manifest("exotic-backup", 3, "2026-09-22", "public", 17,
                new DumpFile(dump.length, sha(dump)), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> BackupV2ArchiveService.validateManifest(invalid));
    }

    private PoeFile poe(long id, int version, String name) throws Exception {
        return new PoeFile(id, 1, version, "procesos-produccion/1/documentos/" + name,
                document.length, sha(document), "LOCAL_DISK");
    }

    private Manifest manifest(List<PoeFile> files) throws Exception {
        return new Manifest("exotic-backup", 2, "2026-09-22", "public", 17,
                new DumpFile(dump.length, sha(dump)), files, List.of());
    }

    private Path archive(Manifest manifest, List<PoeFile> included, byte[] bytes, String extra, boolean symlink) throws IOException {
        Path path = Files.createTempFile(directory, "backup-", ".zip");
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(path)) {
            entry(zip, "manifest.json", mapper.writeValueAsBytes(manifest), false);
            entry(zip, "database.dump", dump, false);
            for (PoeFile file : included) entry(zip, "files/" + file.storageKey(), bytes, false);
            if (extra != null) entry(zip, extra, document, symlink);
        }
        return path;
    }

    private void entry(ZipArchiveOutputStream zip, String name, byte[] bytes, boolean symlink) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        if (symlink) entry.setUnixMode(0120777);
        zip.putArchiveEntry(entry);
        zip.write(bytes);
        zip.closeArchiveEntry();
    }

    private String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
