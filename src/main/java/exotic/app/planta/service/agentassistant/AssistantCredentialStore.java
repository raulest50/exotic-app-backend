package exotic.app.planta.service.agentassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;

/** App-owned credential profile. Its exclusive OS lock covers its entire runtime lifetime. */
final class AssistantCredentialStore implements AutoCloseable {
    private static final int MAX_BYTES = 262_144;
    private final ObjectMapper mapper;
    private final Path directory;
    private final Path file;
    private FileChannel lockChannel;
    private FileLock lock;

    static Path defaultDirectory() {
        String local = System.getenv("LOCALAPPDATA");
        Path root = local == null || local.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share") : Path.of(local);
        return root.resolve("Exotic").resolve("agent-assistant");
    }

    AssistantCredentialStore(Path directory, ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        this.directory = directory.toAbsolutePath().normalize();
        this.file = this.directory.resolve("siwc-session.json");
        try {
            rejectLinks(this.directory);
            Files.createDirectories(this.directory);
            privatePath(this.directory, true);
            Path lockFile = this.directory.resolve("siwc-session.lock");
            if (Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS)) privatePath(lockFile, false);
            lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            privatePath(lockFile, false);
            try { lock = lockChannel.tryLock(); }
            catch (OverlappingFileLockException ex) { throw new ProfileInUseException(); }
            if (lock == null) throw new ProfileInUseException();
        } catch (IOException | RuntimeException ex) {
            try { close(); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }

    synchronized ObjectNode read() throws IOException {
        requireLock();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return mapper.createObjectNode();
        privatePath(file, false);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES)
            throw new IOException("Invalid private profile");
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) {
            java.util.Arrays.fill(bytes, (byte) 0);
            throw new IOException("Private profile limit");
        }
        try (var parser = mapper.createParser(bytes)) {
            var value = mapper.readTree(parser);
            if (value == null || !value.isObject() || parser.nextToken() != null) throw new IOException("Invalid profile JSON");
            return (ObjectNode) value;
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    synchronized void write(ObjectNode value) throws IOException {
        requireLock();
        byte[] bytes = mapper.writeValueAsBytes(value);
        Path temporary = null;
        try {
            if (bytes.length > MAX_BYTES) throw new IOException("Private profile limit");
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) privatePath(file, false);
            temporary = Files.createTempFile(directory, "siwc-session-", ".tmp");
            privatePath(temporary, false);
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    private void requireLock() throws IOException {
        if (lock == null || !lock.isValid()) throw new IOException("Private profile is not locked");
    }

    private static void rejectLinks(Path path) throws IOException {
        for (Path entry = path; entry != null; entry = entry.getParent()) {
            if (Files.isSymbolicLink(entry)) throw new IOException("Linked profile path is unsupported");
        }
    }

    private static void privatePath(Path path, boolean directory) throws IOException {
        rejectLinks(path);
        var user = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
        if (!Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).equals(user)) throw new IOException("Unexpected profile owner");
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
            return;
        }
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null) throw new IOException("Private permissions unavailable");
        var entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(user)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) entry.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        acl.setAcl(List.of(entry.build()));
    }

    @Override public synchronized void close() throws IOException {
        try { if (lock != null) lock.release(); }
        finally {
            lock = null;
            if (lockChannel != null) lockChannel.close();
            lockChannel = null;
        }
    }

    static final class ProfileInUseException extends IOException {
        ProfileInUseException() { super("El perfil del asistente está en uso. Detén el backend u otra herramienta antes de administrarlo."); }
    }
}
