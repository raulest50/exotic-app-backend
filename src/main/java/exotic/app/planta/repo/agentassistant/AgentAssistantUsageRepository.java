package exotic.app.planta.repo.agentassistant;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.math.BigDecimal;

/** Private accounting only: no prompts, SQL, result rows, identities or credentials. */
@Repository
public class AgentAssistantUsageRepository {
    private final JdbcTemplate jdbc;

    public AgentAssistantUsageRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void record(String responseId, String quotaId, String model, long input, long cached,
                       long output, BigDecimal estimatedCost) {
        try {
            jdbc.update("""
                    INSERT INTO agent_assistant_usage
                        (response_id, quota_id, model, input_tokens, cached_input_tokens, output_tokens, estimated_cost)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (response_id) DO NOTHING
                    """, responseId, quotaId, model, input, cached, output, estimatedCost);
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    public BigDecimal spent(String quotaId) {
        try {
            return jdbc.queryForObject("SELECT COALESCE(SUM(estimated_cost), 0) FROM agent_assistant_usage WHERE quota_id = ?",
                    BigDecimal.class, quotaId);
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo verificar el cupo del asistente. Intenta más tarde.");
    }
}
