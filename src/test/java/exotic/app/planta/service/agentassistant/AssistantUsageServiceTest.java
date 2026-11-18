package exotic.app.planta.service.agentassistant;

import exotic.app.planta.repo.agentassistant.AgentAssistantUsageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantUsageServiceTest {
    private final AgentAssistantUsageRepository repository = mock(AgentAssistantUsageRepository.class);

    private AssistantUsageService service(String budget) {
        return new AssistantUsageService(repository, "prototype", new BigDecimal(budget),
                new BigDecimal("0.75"), new BigDecimal("0.075"), new BigDecimal("4.50"));
    }

    @Test void distinguishesCachedInputAndOutputCosts() {
        service("20").record("resp_example", "example-model", 1_000_000, 200_000, 100_000);
        verify(repository).record("resp_example", "prototype", "example-model", 1_000_000,
                200_000, 100_000, new BigDecimal("1.0650000000"));
    }

    @Test void readsPersistedUsageInsteadOfResettingWhenServiceIsRecreated() {
        when(repository.spent("prototype")).thenReturn(new BigDecimal("5"));
        assertThat(service("20").view().remainingPercent()).isEqualTo(75.0);
        assertThat(service("20").view().remainingPercent()).isEqualTo(75.0);
    }

    @Test void exhaustedOrMissingAllowanceCannotStartAnotherPaidRequest() {
        when(repository.spent("prototype")).thenReturn(new BigDecimal("21"));
        assertThat(service("20").view().remainingPercent()).isZero();
        assertThatThrownBy(() -> service("20").requireAllowance()).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service("0").requireAllowance()).isInstanceOf(ResponseStatusException.class);
    }

    @Test void invalidUsageStopsFurtherRequestsWithoutStoringNegativeCounts() {
        var service = service("20");
        assertThatThrownBy(() -> service.record("resp_example", "example-model", 10, 11, 1))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(service.view().configured()).isFalse();
        verifyNoInteractions(repository);
    }

    @Test void failedAccountingSuspendsTheProviderWithoutLeakingTheDatabaseError() {
        doThrow(new IllegalStateException("sensitive database failure"))
                .when(repository).record(anyString(), anyString(), anyString(), anyLong(), anyLong(), anyLong(), any());
        var service = service("20");
        assertThatThrownBy(() -> service.record("resp_example", "example-model", 10, 0, 2))
                .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("sensitive");
        assertThat(service.view().configured()).isFalse();
    }
}
