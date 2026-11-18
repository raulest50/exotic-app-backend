package exotic.app.planta.service.agentassistant;

import exotic.app.planta.repo.agentassistant.AgentAssistantUsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Estimates the assigned allowance using provider-reported tokens and private server-side rates. */
@Service
public class AssistantUsageService {
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private final AgentAssistantUsageRepository repository;
    private final String quotaId;
    private final BigDecimal budget;
    private final BigDecimal inputRate;
    private final BigDecimal cachedRate;
    private final BigDecimal outputRate;
    private volatile boolean accountingHealthy = true;

    public AssistantUsageService(AgentAssistantUsageRepository repository,
            @Value("${app.agent-assistant.quota-id:exotic-initial}") String quotaId,
            @Value("${app.agent-assistant.budget-usd:0}") BigDecimal budget,
            @Value("${app.agent-assistant.input-usd-per-million:0.75}") BigDecimal inputRate,
            @Value("${app.agent-assistant.cached-input-usd-per-million:0.075}") BigDecimal cachedRate,
            @Value("${app.agent-assistant.output-usd-per-million:4.50}") BigDecimal outputRate) {
        this.repository = repository;
        this.quotaId = quotaId;
        this.budget = budget;
        this.inputRate = inputRate;
        this.cachedRate = cachedRate;
        this.outputRate = outputRate;
    }

    public boolean configured() {
        return quotaId != null && quotaId.matches("[A-Za-z0-9_-]{1,100}") && budget.signum() > 0
                && inputRate.signum() > 0 && cachedRate.signum() >= 0
                && cachedRate.compareTo(inputRate) <= 0 && outputRate.signum() > 0;
    }

    public AssistantAiGateway.Usage view() {
        if (!configured() || !accountingHealthy) return new AssistantAiGateway.Usage(false, null);
        BigDecimal remaining = budget.subtract(repository.spent(quotaId)).max(BigDecimal.ZERO);
        double percent = remaining.multiply(BigDecimal.valueOf(100)).divide(budget, 2, RoundingMode.DOWN)
                .min(BigDecimal.valueOf(100)).doubleValue();
        return new AssistantAiGateway.Usage(true, percent);
    }

    public void requireAllowance() {
        var usage = view();
        if (!usage.configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "El cupo del asistente debe configurarse en el servidor.");
        if (usage.remainingPercent() <= 0) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Se agotó el cupo asignado al asistente. Contacta al administrador.");
    }

    /** Called before interpreting output, including refusals or incomplete responses. */
    public void record(String responseId, String model, long input, long cached, long output) {
        if (responseId == null || responseId.isBlank() || responseId.length() > 128
                || model == null || model.isBlank() || model.length() > 100
                || input < 0 || cached < 0 || cached > input || output < 0) {
            accountingHealthy = false;
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el consumo de la respuesta.");
        }
        BigDecimal cost = BigDecimal.valueOf(input - cached).multiply(inputRate)
                .add(BigDecimal.valueOf(cached).multiply(cachedRate))
                .add(BigDecimal.valueOf(output).multiply(outputRate))
                .divide(MILLION, 10, RoundingMode.HALF_UP);
        try {
            repository.record(responseId, quotaId, model, input, cached, output, cost);
        } catch (RuntimeException ex) {
            accountingHealthy = false;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo registrar el consumo. El asistente quedó temporalmente suspendido.");
        }
    }
}
