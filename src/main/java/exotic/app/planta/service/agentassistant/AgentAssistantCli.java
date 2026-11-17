package exotic.app.planta.service.agentassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Desktop;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Development entry point. Does not start Spring, read application properties, or connect to a database. */
public final class AgentAssistantCli {
    private AgentAssistantCli() { }

    public static void main(String[] arguments) {
        int result = run(arguments);
        if (result != 0) System.exit(result);
    }

    static int run(String[] arguments) {
        if (arguments.length == 0 || arguments.length == 1 && "help".equals(arguments[0])) {
            System.out.println("Administración local de Asistente Exotic (backend detenido).\n"
                    + "  login        Autorizar la cuenta del propietario en el navegador oficial.\n"
                    + "  status       Comprobar el perfil local, sin inferencia ni base de datos.\n"
                    + "  models       Consultar los modelos autorizados.\n"
                    + "  probe [slug] Enviar una comprobación breve de texto, sin datos de Exotic.\n"
                    + "  logout       Retirar la sesión local e intentar revocarla.\n"
                    + "Ejemplo: .\\gradlew.bat agentAssistant --args=\"login\"");
            return 0;
        }
        String command = arguments[0];
        if (!List.of("login", "status", "models", "probe", "logout").contains(command)
                || arguments.length > (command.equals("probe") ? 2 : 1)) {
            System.err.println("Comando no válido. Use agentAssistant --args=\"help\".");
            return 2;
        }
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("assistant-cli-local", Map.of("app.runtime-environment", "local")));
        ChatGptGateway gateway = new ChatGptGateway(new ObjectMapper(), new ApplicationRuntimeEnvironmentResolver(environment), true);
        Thread shutdown = new Thread(gateway::shutdown, "assistant-cli-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        try {
            if (command.equals("status")) {
                var status = gateway.status();
                System.out.println(status.connected() ? "Asistente conectado con perfil privado persistente." : status.message());
                return status.connected() ? 0 : 3;
            }
            if (command.equals("login")) {
                if (gateway.status().connected()) {
                    System.out.println("La cuenta ya está configurada. Use probe para verificar inferencia o logout antes de reconectar.");
                    return 0;
                }
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    System.err.println("No hay navegador de escritorio disponible. Ejecute login en una sesión interactiva de este PC.");
                    return 3;
                }
                var authorization = gateway.connect();
                System.out.println("Complete personalmente Sign in with ChatGPT en la ventana oficial. No comparta tokens ni códigos en el chat.");
                // No OAuth URL, tokens or authorization codes are printed or persisted by this CLI.
                Desktop.getDesktop().browse(URI.create(authorization.authorizationUrl()));
                Instant deadline = Instant.now().plusSeconds(610);
                while (gateway.authorizationPending() && Instant.now().isBefore(deadline)) Thread.sleep(250);
                var status = gateway.status();
                System.out.println(status.message());
                return status.connected() ? 0 : 3;
            }
            if (command.equals("logout")) {
                gateway.disconnect();
                System.out.println(gateway.status().message());
                return 0;
            }
            var models = gateway.models();
            if (command.equals("models")) {
                for (var model : models) System.out.println(model.id() + " · " + model.name());
                return 0;
            }
            String model = arguments.length == 2 ? arguments[1] : models.isEmpty() ? "" : models.getFirst().id();
            String reply = gateway.generate(model, "Responde únicamente con la palabra OK. No uses herramientas.",
                    List.of(new ChatGptGateway.Turn("user", "Comprobación técnica de conexión. Responde OK.")));
            if (!"OK".equals(reply.trim())) {
                System.err.println("La inferencia terminó, pero no devolvió el texto esperado. No se imprimió el contenido recibido.");
                return 4;
            }
            System.out.println("Inferencia completada: OK. No se enviaron datos de Exotic ni se accedió a una base de datos.");
            return 0;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.err.println("Operación interrumpida.");
            return 3;
        } catch (ResponseStatusException ex) {
            System.err.println(ex.getReason() == null ? "No se pudo completar la operación local." : ex.getReason());
            return 3;
        } catch (Exception ex) {
            System.err.println("No se pudo completar la operación local. Revise la sesión de escritorio y los permisos del perfil.");
            return 3;
        } finally {
            gateway.shutdown();
            try { Runtime.getRuntime().removeShutdownHook(shutdown); }
            catch (IllegalStateException ignored) { /* JVM shutdown already owns the hook. */ }
        }
    }
}
