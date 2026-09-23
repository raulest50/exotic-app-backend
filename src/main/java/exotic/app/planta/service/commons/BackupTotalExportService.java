package exotic.app.planta.service.commons;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.commons.dto.exportacion.BackupTotalJobResponseDTO;
import exotic.app.planta.model.users.User;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class BackupTotalExportService {

    private static final Duration JOB_TTL = Duration.ofMinutes(15);
    private static final long PG_DUMP_TIMEOUT_MINUTES = 10;
    private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Pattern PG_DUMP_MAJOR_VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.\\d+)?");

    private final DataSource dataSource;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final PgDumpExecutableResolver pgDumpExecutableResolver;
    private final BackupV2ArchiveService backupV2ArchiveService;

    private final ConcurrentMap<String, BackupJob> jobsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> activeJobByUserId = new ConcurrentHashMap<>();
    private final ExecutorService backupExecutor;

    private Path backupDir;

    public BackupTotalExportService(
            DataSource dataSource,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String datasourceUsername,
            @Value("${spring.datasource.password}") String datasourcePassword,
            PgDumpExecutableResolver pgDumpExecutableResolver,
            BackupV2ArchiveService backupV2ArchiveService
    ) {
        this.dataSource = dataSource;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.pgDumpExecutableResolver = pgDumpExecutableResolver;
        this.backupV2ArchiveService = backupV2ArchiveService;
        this.backupExecutor = Executors.newSingleThreadExecutor(new BackupThreadFactory());
    }

    @PostConstruct
    void init() {
        this.backupDir = Path.of(System.getProperty("java.io.tmpdir"), "exotic-backups");
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible crear el directorio temporal de backups", e);
        }
    }

    @PreDestroy
    void shutdown() {
        backupExecutor.shutdownNow();
        cleanupExpiredJobsInternal(true);
    }

    public BackupTotalJobResponseDTO createJob(User user) {
        return createJob(user, false);
    }

    public synchronized BackupTotalJobResponseDTO createJob(User user, boolean v2) {
        cleanupExpiredJobsInternal(false);

        String activeJobId = activeJobByUserId.get(user.getId());
        if (activeJobId != null) {
            BackupJob existingJob = jobsById.get(activeJobId);
            if (existingJob != null && existingJob.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Ya existe un backup total en curso para este usuario."
                );
            }
            activeJobByUserId.remove(user.getId(), activeJobId);
        }

        String jobId = UUID.randomUUID().toString();
        LocalDateTime requestedAt = AppTime.now();
        String dbName = sanitizeForFilename(extractDbName(datasourceUrl));
        String filename = "backup_total_%s_%s_%s%s".formatted(dbName, FILE_TS_FORMAT.format(requestedAt),
                jobId, v2 ? "_v2.zip" : ".dump");
        Path filePath = backupDir.resolve(filename);

        BackupJob job = new BackupJob(jobId, user.getId(), user.getUsername(), filename, filePath, requestedAt, v2);
        jobsById.put(jobId, job);
        activeJobByUserId.put(user.getId(), jobId);

        backupExecutor.submit(() -> runBackup(job));
        return toDto(job);
    }

    public BackupTotalJobResponseDTO getJob(User user, String jobId) {
        BackupJob job = getOwnedJob(user, jobId);
        expireJobIfNeeded(job);
        return toDto(job);
    }

    public DownloadPayload getDownloadPayload(User user, String jobId) {
        BackupJob job = getOwnedJob(user, jobId);
        expireJobIfNeeded(job);

        if (job.status == JobStatus.EXPIRADO) {
            throw new ResponseStatusException(HttpStatus.GONE, "El archivo de backup ya expiró y fue eliminado.");
        }
        if (job.status == JobStatus.ERROR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, job.messageOrDefault("El backup terminó con error y no se puede descargar."));
        }
        if (job.status != JobStatus.LISTO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El backup todavía se está generando.");
        }
        if (!Files.exists(job.filePath)) {
            markJobError(job, "BACKUP_FILE_UNAVAILABLE", "El archivo de backup ya no está disponible en el servidor.", null);
            throw new ResponseStatusException(HttpStatus.GONE, "El archivo de backup ya no está disponible.");
        }

        try {
            return new DownloadPayload(new InputStreamResource(Files.newInputStream(job.filePath)), job.filename, Files.size(job.filePath));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No fue posible abrir el archivo de backup.");
        }
    }

    public void deleteJob(User user, String jobId) {
        BackupJob job = getOwnedJob(user, jobId);
        if (job.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar un backup que aún está en proceso.");
        }
        jobsById.remove(jobId);
        activeJobByUserId.remove(user.getId(), jobId);
        deleteFileQuietly(job.filePath);
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 300000)
    public void cleanupExpiredJobs() {
        cleanupExpiredJobsInternal(false);
    }

    private void runBackup(BackupJob job) {
        job.status = JobStatus.EN_PROCESO;
        LocalDateTime startedAt = AppTime.now();

        try {
            if (job.v2) {
                job.message = "Generando respaldo V2: PostgreSQL y todas las versiones de POE...";
                backupV2ArchiveService.exportArchive(job.filePath, this::writeDump);
            } else {
                writeDump(job.filePath, null);
            }

            job.message = job.v2 ? "Respaldo V2 listo: base de datos y POE. No incluye otros tipos de archivos." : null;
            job.readyAt = AppTime.now();
            job.expiresAt = job.readyAt.plus(JOB_TTL);
            long sizeBytes = Files.size(job.filePath);
            job.status = JobStatus.LISTO;

            log.info("Backup total PostgreSQL completado. jobId={}, user={}, durationSeconds={}, sizeBytes={}",
                    job.jobId,
                    job.ownerUsername,
                    Duration.between(startedAt, job.readyAt).toSeconds(),
                    sizeBytes);
        } catch (PgDumpExecutableResolver.PgDumpResolutionException e) {
            markJobError(job, e.getErrorCode(), e.getMessage(), null);
        } catch (IOException e) {
            markJobError(job, "PG_DUMP_EXECUTION_ERROR", job.v2
                    ? "No fue posible generar el respaldo V2. Revise la disponibilidad de los POE, el espacio y las herramientas PostgreSQL."
                    : "No fue posible ejecutar pg_dump en el servidor.", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markJobError(job, "PG_DUMP_INTERRUPTED", "La generación del backup fue interrumpida.", e.getMessage());
        } catch (IllegalArgumentException e) {
            markJobError(job, "BACKUP_CONFIGURATION_INVALID", e.getMessage(), null);
        } catch (Exception e) {
            markJobError(job, "BACKUP_UNKNOWN_ERROR", "Ocurrió un error inesperado al generar el backup.", e.getMessage());
        } finally {
            activeJobByUserId.remove(job.ownerUserId, job.jobId);
        }
    }

    // Shared by V1 and V2; V2 supplies the snapshot used to read its document inventory.
    void writeDump(Path destination, String snapshot) throws IOException, InterruptedException {
        String executable = pgDumpExecutableResolver.resolveExecutable();
        PgDumpRuntimeValidation validation = validatePgDumpRuntime(executable);
        if (!validation.compatible()) {
            throw new PgDumpExecutableResolver.PgDumpResolutionException(validation.errorCode(), validation.message());
        }
        JdbcConnectionInfo connection = extractConnectionInfo(datasourceUrl);
        List<String> command = new ArrayList<>(List.of(executable, "--format=custom", "--no-password",
                "--file=" + destination.toAbsolutePath(), "--host=" + connection.host(),
                "--port=" + connection.port(), "--username=" + datasourceUsername, "--dbname=" + connection.dbName()));
        if (snapshot != null) command.add("--snapshot=" + snapshot);
        BackupProcessRunner.run(command, datasourcePassword, Duration.ofMinutes(PG_DUMP_TIMEOUT_MINUTES));
        if (!Files.isRegularFile(destination) || Files.size(destination) == 0) {
            throw new IOException("El backup finalizo sin producir un archivo descargable.");
        }
    }

    private PgDumpRuntimeValidation validatePgDumpRuntime(String pgDumpExecutable) {
        String versionOutput;
        try {
            versionOutput = executeCommandAndCaptureOutput(List.of(pgDumpExecutable, "--version"));
        } catch (IOException e) {
            log.error("pg_dump no está disponible en el runtime: {}", e.getMessage());
            return new PgDumpRuntimeValidation(false, "PG_DUMP_NOT_FOUND_IN_RUNTIME",
                    "El servidor no tiene instalado pg_dump. Contacte al administrador del sistema.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PgDumpRuntimeValidation(false, "PG_DUMP_INTERRUPTED",
                    "La validación interna de pg_dump fue interrumpida.");
        }

        Integer pgDumpMajorVersion = parsePgDumpMajorVersion(versionOutput);
        if (pgDumpMajorVersion == null) {
            log.error("No fue posible interpretar la versión de pg_dump: {}", versionOutput);
            return new PgDumpRuntimeValidation(false, "PG_DUMP_VERSION_UNKNOWN",
                    "No fue posible verificar la versión de pg_dump instalada en el servidor.");
        }

        int serverMajorVersion;
        try (var connection = dataSource.getConnection()) {
            serverMajorVersion = connection.getMetaData().getDatabaseMajorVersion();
        } catch (Exception e) {
            log.error("No fue posible consultar la versión de PostgreSQL del servidor", e);
            return new PgDumpRuntimeValidation(false, "DB_SERVER_VERSION_UNAVAILABLE",
                    "No fue posible verificar la compatibilidad del backup con la base de datos.");
        }

        if (pgDumpMajorVersion < serverMajorVersion) {
            String message = "La versión de pg_dump del servidor no es compatible con la versión actual de PostgreSQL.";
            log.error("Incompatibilidad pg_dump/server. pg_dumpMajorVersion={}, serverMajorVersion={}",
                    pgDumpMajorVersion, serverMajorVersion);
            return new PgDumpRuntimeValidation(false, "PG_DUMP_VERSION_INCOMPATIBLE", message);
        }

        return new PgDumpRuntimeValidation(true, null, null);
    }

    private String executeCommandAndCaptureOutput(List<String> command) throws IOException, InterruptedException {
        return BackupProcessRunner.run(command, null, Duration.ofMinutes(1));
    }

    private Integer parsePgDumpMajorVersion(String versionOutput) {
        Matcher matcher = PG_DUMP_MAJOR_VERSION_PATTERN.matcher(versionOutput);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private BackupJob getOwnedJob(User user, String jobId) {
        BackupJob job = jobsById.get(jobId);
        if (job == null || !job.ownerUserId.equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró un backup total con ese identificador.");
        }
        return job;
    }

    private void markJobError(BackupJob job, String errorCode, String message, String technicalDetails) {
        job.errorCode = errorCode;
        job.message = message;
        job.readyAt = null;
        job.expiresAt = AppTime.now().plus(JOB_TTL);
        deleteFileQuietly(job.filePath);
        job.status = JobStatus.ERROR;

        if (technicalDetails == null || technicalDetails.isBlank()) {
            log.error("Backup total PostgreSQL falló. jobId={}, user={}, errorCode={}, message={}",
                    job.jobId, job.ownerUsername, errorCode, message);
        } else {
            log.error("Backup total PostgreSQL falló. jobId={}, user={}, errorCode={}, message={}, details={}",
                    job.jobId, job.ownerUsername, errorCode, message, technicalDetails);
        }
    }

    private void cleanupExpiredJobsInternal(boolean forceDeleteAllTerminalJobs) {
        LocalDateTime now = AppTime.now();
        for (BackupJob job : jobsById.values()) {
            if (job.isActive()) {
                continue;
            }

            boolean shouldDelete = forceDeleteAllTerminalJobs
                    || (job.expiresAt != null && !now.isBefore(job.expiresAt));

            if (!shouldDelete) {
                continue;
            }

            deleteFileQuietly(job.filePath);
            if (!forceDeleteAllTerminalJobs) {
                job.status = JobStatus.EXPIRADO;
                job.message = "El archivo de backup expiró y fue eliminado del servidor.";
            }
            jobsById.remove(job.jobId);
        }
    }

    private void expireJobIfNeeded(BackupJob job) {
        if (job.isActive() || job.expiresAt == null) {
            return;
        }
        if (AppTime.now().isBefore(job.expiresAt)) {
            return;
        }
        deleteFileQuietly(job.filePath);
        job.status = JobStatus.EXPIRADO;
        job.message = "El archivo de backup expiró y fue eliminado del servidor.";
        jobsById.remove(job.jobId);
    }

    private void deleteFileQuietly(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("No fue posible eliminar archivo temporal de backup: {}", filePath, e);
        }
    }

    private BackupTotalJobResponseDTO toDto(BackupJob job) {
        return new BackupTotalJobResponseDTO(
                job.jobId,
                job.status.name(),
                job.filename,
                job.requestedAt,
                job.readyAt,
                job.expiresAt,
                job.errorCode,
                job.message
        );
    }

    private String extractDbName(String jdbcUrl) {
        return extractConnectionInfo(jdbcUrl).dbName();
    }

    private JdbcConnectionInfo extractConnectionInfo(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("La configuración del datasource no define una URL JDBC válida.");
        }

        String uriCandidate = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring(5) : jdbcUrl;
        try {
            URI uri = new URI(uriCandidate);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath();
            String dbName = path != null && path.length() > 1 ? path.substring(1) : null;

            if (host == null || host.isBlank() || dbName == null || dbName.isBlank()) {
                throw new IllegalArgumentException("No fue posible interpretar host y nombre de base de datos desde la URL JDBC actual.");
            }

            return new JdbcConnectionInfo(host, port, dbName);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("La URL JDBC configurada no tiene un formato compatible para backup total.");
        }
    }

    private String sanitizeForFilename(String rawValue) {
        return rawValue.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record DownloadPayload(InputStreamResource resource, String filename, long contentLength) {
    }

    private record JdbcConnectionInfo(String host, int port, String dbName) {
    }

    private record PgDumpRuntimeValidation(boolean compatible, String errorCode, String message) {
    }

    private enum JobStatus {
        PENDIENTE,
        EN_PROCESO,
        LISTO,
        ERROR,
        EXPIRADO
    }

    private static final class BackupJob {
        private final String jobId;
        private final Long ownerUserId;
        private final String ownerUsername;
        private final String filename;
        private final Path filePath;
        private final LocalDateTime requestedAt;
        private final boolean v2;

        private volatile JobStatus status = JobStatus.PENDIENTE;
        private volatile LocalDateTime readyAt;
        private volatile LocalDateTime expiresAt;
        private volatile String errorCode;
        private volatile String message;

        private BackupJob(String jobId, Long ownerUserId, String ownerUsername, String filename, Path filePath, LocalDateTime requestedAt, boolean v2) {
            this.jobId = jobId;
            this.ownerUserId = ownerUserId;
            this.ownerUsername = ownerUsername;
            this.filename = filename;
            this.filePath = filePath;
            this.requestedAt = requestedAt;
            this.v2 = v2;
        }

        private boolean isActive() {
            return status == JobStatus.PENDIENTE || status == JobStatus.EN_PROCESO;
        }

        private String messageOrDefault(String defaultMessage) {
            return message == null || message.isBlank() ? defaultMessage : message;
        }
    }

    private static final class BackupThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "backup-total-export");
            thread.setDaemon(true);
            return thread;
        }
    }

}
