package exotic.app.planta.service.commons;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Runs PostgreSQL tools without a shell and with bounded diagnostic output. */
final class BackupProcessRunner {
    private BackupProcessRunner() { }

    static String run(List<String> command, String password, Duration timeout)
            throws IOException, InterruptedException {
        return run(command, password, timeout, null, 0);
    }

    static String run(List<String> command, String password, Duration timeout, Path stdoutFile, long maxOutputBytes)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(stdoutFile == null);
        if (password != null && !password.isBlank()) builder.environment().put("PGPASSWORD", password);
        builder.environment().put("PGCONNECT_TIMEOUT", "15");
        Process process = builder.start();
        process.getOutputStream().close();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream input = stdoutFile == null ? process.getInputStream() : process.getErrorStream()) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    int remaining = 16_384 - output.size();
                    if (remaining > 0) output.write(buffer, 0, Math.min(remaining, length));
                }
            } catch (IOException ignored) {
                // The process may be terminated on timeout or application shutdown.
            }
        }, "backup-process-output");
        reader.setDaemon(true);
        reader.start();
        AtomicReference<IOException> copyFailure = new AtomicReference<>();
        Thread fileWriter = null;
        if (stdoutFile != null) {
            fileWriter = new Thread(() -> {
                try (InputStream input = process.getInputStream(); OutputStream file = Files.newOutputStream(stdoutFile)) {
                    byte[] buffer = new byte[64 * 1024];
                    long total = 0;
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        total += length;
                        if (total > maxOutputBytes) throw new IOException("El SQL descomprimido excede el espacio o limite permitido.");
                        file.write(buffer, 0, length);
                    }
                } catch (IOException exception) {
                    copyFailure.set(exception);
                    process.destroyForcibly();
                }
            }, "backup-process-file");
            fileWriter.setDaemon(true);
            fileWriter.start();
        }
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("La herramienta PostgreSQL excedio el tiempo permitido.");
            }
            reader.join(5000);
            if (fileWriter != null) {
                fileWriter.join(5000);
                if (fileWriter.isAlive()) throw new IOException("No fue posible completar el archivo SQL de restauracion.");
            }
            if (copyFailure.get() != null) throw copyFailure.get();
            if (reader.isAlive()) throw new IOException("No fue posible completar la lectura del proceso PostgreSQL.");
            if (process.exitValue() != 0) {
                // Output can contain restored data. Do not expose it in errors or logs.
                throw new IOException("La herramienta PostgreSQL fallo (codigo " + process.exitValue() + ").");
            }
            return output.toString(StandardCharsets.UTF_8).trim();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }
}
