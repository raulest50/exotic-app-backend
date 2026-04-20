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

        String rawPath = configuredPgDumpPath == null ? "" : configuredPgDumpPath.trim();
        if (rawPath.isBlank()) {
            throw new PgDumpResolutionException(
                    "PG_DUMP_PATH_REQUIRED_LOCAL",
                    "En entorno local debe configurar app.backup.pg-dump-path con la ruta completa a pg_dump.exe."
            );
        }

        Path pgDumpPath = Path.of(rawPath);
        if (!Files.exists(pgDumpPath)) {
            throw new PgDumpResolutionException(
                    "PG_DUMP_PATH_INVALID",
                    "La ruta configurada para pg_dump no existe en el servidor local."
            );
        }
        if (!Files.isRegularFile(pgDumpPath)) {
            throw new PgDumpResolutionException(
                    "PG_DUMP_PATH_INVALID",
                    "La ruta configurada para pg_dump debe apuntar a un archivo ejecutable, no a un directorio."
            );
        }

        String fileName = pgDumpPath.getFileName() == null ? "" : pgDumpPath.getFileName().toString().toLowerCase();
        if ("psql".equals(fileName) || "psql.exe".equals(fileName)) {
            throw new PgDumpResolutionException(
                    "PG_DUMP_EXECUTABLE_MISMATCH",
                    "La ruta configurada apunta a psql, pero la exportación total requiere pg_dump."
            );
        }
        if (!"pg_dump".equals(fileName) && !"pg_dump.exe".equals(fileName)) {
            throw new PgDumpResolutionException(
                    "PG_DUMP_EXECUTABLE_MISMATCH",
                    "La ruta configurada debe apuntar a pg_dump o pg_dump.exe."
            );
        }

        return pgDumpPath.toAbsolutePath().toString();
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
