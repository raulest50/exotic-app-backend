package exotic.app.planta.service.commons;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.commons.dto.importacion.BackupTotalImportJobResponseDTO;
import exotic.app.planta.model.users.User;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class BackupTotalImportService {

    private static final Duration JOB_TTL = Duration.ofMinutes(15);
    private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Pattern PG_TOOL_MAJOR_VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.\\d+)?");
    private static final String OPERATION_NAME = "La importacion total de base de datos";

    private final DataSource dataSource;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final PgDumpExecutableResolver pgDumpExecutableResolver;
    private final DangerousOperationGuard dangerousOperationGuard;
    private final ImportedPasswordSanitizationService importedPasswordSanitizationService;
    private final BackupV2ImportService backupV2ImportService;
    private final BackupSchemaCompatibilityService schemas;
    private final DatabaseImportMaintenanceService maintenance;

    private final ConcurrentMap<String, ImportJob> jobsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> activeJobByUsername = new ConcurrentHashMap<>();
    private final AtomicReference<String> activeGlobalJobId = new AtomicReference<>();
    private final ExecutorService importExecutor;

    private Path importDir;

    public BackupTotalImportService(
            DataSource dataSource,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String datasourceUsername,
            @Value("${spring.datasource.password}") String datasourcePassword,
            PgDumpExecutableResolver pgDumpExecutableResolver,
            DangerousOperationGuard dangerousOperationGuard,
            ImportedPasswordSanitizationService importedPasswordSanitizationService,
            BackupV2ImportService backupV2ImportService,
            BackupSchemaCompatibilityService schemas,
            DatabaseImportMaintenanceService maintenance
    ) {
        this.dataSource = dataSource;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.pgDumpExecutableResolver = pgDumpExecutableResolver;
        this.dangerousOperationGuard = dangerousOperationGuard;
        this.importedPasswordSanitizationService = importedPasswordSanitizationService;
        this.backupV2ImportService = backupV2ImportService;
        this.schemas = schemas;
        this.maintenance = maintenance;
        this.importExecutor = Executors.newSingleThreadExecutor(new ImportThreadFactory());
    }

    @PostConstruct
    void init() {
        this.importDir = Path.of(System.getProperty("java.io.tmpdir"), "exotic-backup-imports");
        try {
            Files.createDirectories(importDir);
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible crear el directorio temporal de importaciones", e);
        }
    }

    @PreDestroy
    void shutdown() {
        importExecutor.shutdownNow();
        cleanupExpiredJobsInternal(true);
    }

    public BackupTotalImportJobResponseDTO createJob(User user, MultipartFile file) {
        return createJob(user, file, false);
    }

    public synchronized BackupTotalImportJobResponseDTO createJob(User user, MultipartFile file, boolean v2) {
        dangerousOperationGuard.assertLocalOrStagingOnly(OPERATION_NAME);
        if (maintenance.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La base esta en mantenimiento. Revise la importacion anterior antes de continuar.");
        }
        cleanupExpiredJobsInternal(false);

        String extension = v2 ? ".zip" : ".dump";
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar un archivo " + extension + " para continuar.");
        }

        String originalFilename = sanitizeForFilename(file.getOriginalFilename());
        if (!originalFilename.toLowerCase(java.util.Locale.ROOT).endsWith(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta importacion total solo acepta archivos " + extension + ".");
        }

        if (v2 && file.getSize() > BackupV2ArchiveService.MAX_TOTAL_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El paquete V2 supera el limite de 8 GiB.");
        }

        String existingGlobalJobId = activeGlobalJobId.get();
        if (existingGlobalJobId != null) {
            ImportJob existingGlobalJob = jobsById.get(existingGlobalJobId);
            if (existingGlobalJob != null && existingGlobalJob.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Ya existe una importacion total en curso para esta base de datos."
                );
            }
            activeGlobalJobId.compareAndSet(existingGlobalJobId, null);
        }

        String activeJobId = activeJobByUsername.get(user.getUsername());
        if (activeJobId != null) {
            ImportJob existingJob = jobsById.get(activeJobId);
            if (existingJob != null && existingJob.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Ya existe una importacion total en curso para este usuario."
                );
            }
            activeJobByUsername.remove(user.getUsername(), activeJobId);
        }

        String jobId = UUID.randomUUID().toString();
        LocalDateTime requestedAt = AppTime.now();
        String dbName = sanitizeForFilename(extractDbName(datasourceUrl));
        String filename = "importacion_total_%s_%s_%s_%s".formatted(
                dbName,
                FILE_TS_FORMAT.format(requestedAt),
                jobId,
                originalFilename.length() > 100 ? originalFilename.substring(originalFilename.length() - 100) : originalFilename
        );
        Path dumpFilePath = importDir.resolve(filename);

        try {
            file.transferTo(dumpFilePath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No fue posible almacenar temporalmente el archivo de importacion.");
        }

        if (!activeGlobalJobId.compareAndSet(null, jobId)) {
            deleteFileQuietly(dumpFilePath);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una importacion total en curso para esta base de datos."
            );
        }

        String previousUserJobId = activeJobByUsername.putIfAbsent(user.getUsername(), jobId);
        if (previousUserJobId != null) {
            activeGlobalJobId.compareAndSet(jobId, null);
            deleteFileQuietly(dumpFilePath);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una importacion total en curso para este usuario."
            );
        }

        ImportJob job = new ImportJob(jobId, user.getUsername(), filename, dumpFilePath, requestedAt, v2);
        jobsById.put(jobId, job);

        importExecutor.submit(() -> runImport(job));
        return toDto(job);
    }

    public BackupTotalImportJobResponseDTO getJob(String ownerUsername, String jobId) {
        dangerousOperationGuard.assertLocalOrStagingOnly(OPERATION_NAME);
        ImportJob job = getOwnedJob(ownerUsername, jobId);
        expireJobIfNeeded(job);
        return toDto(job);
    }

    public void deleteJob(String ownerUsername, String jobId) {
        dangerousOperationGuard.assertLocalOrStagingOnly(OPERATION_NAME);
        ImportJob job = getOwnedJob(ownerUsername, jobId);
        if (job.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar una importacion total que aun esta en proceso.");
        }
        jobsById.remove(jobId);
        activeJobByUsername.remove(ownerUsername, jobId);
        activeGlobalJobId.compareAndSet(jobId, null);
        deleteFileQuietly(job.dumpFilePath);
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 300000)
    public void cleanupExpiredJobs() {
        cleanupExpiredJobsInternal(false);
    }

    private void runImport(ImportJob job) {
        LocalDateTime startedAt = AppTime.now();
        job.startedAt = startedAt;
        int poeCount = 0;
        Path work = null;
        AtomicReference<DatabaseImportMaintenanceService.MaintenanceLease> lease = new AtomicReference<>();

        try {
            dangerousOperationGuard.assertLocalOrStagingOnly(OPERATION_NAME);

            job.status = JobStatus.VALIDANDO;
            job.message = "Validando archivo y herramientas de restauracion...";

            if (!Files.exists(job.dumpFilePath) || Files.size(job.dumpFilePath) <= 0) {
                markJobError(job, "IMPORT_FILE_MISSING", "El archivo de importacion no esta disponible o esta vacio.", null);
                return;
            }

            String pgRestoreExecutable = pgDumpExecutableResolver.resolveRestoreExecutable();
            PgRestoreRuntimeValidation validation = validatePgRestoreRuntime(pgRestoreExecutable);
            if (!validation.compatible()) {
                markJobError(job, validation.errorCode(), validation.message(), null);
                return;
            }

            if (job.v2) {
                poeCount = backupV2ImportService.restore(job.dumpFilePath, job.jobId, message -> {
                    job.message = message;
                    if (message.startsWith("Restaurando")) job.status = JobStatus.RESTAURANDO;
                }, plan -> {
                    recordPlan(job, plan);
                    job.message = "Esperando el cierre de operaciones activas antes del reemplazo...";
                    lease.set(enterMaintenance(job));
                }, () -> {
                    job.databaseMutationStarted = true;
                    lease.get().markDatabaseMutation();
                });
            } else {
                work = Files.createTempDirectory(importDir, "import-v1-");
                try (var connection = dataSource.getConnection()) {
                    BackupV2ArchiveService.requirePublicSchema(connection);
                }
                recordPlan(job, schemas.inspect(job.dumpFilePath, work, null));
                String psql = pgDumpExecutableResolver.resolvePsqlExecutable();
                BackupProcessRunner.run(List.of(psql, "--version"), null, Duration.ofMinutes(1));
                Path sql = work.resolve("restore.sql");
                BackupV2ArchiveService.requireSpace(work, 0);
                long maxSqlBytes = Math.min(BackupV2ArchiveService.MAX_TOTAL_BYTES,
                        Files.getFileStore(work).getUsableSpace() - 64L * 1024 * 1024);
                BackupProcessRunner.run(List.of(pgRestoreExecutable, "--clean", "--if-exists", "--no-owner", "--no-privileges",
                        "--file=-", job.dumpFilePath.toAbsolutePath().toString()),
                        null, Duration.ofMinutes(15), sql, maxSqlBytes);
                Path reset = work.resolve("reset.sql");
                Files.writeString(reset, "SET lock_timeout = '30s';\nDROP SCHEMA IF EXISTS public CASCADE;\nCREATE SCHEMA public;\n",
                        StandardCharsets.UTF_8);
                job.message = "Esperando el cierre de operaciones activas antes del reemplazo...";
                lease.set(enterMaintenance(job));
                dangerousOperationGuard.assertLocalOrStagingOnly(OPERATION_NAME);
                job.status = JobStatus.RESTAURANDO;
                job.message = "Restaurando el esquema y los datos en una sola transaccion...";
                JdbcConnectionInfo connectionInfo = extractConnectionInfo(datasourceUrl);
                List<String> command = new ArrayList<>(List.of(psql, "--no-psqlrc", "--no-password", "--quiet",
                        "--single-transaction", "--set=ON_ERROR_STOP=1", "--set=VERBOSITY=terse",
                        "--host=" + connectionInfo.host(), "--port=" + connectionInfo.port(),
                        "--username=" + datasourceUsername, "--dbname=" + connectionInfo.dbName(),
                        "--file=" + reset.toAbsolutePath(), "--file=" + sql.toAbsolutePath()));
                job.databaseMutationStarted = true;
                lease.get().markDatabaseMutation();
                BackupProcessRunner.run(command, datasourcePassword, Duration.ofMinutes(15));
            }

            job.status = JobStatus.MIGRANDO;
            job.message = "Aplicando las migraciones pendientes del backend instalado...";
            job.migrationsApplied = schemas.migrate(job.schemaPlan);
            job.status = JobStatus.VERIFICANDO;
            job.message = "Verificando el historial Flyway y el esquema de las entidades actuales...";
            schemas.validateSchema(job.schemaPlan);
            job.finalSchemaVersion = job.targetSchemaVersion;
            job.status = JobStatus.SANEANDO;
            job.message = "Saneando contrasenas importadas para entorno no productivo...";
            ImportedPasswordSanitizationService.PasswordSanitizationResult sanitizationResult;
            try {
                sanitizationResult = importedPasswordSanitizationService.sanitizeImportedUserPasswords();
            } catch (Exception e) {
                markJobError(
                        job,
                        "PASSWORD_SANITIZATION_FAILED",
                        "No fue posible sanear las credenciales importadas. Se requiere recuperacion manual.",
                        null
                );
                return;
            }

            // No request can enter until both validation and sanitization have finished.
            lease.get().complete();
            lease.get().close();
            job.finishedAt = AppTime.now();
            job.expiresAt = job.finishedAt.plus(JOB_TTL);
            job.message = job.v2
                    ? "Importacion V2 completada y verificada: base de datos y " + poeCount + " versiones de POE. Vuelva a iniciar sesion."
                    : "Importacion total completada y esquema verificado. Vuelva a iniciar sesion.";
            job.status = JobStatus.LISTO;

            log.warn("Importacion total PostgreSQL completada. jobId={}, user={}, durationSeconds={}, sanitizedUsers={}, privilegedUsersSkipped={}, invalidUsersSkipped={}",
                    job.jobId,
                    job.ownerUsername,
                    Duration.between(startedAt, job.finishedAt).toSeconds(),
                    sanitizationResult.sanitizedUsers(),
                    sanitizationResult.privilegedUsersSkipped(),
                    sanitizationResult.invalidUsersSkipped());
        } catch (PgDumpExecutableResolver.PgDumpResolutionException e) {
            markJobError(job, e.getErrorCode(), e.getMessage(), null);
        } catch (UnsupportedOperationException e) {
            markJobError(job, "IMPORT_NOT_AVAILABLE", e.getMessage(), null);
        } catch (IOException e) {
            markJobError(job, "PG_RESTORE_EXECUTION_ERROR", job.v2
                    ? "No se completo la importacion V2. Revise el paquete, el espacio disponible y las herramientas PostgreSQL. Los POE ya copiados pueden permanecer en disco."
                    : "No se completo la restauracion total. Revise las herramientas PostgreSQL y el espacio disponible.", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markJobError(job, "PG_RESTORE_INTERRUPTED", "La restauracion total fue interrumpida.", null);
        } catch (IllegalArgumentException e) {
            markJobError(job, "IMPORT_INCOMPATIBLE", "El respaldo no es compatible con la linea de migraciones activa o con el formato admitido.", null);
        } catch (Exception e) {
            String code = switch (job.status) {
                case MIGRANDO -> "IMPORT_MIGRATION_FAILED";
                case VERIFICANDO -> "IMPORT_SCHEMA_VALIDATION_FAILED";
                default -> "IMPORT_UNKNOWN_ERROR";
            };
            markJobError(job, code, "No se completo la importacion y verificacion del esquema.", null);
        } finally {
            if (lease.get() != null) {
                try { lease.get().close(); }
                catch (IOException exception) {
                    job.requiresRecovery = true;
                    markJobError(job, "IMPORT_MAINTENANCE_ERROR", "No fue posible cerrar el mantenimiento. Se requiere revision manual.", null);
                }
            }
            if (work != null) {
                try { BackupV2ArchiveService.deleteTree(work); }
                catch (IOException exception) { log.warn("No fue posible limpiar el SQL temporal. jobId={}", job.jobId); }
            }
            activeJobByUsername.remove(job.ownerUsername, job.jobId);
            activeGlobalJobId.compareAndSet(job.jobId, null);
            deleteFileQuietly(job.dumpFilePath);
        }
    }

    private void recordPlan(ImportJob job, BackupSchemaCompatibilityService.SchemaPlan plan) {
        job.schemaPlan = plan;
        job.sourceSchemaVersion = plan.sourceVersion();
        job.targetSchemaVersion = plan.targetVersion();
    }

    private DatabaseImportMaintenanceService.MaintenanceLease enterMaintenance(ImportJob job)
            throws IOException, InterruptedException {
        try {
            return maintenance.begin(job.jobId);
        } catch (IOException | InterruptedException | RuntimeException exception) {
            // A failed admission can itself leave a persistent marker (for example an I/O error).
            job.requiresRecovery = maintenance.isBlocked();
            throw exception;
        }
    }

    PgRestoreRuntimeValidation validatePgRestoreRuntime(String pgRestoreExecutable) {
        String versionOutput;
        try {
            versionOutput = BackupProcessRunner.run(List.of(pgRestoreExecutable, "--version"), null, Duration.ofMinutes(1));
        } catch (IOException e) {
            log.error("pg_restore no esta disponible en el runtime: {}", e.getMessage());
            return new PgRestoreRuntimeValidation(false, "PG_RESTORE_NOT_FOUND_IN_RUNTIME",
                    "El servidor no tiene instalado pg_restore. Contacte al administrador del sistema.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PgRestoreRuntimeValidation(false, "PG_RESTORE_INTERRUPTED",
                    "La validacion interna de pg_restore fue interrumpida.");
        }

        Integer pgRestoreMajorVersion = parsePgToolMajorVersion(versionOutput);
        if (pgRestoreMajorVersion == null) {
            log.error("No fue posible interpretar la version de pg_restore: {}", versionOutput);
            return new PgRestoreRuntimeValidation(false, "PG_RESTORE_VERSION_UNKNOWN",
                    "No fue posible verificar la version de pg_restore instalada en el servidor.");
        }

        String psqlVersion;
        try {
            psqlVersion = BackupProcessRunner.run(List.of(pgDumpExecutableResolver.resolvePsqlExecutable(), "--version"),
                    null, Duration.ofMinutes(1));
        } catch (IOException e) {
            return new PgRestoreRuntimeValidation(false, "PSQL_NOT_FOUND_IN_RUNTIME",
                    "El servidor no tiene disponible psql para la importacion total.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PgRestoreRuntimeValidation(false, "PG_RESTORE_INTERRUPTED",
                    "La validacion de las herramientas PostgreSQL fue interrumpida.");
        }
        Integer psqlMajorVersion = parsePgToolMajorVersion(psqlVersion);
        if (psqlMajorVersion == null) {
            return new PgRestoreRuntimeValidation(false, "PSQL_VERSION_UNKNOWN",
                    "No fue posible verificar la version de psql instalada en el servidor.");
        }

        int serverMajorVersion;
        try (var connection = dataSource.getConnection()) {
            serverMajorVersion = connection.getMetaData().getDatabaseMajorVersion();
        } catch (Exception e) {
            log.error("No fue posible consultar la version de PostgreSQL del servidor", e);
            return new PgRestoreRuntimeValidation(false, "DB_SERVER_VERSION_UNAVAILABLE",
                    "No fue posible verificar la compatibilidad de la restauracion con la base de datos.");
        }

        if (pgRestoreMajorVersion != serverMajorVersion) {
            String message = "La importacion requiere pg_restore de la misma version mayor que el servidor PostgreSQL.";
            log.error("Incompatibilidad pg_restore/server. pgRestoreMajorVersion={}, serverMajorVersion={}",
                    pgRestoreMajorVersion, serverMajorVersion);
            return new PgRestoreRuntimeValidation(false, "PG_RESTORE_VERSION_INCOMPATIBLE", message);
        }
        if (psqlMajorVersion != serverMajorVersion) {
            return new PgRestoreRuntimeValidation(false, "PSQL_VERSION_INCOMPATIBLE",
                    "La importacion requiere psql de la misma version mayor que el servidor PostgreSQL.");
        }

        return new PgRestoreRuntimeValidation(true, null, null);
    }

    private Integer parsePgToolMajorVersion(String versionOutput) {
        Matcher matcher = PG_TOOL_MAJOR_VERSION_PATTERN.matcher(versionOutput);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private ImportJob getOwnedJob(String ownerUsername, String jobId) {
        ImportJob job = jobsById.get(jobId);
        if (job == null || !job.ownerUsername.equals(ownerUsername)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro una importacion total con ese identificador.");
        }
        return job;
    }

    private void markJobError(ImportJob job, String errorCode, String message, String technicalDetails) {
        job.requiresRecovery = job.requiresRecovery || job.databaseMutationStarted;
        job.errorCode = errorCode;
        job.message = job.requiresRecovery
                ? message + " La base permanece en mantenimiento; requiere recuperacion y revision manual."
                : message;
        job.finishedAt = AppTime.now();
        job.expiresAt = job.finishedAt.plus(JOB_TTL);
        job.status = JobStatus.ERROR;

        // Subprocess/SQL exception text can contain restored data. Log only the diagnostic category.
        log.error("Importacion total PostgreSQL fallo. jobId={}, errorCode={}, requiresRecovery={}",
                job.jobId, errorCode, job.requiresRecovery);
    }

    private void cleanupExpiredJobsInternal(boolean forceDeleteAllTerminalJobs) {
        LocalDateTime now = AppTime.now();
        for (ImportJob job : jobsById.values()) {
            if (job.isActive()) {
                continue;
            }

            boolean shouldDelete = forceDeleteAllTerminalJobs
                    || (job.expiresAt != null && !now.isBefore(job.expiresAt));

            if (!shouldDelete) {
                continue;
            }

            deleteFileQuietly(job.dumpFilePath);
            if (!forceDeleteAllTerminalJobs) {
                job.status = JobStatus.EXPIRADO;
                job.message = "El resultado de la importacion expiro y fue limpiado del servidor.";
            }
            jobsById.remove(job.jobId);
        }
    }

    private void expireJobIfNeeded(ImportJob job) {
        if (job.isActive() || job.expiresAt == null) {
            return;
        }
        if (AppTime.now().isBefore(job.expiresAt)) {
            return;
        }
        deleteFileQuietly(job.dumpFilePath);
        job.status = JobStatus.EXPIRADO;
        job.message = "El resultado de la importacion expiro y fue limpiado del servidor.";
        jobsById.remove(job.jobId);
    }

    private void deleteFileQuietly(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("No fue posible eliminar archivo temporal de importacion: {}", filePath, e);
        }
    }

    private BackupTotalImportJobResponseDTO toDto(ImportJob job) {
        return new BackupTotalImportJobResponseDTO(
                job.jobId,
                job.status.name(),
                job.filename,
                job.requestedAt,
                job.startedAt,
                job.finishedAt,
                job.expiresAt,
                job.errorCode,
                job.message,
                job.sourceSchemaVersion,
                job.targetSchemaVersion,
                job.finalSchemaVersion,
                job.migrationsApplied,
                job.requiresRecovery
        );
    }

    private String extractDbName(String jdbcUrl) {
        return extractConnectionInfo(jdbcUrl).dbName();
    }

    private JdbcConnectionInfo extractConnectionInfo(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("La configuracion del datasource no define una URL JDBC valida.");
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
            throw new IllegalArgumentException("La URL JDBC configurada no tiene un formato compatible para importacion total.");
        }
    }

    private String sanitizeForFilename(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "backup_total.dump";
        }
        return rawValue.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private record JdbcConnectionInfo(String host, int port, String dbName) {
    }

    record PgRestoreRuntimeValidation(boolean compatible, String errorCode, String message) {
    }

    private enum JobStatus {
        PENDIENTE,
        VALIDANDO,
        PURGANDO,
        RESTAURANDO,
        MIGRANDO,
        VERIFICANDO,
        SANEANDO,
        LISTO,
        ERROR,
        EXPIRADO
    }

    private static final class ImportJob {
        private final String jobId;
        private final String ownerUsername;
        private final String filename;
        private final Path dumpFilePath;
        private final LocalDateTime requestedAt;
        private final boolean v2;

        private volatile JobStatus status = JobStatus.PENDIENTE;
        private volatile LocalDateTime startedAt;
        private volatile LocalDateTime finishedAt;
        private volatile LocalDateTime expiresAt;
        private volatile String errorCode;
        private volatile String message;
        private volatile String sourceSchemaVersion;
        private volatile String targetSchemaVersion;
        private volatile String finalSchemaVersion;
        private volatile Integer migrationsApplied;
        private volatile boolean requiresRecovery;
        private boolean databaseMutationStarted;
        private BackupSchemaCompatibilityService.SchemaPlan schemaPlan;

        private ImportJob(String jobId, String ownerUsername, String filename, Path dumpFilePath, LocalDateTime requestedAt, boolean v2) {
            this.jobId = jobId;
            this.ownerUsername = ownerUsername;
            this.filename = filename;
            this.dumpFilePath = dumpFilePath;
            this.requestedAt = requestedAt;
            this.v2 = v2;
        }

        private boolean isActive() {
            return status == JobStatus.PENDIENTE
                    || status == JobStatus.VALIDANDO
                    || status == JobStatus.PURGANDO
                    || status == JobStatus.RESTAURANDO
                    || status == JobStatus.MIGRANDO
                    || status == JobStatus.VERIFICANDO
                    || status == JobStatus.SANEANDO;
        }
    }

    private static final class ImportThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "backup-total-import");
            thread.setDaemon(true);
            return thread;
        }
    }

}
