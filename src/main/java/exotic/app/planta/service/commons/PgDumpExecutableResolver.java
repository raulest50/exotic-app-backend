package exotic.app.planta.service.commons;

import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class PgDumpExecutableResolver {

    private final ApplicationRuntimeEnvironmentResolver applicationRuntimeEnvironmentResolver;

    @Value("${app.backup.pg-dump-path:}")
    private String configuredPgDumpPath;

    public String resolveExecutable() {
        if (!applicationRuntimeEnvironmentResolver.isLocal()) {
            return "pg_dump";
        }

        return validateConfiguredLocalPgDumpPath(
                "PG_DUMP_PATH_REQUIRED_LOCAL",
                "PG_DUMP_PATH_INVALID",
                "PG_DUMP_EXECUTABLE_MISMATCH",
                "En entorno local debe configurar app.backup.pg-dump-path con la ruta completa a pg_dump.exe.",
                "La ruta configurada apunta a psql, pero la exportacion total requiere pg_dump.",
                "La ruta configurada debe apuntar a pg_dump o pg_dump.exe."
        ).toAbsolutePath().toString();
    }

    public String resolveRestoreExecutable() {
        if (!applicationRuntimeEnvironmentResolver.isLocal()) {
            return "pg_restore";
        }

        Path dumpExecutable = validateConfiguredLocalPgDumpPath(
                "PG_DUMP_PATH_REQUIRED_LOCAL",
                "PG_DUMP_PATH_INVALID",
                "PG_DUMP_EXECUTABLE_MISMATCH",
                "En entorno local debe configurar app.backup.pg-dump-path con la ruta completa a pg_dump.exe.",
                "La ruta configurada apunta a psql, pero la importacion total requiere encontrar pg_restore junto a pg_dump.",
                "La ruta configurada debe apuntar a pg_dump o pg_dump.exe."
        );

        String dumpFileName = dumpExecutable.getFileName() == null ? "" : dumpExecutable.getFileName().toString();
        String restoreFileName = dumpFileName.toLowerCase().endsWith(".exe") ? "pg_restore.exe" : "pg_restore";
        Path restoreExecutable = dumpExecutable.resolveSibling(restoreFileName);

        if (!Files.exists(restoreExecutable)) {
            throw new PgDumpResolutionException(
                    "PG_RESTORE_PATH_INVALID",
                    "No se encontro pg_restore junto al pg_dump configurado para entorno local."
            );
        }
        if (!Files.isRegularFile(restoreExecutable)) {
            throw new PgDumpResolutionException(
                    "PG_RESTORE_PATH_INVALID",
                    "La ruta derivada para pg_restore debe apuntar a un archivo ejecutable."
            );
        }

        return restoreExecutable.toAbsolutePath().toString();
    }

    private Path validateConfiguredLocalPgDumpPath(
            String requiredErrorCode,
            String invalidPathErrorCode,
            String mismatchErrorCode,
            String requiredMessage,
            String psqlMismatchMessage,
            String expectedMessage
    ) {
        String rawPath = configuredPgDumpPath == null ? "" : configuredPgDumpPath.trim();
        if (rawPath.isBlank()) {
            throw new PgDumpResolutionException(requiredErrorCode, requiredMessage);
        }

        Path configuredPath = Path.of(rawPath);
        if (!Files.exists(configuredPath)) {
            throw new PgDumpResolutionException(
                    invalidPathErrorCode,
                    "La ruta configurada para pg_dump no existe en el servidor local."
            );
        }
        if (!Files.isRegularFile(configuredPath)) {
            throw new PgDumpResolutionException(
                    invalidPathErrorCode,
                    "La ruta configurada para pg_dump debe apuntar a un archivo ejecutable, no a un directorio."
            );
        }

        String fileName = configuredPath.getFileName() == null ? "" : configuredPath.getFileName().toString().toLowerCase();
        if ("psql".equals(fileName) || "psql.exe".equals(fileName)) {
            throw new PgDumpResolutionException(mismatchErrorCode, psqlMismatchMessage);
        }
        if (!"pg_dump".equals(fileName) && !"pg_dump.exe".equals(fileName)) {
            throw new PgDumpResolutionException(mismatchErrorCode, expectedMessage);
        }

        return configuredPath;
    }

    public static final class PgDumpResolutionException extends IllegalStateException {
        private final String errorCode;

        public PgDumpResolutionException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
