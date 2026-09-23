package exotic.app.planta.service.commons;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.config.AppTime;
import exotic.app.planta.config.StorageProperties;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** V2 currently includes the database and every referenced POE version, including retired versions. */
@Service
public class BackupV2ArchiveService {
    static final long MAX_TOTAL_BYTES = 8L * 1024 * 1024 * 1024;
    static final long MAX_POE_BYTES = 32L * 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 16 * 1024 * 1024;
    private static final int MAX_DOCUMENTS = 100_000;
    static final String DOCUMENT_SELECT = """
            SELECT id, proceso_id, version, storage_key, tamano_bytes, lower(sha256) AS sha256, storage_provider
            FROM public.proceso_produccion_documento_version ORDER BY id
            """;
    static final String DOCUMENT_JSON_SELECT = """
            SELECT coalesce(jsonb_agg(jsonb_build_object(
                'id', id, 'procesoId', proceso_id, 'version', version,
                'storageKey', storage_key, 'sizeBytes', tamano_bytes,
                'sha256', lower(sha256), 'storageProvider', storage_provider
            ) ORDER BY id), '[]'::jsonb) FROM public.proceso_produccion_documento_version
            """;
    static final String MIGRATION_JSON_SELECT = """
            SELECT coalesce(jsonb_agg(jsonb_build_object('version', version, 'checksum', checksum)
                ORDER BY version), '[]'::jsonb)
            FROM public.flyway_schema_history WHERE success AND version IS NOT NULL
            """;

    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final Path storageRoot;

    public BackupV2ArchiveService(DataSource dataSource, ObjectMapper mapper, StorageProperties storage) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.storageRoot = Path.of(storage.getUPLOAD_DIR()).toAbsolutePath().normalize();
    }

    public void exportArchive(Path archive, DumpWriter writer) throws Exception {
        Path work = Files.createTempDirectory(archive.getParent(), "backup-v2-");
        try {
            Path dump = work.resolve("database.dump");
            List<PoeFile> documents;
            List<SchemaMigration> migrations;
            int postgresMajor;
            // pg_dump and the POE inventory must observe the same committed database snapshot.
            try (Connection connection = dataSource.getConnection()) {
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setReadOnly(true);
                connection.setAutoCommit(false);
                requirePublicSchema(connection);
                postgresMajor = connection.getMetaData().getDatabaseMajorVersion();
                documents = readDocuments(connection);
                migrations = readMigrations(connection);
                validateDocuments(documents);
                String snapshot;
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("select pg_export_snapshot()")) {
                    result.next();
                    snapshot = result.getString(1);
                }
                writer.write(dump, snapshot);
                connection.rollback();
            }
            long dumpSize = Files.size(dump);
            Manifest manifest = new Manifest("exotic-backup", 2, AppTime.now().toString(), "public",
                    postgresMajor, new DumpFile(dumpSize, sha256(dump)), documents, migrations);
            validateManifest(manifest);
            byte[] json = mapper.writeValueAsBytes(manifest);
            if (json.length > MAX_MANIFEST_BYTES) throw invalid("El inventario de POE excede el limite permitido.");
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(archive)) {
                zip.setEncoding("UTF-8");
                zip.setLevel(1);
                zip.putArchiveEntry(new ZipArchiveEntry("manifest.json"));
                zip.write(json);
                zip.closeArchiveEntry();
                writeEntry(zip, "database.dump", dump, dumpSize, manifest.database().sha256());
                for (PoeFile document : documents) {
                    Path source = resolveDocument(document);
                    if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                        throw invalid("Falta el archivo del POE version ID " + document.id()
                                + ". No se genero un respaldo V2 completo.");
                    }
                    writeEntry(zip, entryName(document), source, document.sizeBytes(), document.sha256());
                }
            }
            if (Files.size(archive) > MAX_TOTAL_BYTES) throw invalid("El ZIP V2 supera el limite permitido de 8 GiB.");
        } finally {
            deleteTree(work);
        }
    }

    /** Extracts only declared entries, with bounded sizes and no use of ZIP paths as destination paths. */
    public Manifest extractValidated(Path archive, Path destination) throws IOException {
        try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
            Map<String, ZipArchiveEntry> entries = new LinkedHashMap<>();
            var enumeration = zip.getEntries();
            while (enumeration.hasMoreElements()) {
                ZipArchiveEntry entry = enumeration.nextElement();
                int type = entry.getUnixMode() & 0170000;
                if (entries.size() >= MAX_DOCUMENTS + 2 || entry.isDirectory() || entry.isUnixSymlink()
                        || (type != 0 && type != 0100000) || !zip.canReadEntryData(entry)
                        || entries.putIfAbsent(entry.getName(), entry) != null) {
                    throw invalid("El ZIP contiene entradas duplicadas, incompatibles o no permitidas.");
                }
            }
            ZipArchiveEntry manifestEntry = entries.get("manifest.json");
            if (manifestEntry == null || manifestEntry.getSize() <= 0 || manifestEntry.getSize() > MAX_MANIFEST_BYTES) {
                throw invalid("El archivo no contiene un inventario V2 valido.");
            }
            Manifest manifest;
            try (InputStream input = zip.getInputStream(manifestEntry)) {
                byte[] json = input.readNBytes(MAX_MANIFEST_BYTES + 1);
                if (json.length > MAX_MANIFEST_BYTES) throw invalid("El inventario excede el limite permitido.");
                manifest = mapper.readValue(json, Manifest.class);
            }
            validateManifest(manifest);
            Set<String> expected = new HashSet<>(List.of("manifest.json", "database.dump"));
            manifest.poes().forEach(document -> expected.add(entryName(document)));
            if (!entries.keySet().equals(expected)) {
                throw invalid("El paquete contiene archivos faltantes o ajenos a su inventario V2.");
            }
            long required = manifest.database().sizeBytes()
                    + manifest.poes().stream().mapToLong(PoeFile::sizeBytes).sum();
            requireSpace(destination, required);
            extractEntry(zip, entries.get("database.dump"), destination.resolve("database.dump"),
                    manifest.database().sizeBytes(), manifest.database().sha256());
            for (PoeFile document : manifest.poes()) {
                extractEntry(zip, entries.get(entryName(document)), stagedDocument(destination, document),
                        document.sizeBytes(), document.sha256());
            }
            return manifest;
        }
    }

    static List<PoeFile> readDocuments(Connection connection) throws SQLException {
        List<PoeFile> result = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(DOCUMENT_SELECT)) {
            while (rows.next()) {
                result.add(new PoeFile(rows.getLong("id"), rows.getInt("proceso_id"), rows.getInt("version"),
                        rows.getString("storage_key"), rows.getLong("tamano_bytes"), rows.getString("sha256"),
                        rows.getString("storage_provider")));
            }
        }
        return result;
    }

    static void requirePublicSchema(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("select current_schema()")) {
            if (!result.next() || !"public".equals(result.getString(1))) {
                throw invalid("La V2 requiere el esquema public utilizado por la aplicacion.");
            }
        }
    }

    static List<SchemaMigration> readMigrations(Connection connection) throws SQLException {
        List<SchemaMigration> result = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT version, checksum FROM public.flyway_schema_history
                WHERE success AND version IS NOT NULL ORDER BY version
                """)) {
            while (rows.next()) result.add(new SchemaMigration(rows.getString(1), (Integer) rows.getObject(2)));
        }
        return result;
    }

    Path resolveDocument(PoeFile document) throws IOException {
        validateDocument(document);
        return resolveWithoutSymlinks(storageRoot, document.storageKey());
    }

    static Path resolveWithoutSymlinks(Path root, String relative) throws IOException {
        Path destination = root.resolve(relative).normalize();
        if (!destination.startsWith(root) || destination.equals(root)) throw invalid("Ruta de archivo no permitida.");
        // Check all ancestors, including the storage root; normalization alone does not reject symlinks.
        for (Path current = destination; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw invalid("No se permiten enlaces simbolicos en las rutas del respaldo.");
        }
        return destination;
    }

    Path storageRoot() { return storageRoot; }

    static Path stagedDocument(Path work, PoeFile document) {
        return work.resolve("poes").resolve(document.id() + ".bin");
    }

    private static String entryName(PoeFile document) { return "files/" + document.storageKey(); }

    static void validateManifest(Manifest manifest) {
        if (manifest == null || !"exotic-backup".equals(manifest.format()) || manifest.version() != 2
                || !"public".equals(manifest.schema()) || manifest.postgresMajor() < 10
                || manifest.database() == null || manifest.database().sizeBytes() <= 0
                || manifest.database().sizeBytes() > MAX_TOTAL_BYTES || !validSha(manifest.database().sha256())) {
            throw invalid("El paquete no corresponde a un respaldo V2 compatible.");
        }
        validateDocuments(manifest.poes());
        if (manifest.migrations() == null) throw invalid("Falta la informacion de compatibilidad de esquema.");
        Set<String> versions = new HashSet<>();
        for (SchemaMigration migration : manifest.migrations()) {
            if (migration == null || migration.version() == null || migration.version().isBlank()
                    || !versions.add(migration.version())) throw invalid("El historial de esquema no es valido.");
        }
        long total = manifest.database().sizeBytes();
        for (PoeFile document : manifest.poes()) {
            total += document.sizeBytes();
            if (total > MAX_TOTAL_BYTES) throw invalid("El respaldo descomprimido supera el limite de 8 GiB.");
        }
    }

    private static void validateDocuments(List<PoeFile> documents) {
        if (documents == null || documents.size() > MAX_DOCUMENTS) throw invalid("Inventario de POE invalido.");
        Set<Long> ids = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (PoeFile document : documents) {
            validateDocument(document);
            if (!ids.add(document.id()) || !keys.add(document.storageKey())) throw invalid("Hay POE duplicados en el inventario.");
        }
    }

    private static void validateDocument(PoeFile document) {
        if (document == null || document.id() <= 0 || document.procesoId() <= 0 || document.version() <= 0
                || document.sizeBytes() <= 0 || document.sizeBytes() > MAX_POE_BYTES
                || !validSha(document.sha256()) || !"LOCAL_DISK".equals(document.storageProvider())
                || document.storageKey() == null
                || !document.storageKey().matches("procesos-produccion/" + document.procesoId()
                        + "/documentos/[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+")) {
            throw invalid("El inventario contiene una version de POE invalida o no compatible.");
        }
    }

    private static boolean validSha(String sha) { return sha != null && sha.matches("[0-9a-f]{64}"); }

    private static void writeEntry(ZipArchiveOutputStream zip, String name, Path source, long size, String sha)
            throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setSize(size);
        zip.putArchiveEntry(entry);
        try (InputStream input = Files.newInputStream(source)) { copyVerified(input, zip, size, sha, name); }
        zip.closeArchiveEntry();
    }

    private static void extractEntry(ZipFile zip, ZipArchiveEntry entry, Path target, long size, String sha)
            throws IOException {
        if (entry.getSize() != size) throw invalid("El tamano del archivo no coincide con el inventario: " + entry.getName());
        Files.createDirectories(target.getParent());
        try (InputStream input = zip.getInputStream(entry); OutputStream output = Files.newOutputStream(target)) {
            copyVerified(input, output, size, sha, entry.getName());
        }
    }

    private static void copyVerified(InputStream input, OutputStream output, long expectedSize, String expectedSha, String name)
            throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[64 * 1024];
        long count = 0;
        int length;
        while ((length = input.read(buffer)) != -1) {
            count += length;
            if (count > expectedSize) throw invalid("El archivo excede el tamano declarado: " + name);
            digest.update(buffer, 0, length);
            output.write(buffer, 0, length);
        }
        if (count != expectedSize || !HexFormat.of().formatHex(digest.digest()).equals(expectedSha)) {
            throw invalid("El contenido no coincide con la version registrada: " + name);
        }
    }

    static String sha256(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static void requireSpace(Path directory, long bytes) throws IOException {
        if (Files.getFileStore(directory).getUsableSpace() < bytes + 64L * 1024 * 1024) {
            throw invalid("No hay suficiente espacio libre para preparar el respaldo.");
        }
    }

    static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }

    @FunctionalInterface
    public interface DumpWriter { void write(Path path, String snapshot) throws Exception; }

    public record Manifest(String format, int version, String createdAt, String schema, int postgresMajor,
                           DumpFile database, List<PoeFile> poes, List<SchemaMigration> migrations) { }
    public record DumpFile(long sizeBytes, String sha256) { }
    public record SchemaMigration(String version, Integer checksum) { }
    public record PoeFile(long id, int procesoId, int version, String storageKey, long sizeBytes,
                          String sha256, String storageProvider) { }
}
