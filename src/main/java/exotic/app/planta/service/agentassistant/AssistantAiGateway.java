package exotic.app.planta.service.agentassistant;

import java.util.List;

/** Server-side AI boundary. Credentials and accounting details never cross this boundary. */
public interface AssistantAiGateway {
    boolean enabled();
    boolean available();
    /** Opaque generation identifier; never a credential or account identifier. */
    String connectionId();
    String generate(String instructions, List<Turn> turns);

    default Usage usage() { return new Usage(false, null); }

    record Turn(String role, String content) { }
    /** Percentage of the server-assigned allowance; never the provider's wallet balance. */
    record Usage(boolean configured, Double remainingPercent) { }
}
