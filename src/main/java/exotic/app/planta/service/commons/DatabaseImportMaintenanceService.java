package exotic.app.planta.service.commons;

import exotic.app.planta.config.StorageProperties;
import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Coordinates HTTP and scheduled work in the single backend process during a full import. */
@Service
public class DatabaseImportMaintenanceService {
    private final Path marker;
    private final boolean enabled;
    private final ReentrantReadWriteLock workLock = new ReentrantReadWriteLock(true);
    private volatile boolean blocked;

    @Autowired
    public DatabaseImportMaintenanceService(StorageProperties storage,
                                           ApplicationRuntimeEnvironmentResolver environment) {
        this(Path.of(storage.getUPLOAD_DIR()).toAbsolutePath().normalize()
                .resolve(".database-import-maintenance"), environment.isLocalOrStaging());
    }

    DatabaseImportMaintenanceService(Path marker, boolean enabled) {
        this.marker = marker.toAbsolutePath().normalize();
        this.enabled = enabled;
        // A restart must not silently reopen an import whose outcome is unknown.
        this.blocked = enabled && Files.exists(this.marker, LinkOption.NOFOLLOW_LINKS);
    }

    public boolean isBlocked() {
        return enabled && blocked;
    }

    /** Returns null rather than waiting behind an import. The permit must be closed on this thread. */
    public Access tryEnterWork() {
        if (!enabled) return new Access(false);
        if (blocked || !workLock.readLock().tryLock()) return null;
        if (blocked) {
            workLock.readLock().unlock();
            return null;
        }
        return new Access(true);
    }

    public void runScheduled(Runnable work) {
        try (Access access = tryEnterWork()) {
            if (access != null) work.run();
        }
    }

    public MaintenanceLease begin(String jobId) throws IOException, InterruptedException {
        if (!enabled) throw new IllegalStateException("La importacion solo esta habilitada en local y staging.");
        UUID.fromString(jobId);
        synchronized (this) {
            if (blocked) throw new IllegalStateException("La base esta en mantenimiento y requiere revision del operador.");
            // Close admission before waiting for requests already in flight to finish.
            blocked = true;
            try {
                Files.createDirectories(marker.getParent());
                writeMarker(jobId, "PREPARANDO", true);
            } catch (IOException | RuntimeException exception) {
                blocked = Files.exists(marker, LinkOption.NOFOLLOW_LINKS);
                throw exception;
            }
        }
        boolean acquired = false;
        try {
            acquired = workLock.writeLock().tryLock(30, TimeUnit.SECONDS);
            if (!acquired) throw new IOException("No finalizaron las operaciones activas; no se reemplazo la base.");
            return new MaintenanceLease(jobId);
        } finally {
            if (!acquired) {
                Files.delete(marker);
                blocked = false;
            }
        }
    }

    private void writeMarker(String jobId, String phase, boolean create) throws IOException {
        var options = create
                ? new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW}
                : new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};
        // Never follow a marker substituted by a symbolic link.
        java.util.Set<java.nio.file.OpenOption> openOptions = new java.util.HashSet<>(java.util.List.of(options));
        openOptions.add(LinkOption.NOFOLLOW_LINKS);
        try (FileChannel file = FileChannel.open(marker, openOptions)) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode("jobId=" + jobId + "\nphase=" + phase + "\n");
            while (bytes.hasRemaining()) file.write(bytes);
            file.force(true);
        }
    }

    public final class Access implements AutoCloseable {
        private boolean held;
        private Access(boolean held) { this.held = held; }
        @Override public void close() {
            if (held) {
                held = false;
                workLock.readLock().unlock();
            }
        }
    }

    public final class MaintenanceLease implements AutoCloseable {
        private final String jobId;
        private boolean databaseMutation;
        private boolean completed;
        private boolean closed;

        private MaintenanceLease(String jobId) { this.jobId = jobId; }

        public void markDatabaseMutation() {
            // Set this first: even an ambiguous marker write requires operator review.
            databaseMutation = true;
            try { writeMarker(jobId, "REEMPLAZANDO", false); }
            catch (IOException exception) {
                throw new IllegalStateException("No fue posible persistir el estado de mantenimiento.", exception);
            }
        }

        public void complete() throws IOException {
            Files.delete(marker);
            completed = true;
            blocked = false;
        }

        public boolean requiresRecovery() { return databaseMutation && !completed; }

        @Override public void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                if (!databaseMutation && !completed) {
                    Files.delete(marker);
                    blocked = false;
                }
            } finally {
                workLock.writeLock().unlock();
            }
        }
    }
}
