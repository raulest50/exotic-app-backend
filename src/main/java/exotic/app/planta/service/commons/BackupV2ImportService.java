package exotic.app.planta.service.commons;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.service.commons.BackupV2ArchiveService.Manifest;
import exotic.app.planta.service.commons.BackupV2ArchiveService.PoeFile;
import exotic.app.planta.service.commons.BackupV2ArchiveService.SchemaMigration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static exotic.app.planta.service.commons.BackupV2ArchiveService.invalid;

@Service
@Slf4j
public class BackupV2ImportService {
    private final BackupV2ArchiveService archives;
    private final PgDumpExecutableResolver executables;
    private final DangerousOperationGuard guard;
    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final String datasourceUrl;
    private final String username;
    private final String password;

    public BackupV2ImportService(BackupV2ArchiveService archives, PgDumpExecutableResolver executables,
                                 DangerousOperationGuard guard, DataSource dataSource, ObjectMapper mapper,
                                 @Value("${spring.datasource.url}") String datasourceUrl,
                                 @Value("${spring.datasource.username}") String username,
                                 @Value("${spring.datasource.password}") String password) {
        this.archives = archives;
        this.executables = executables;
        this.guard = guard;
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.datasourceUrl = datasourceUrl;
        this.username = username;
        this.password = password;
    }

    public int restore(Path zip, String jobId, Consumer<String> progress) throws Exception {
        guard.assertLocalOrStagingOnly("La importacion total V2");
        Path work = Files.createTempDirectory(zip.getParent(), "import-v2-");
        Path recovery = null;
        boolean restored = false;
        try {
            progress.accept("Validando el ZIP, el inventario y el contenido de todos los POE...");
            Manifest manifest = archives.extractValidated(zip, work);
            String restore = executables.resolveRestoreExecutable();
            String psql = executables.resolvePsqlExecutable();
            BackupProcessRunner.run(List.of(psql, "--version"), null, Duration.ofMinutes(1));
            List<PoeFile> current;
            try (var connection = dataSource.getConnection()) {
                BackupV2ArchiveService.requirePublicSchema(connection);
                if (manifest.postgresMajor() != connection.getMetaData().getDatabaseMajorVersion()) {
                    throw invalid("La importacion V2 requiere la misma version mayor de PostgreSQL que el origen.");
                }
                List<SchemaMigration> expectedMigrations = manifest.migrations().stream()
                        .sorted(Comparator.comparing(SchemaMigration::version)).toList();
                if (!expectedMigrations.equals(BackupV2ArchiveService.readMigrations(connection))) {
                    throw invalid("El respaldo y el destino tienen diferentes migraciones Flyway. "
                            + "Sincronice las versiones de la aplicacion antes de importar V2.");
                }
                current = BackupV2ArchiveService.readDocuments(connection);
            }

            // pg_restore parses the entire archive BEFORE any database or live-file replacement.
            Path sql = work.resolve("restore.sql");
            progress.accept("Validando el respaldo PostgreSQL y preparando su restauracion...");
            BackupV2ArchiveService.requireSpace(work, 0);
            long maxSqlBytes = Math.min(BackupV2ArchiveService.MAX_TOTAL_BYTES,
                    Files.getFileStore(work).getUsableSpace() - 64L * 1024 * 1024);
            BackupProcessRunner.run(List.of(restore, "--clean", "--if-exists", "--no-owner", "--no-privileges",
                    "--file=-", work.resolve("database.dump").toAbsolutePath().toString()),
                    null, Duration.ofMinutes(15), sql, maxSqlBytes);
            Path reset = work.resolve("reset.sql");
            Files.writeString(reset, "SET lock_timeout = '30s';\nDROP SCHEMA IF EXISTS public CASCADE;\nCREATE SCHEMA public;\n");
            Path verify = work.resolve("verify.sql");
            Files.writeString(verify, verificationSql(manifest), StandardCharsets.UTF_8);

            // Immutable storage keys let us copy files first without invalidating the current database.
            // A conflicting key/hash is rejected; existing unreferenced files are never deleted.
            validateDestinations(manifest.poes(), current);
            guard.assertLocalOrStagingOnly("La importacion total V2");
            Path root = archives.storageRoot();
            Files.createDirectories(root);
            recovery = BackupV2ArchiveService.resolveWithoutSymlinks(root, ".backup-v2-recovery/" + jobId);
            Files.createDirectories(recovery);
            mapper.writeValue(recovery.resolve("manifest.json").toFile(), manifest);
            progress.accept("Restaurando archivos POE verificados. Se conservan los archivos de otros modulos...");
            copyDocuments(work, recovery, manifest.poes());

            guard.assertLocalOrStagingOnly("La importacion total V2");
            progress.accept("Restaurando PostgreSQL y verificando su correspondencia con los POE...");
            List<String> command = new ArrayList<>(List.of(psql, "--no-psqlrc", "--no-password", "--quiet",
                    "--single-transaction", "--set=ON_ERROR_STOP=1", "--set=VERBOSITY=terse"));
            command.addAll(connectionArguments());
            command.addAll(List.of("--file=" + reset.toAbsolutePath(), "--file=" + sql.toAbsolutePath(),
                    "--file=" + verify.toAbsolutePath()));
            // Reset, restore and manifest verification commit together. No separate DROP SCHEMA.
            BackupProcessRunner.run(command, password, Duration.ofMinutes(15));
            restored = true;
            return manifest.poes().size();
        } finally {
            cleanup(work);
            if (recovery != null) {
                if (restored) cleanup(recovery);
                else log.warn("Importacion V2 no completada. Copias de archivos reemplazados conservadas en {}. "
                        + "Los POE ya copiados son compatibles con las referencias anteriores.", recovery);
            }
        }
    }

    String verificationSql(Manifest manifest) throws IOException {
        List<PoeFile> sorted = manifest.poes().stream().sorted(Comparator.comparingLong(PoeFile::id)).toList();
        String encoded = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(sorted));
        String migrations = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(manifest.migrations().stream()
                .sorted(Comparator.comparing(SchemaMigration::version)).toList()));
        return """
                DO $backup_v2$
                BEGIN
                    IF (%s) IS DISTINCT FROM convert_from(decode('%s', 'base64'), 'UTF8')::jsonb THEN
                        RAISE EXCEPTION 'El respaldo PostgreSQL no corresponde al inventario de POE del paquete V2';
                    END IF;
                    IF (%s) IS DISTINCT FROM convert_from(decode('%s', 'base64'), 'UTF8')::jsonb THEN
                        RAISE EXCEPTION 'El esquema restaurado no corresponde a la version compatible del paquete V2';
                    END IF;
                END;
                $backup_v2$;
                """.formatted(BackupV2ArchiveService.DOCUMENT_JSON_SELECT, encoded,
                        BackupV2ArchiveService.MIGRATION_JSON_SELECT, migrations);
    }

    void validateDestinations(List<PoeFile> documents, List<PoeFile> current) throws IOException {
        Map<String, PoeFile> byKey = new HashMap<>();
        current.forEach(document -> byKey.put(document.storageKey(), document));
        long needed = 0;
        for (PoeFile document : documents) {
            PoeFile existing = byKey.get(document.storageKey());
            if (existing != null && (!document.sha256().equals(existing.sha256())
                    || document.sizeBytes() != existing.sizeBytes())) {
                throw invalid("La ruta del POE ID " + document.id()
                        + " ya corresponde a otro contenido en el destino. No se reemplazo la base.");
            }
            Path target = archives.resolveDocument(document);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw invalid("El destino del POE no es un archivo.");
                needed = Math.addExact(needed, Files.size(target));
            }
            needed = Math.addExact(needed, document.sizeBytes());
        }
        Path root = archives.storageRoot();
        Files.createDirectories(root);
        BackupV2ArchiveService.requireSpace(root, needed);
    }

    private void copyDocuments(Path work, Path recovery, List<PoeFile> documents) throws IOException {
        for (PoeFile document : documents) {
            Path target = archives.resolveDocument(document);
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.size(target) == document.sizeBytes()
                        && BackupV2ArchiveService.sha256(target).equals(document.sha256())) continue;
                Files.copy(target, recovery.resolve(document.id() + ".bin"));
            }
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".restore-v2-", ".tmp");
            try {
                Files.copy(BackupV2ArchiveService.stagedDocument(work, document), temporary, StandardCopyOption.REPLACE_EXISTING);
                // Fail safely on a filesystem that cannot atomically publish the file.
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private List<String> connectionArguments() {
        URI uri = URI.create(datasourceUrl.startsWith("jdbc:") ? datasourceUrl.substring(5) : datasourceUrl);
        if (uri.getHost() == null || uri.getPath() == null || uri.getPath().length() < 2) {
            throw invalid("La configuracion de PostgreSQL no es compatible con la importacion V2.");
        }
        return List.of("--host=" + uri.getHost(), "--port=" + (uri.getPort() > 0 ? uri.getPort() : 5432),
                "--username=" + username, "--dbname=" + uri.getPath().substring(1));
    }

    private void cleanup(Path directory) {
        try { BackupV2ArchiveService.deleteTree(directory); }
        catch (IOException e) { log.warn("No fue posible limpiar el directorio temporal {}", directory, e); }
    }
}
