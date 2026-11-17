package exotic.app.planta.service.agentassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fake credentials in disposable TempDir only. No profile of the current user is read. */
class AssistantCredentialStoreTest {
    @TempDir Path temporary;
    final ObjectMapper mapper = new ObjectMapper();

    @Test void aPrivateProfileSurvivesClosingAndReopeningWithoutTemporaryFiles() throws Exception {
        Path directory = temporary.resolve("profile");
        var expected = mapper.createObjectNode().put("fake_access", "fixture-only");
        try (var store = new AssistantCredentialStore(directory, mapper)) {
            assertThat(store.read().isEmpty()).isTrue();
            store.write(expected);
        }
        try (var store = new AssistantCredentialStore(directory, mapper)) {
            assertThat(store.read()).isEqualTo(expected);
        }
        try (var files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("siwc-session.json", "siwc-session.lock");
        }
        Path file = directory.resolve("siwc-session.json");
        var posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) assertThat(posix.readAttributes().permissions())
                .containsExactlyInAnyOrderElementsOf(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        else {
            var acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            assertThat(acl).isNotNull();
            var owner = acl.getOwner();
            assertThat(acl.getAcl().stream().filter(entry -> entry.type() == AclEntryType.ALLOW).toList())
                    .allMatch(entry -> entry.principal().equals(owner));
        }
    }

    @Test void onlyOneStoreCanOwnTheProfileAndClosingReleasesIt() throws Exception {
        Path directory = temporary.resolve("profile");
        try (var first = new AssistantCredentialStore(directory, mapper)) {
            assertThatThrownBy(() -> new AssistantCredentialStore(directory, mapper))
                    .isInstanceOf(AssistantCredentialStore.ProfileInUseException.class);
            first.write(mapper.createObjectNode().put("fixture", true));
        }
        try (var next = new AssistantCredentialStore(directory, mapper)) {
            assertThat(next.read().path("fixture").asBoolean()).isTrue();
        }
    }

    @Test void anotherJvmCannotAcquireTheRuntimeLock() throws Exception {
        Path directory = temporary.resolve("profile");
        Path source = temporary.resolve("LockProbe.java");
        Files.writeString(source, """
                import java.nio.channels.*;
                import java.nio.file.*;
                class LockProbe {
                    public static void main(String[] args) throws Exception {
                        try (var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.WRITE)) {
                            var lock = channel.tryLock();
                            System.out.print(lock == null ? "BUSY" : "AVAILABLE");
                            if (lock != null) lock.release();
                        }
                    }
                }
                """);
        try (var first = new AssistantCredentialStore(directory, mapper)) {
            assertThat(probe(source, directory)).isEqualTo("BUSY");
        }
        assertThat(probe(source, directory)).isEqualTo("AVAILABLE");
    }

    private String probe(Path source, Path directory) throws Exception {
        String binary = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", binary).toString(),
                source.toString(), directory.resolve("siwc-session.lock").toString()).redirectErrorStream(true).start();
        try {
            assertThat(child.waitFor(15, TimeUnit.SECONDS)).isTrue();
            String output = new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(child.exitValue()).withFailMessage(output).isZero();
            return output;
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }

    @Test void oversizedReplacementDoesNotDestroyTheLastCompleteProfile() throws Exception {
        try (var store = new AssistantCredentialStore(temporary.resolve("profile"), mapper)) {
            store.write(mapper.createObjectNode().put("version", 1));
            assertThatThrownBy(() -> store.write(mapper.createObjectNode().put("too_large", "x".repeat(262_144))))
                    .isInstanceOf(IOException.class);
            assertThat(store.read().path("version").asInt()).isEqualTo(1);
        }
    }

    @Test void malformedOrTrailingJsonIsRejectedRatherThanPartiallyRead() throws Exception {
        Path directory = temporary.resolve("profile");
        try (var store = new AssistantCredentialStore(directory, mapper)) {
            store.write(mapper.createObjectNode().put("fixture", true));
            Files.writeString(directory.resolve("siwc-session.json"), "{\"fixture\":true} {\"unexpected\":true}");
            assertThatThrownBy(store::read).isInstanceOf(IOException.class);
        }
    }

    @Test void aClosedStoreCannotReadOrWrite() throws Exception {
        var store = new AssistantCredentialStore(temporary.resolve("profile"), mapper);
        store.close();
        assertThatThrownBy(store::read).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> store.write(mapper.createObjectNode())).isInstanceOf(IOException.class);
    }

    @Test void helpDoesNotNeedAProfileNetworkOrSpring() {
        assertThat(AgentAssistantCli.run(new String[]{"help"})).isZero();
        assertThat(AgentAssistantCli.run(new String[]{"unknown-command"})).isEqualTo(2);
    }
}
