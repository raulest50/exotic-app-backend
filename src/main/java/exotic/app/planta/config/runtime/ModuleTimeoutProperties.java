package exotic.app.planta.config.runtime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

/** Transaction budgets loaded from the versioned module timeout catalog. */
@Component
@ConfigurationProperties(prefix = "app.module-timeouts", ignoreUnknownFields = false)
@Validated
public class ModuleTimeoutProperties {

    @Valid
    @NotEmpty
    private Map<@NotBlank String,
            @NotEmpty Map<@NotBlank String, @NotNull @Valid OperationTimeout>> production =
            new LinkedHashMap<>();

    @Valid
    @NotEmpty
    private Map<@NotBlank String,
            @NotEmpty Map<@NotBlank String, @NotNull @Valid OperationTimeout>> testing =
            new LinkedHashMap<>();

    public Map<String, Map<String, OperationTimeout>> getProduction() {
        return production;
    }

    public void setProduction(Map<String, Map<String, OperationTimeout>> production) {
        this.production = production;
    }

    public Map<String, Map<String, OperationTimeout>> getTesting() {
        return testing;
    }

    public void setTesting(Map<String, Map<String, OperationTimeout>> testing) {
        this.testing = testing;
    }

    public static class OperationTimeout {

        @NotNull
        @Positive
        private Integer transactionSeconds;

        @Valid
        @NotNull
        private Map<@NotBlank String, @NotNull @Valid EnvironmentTimeout> environments =
                new LinkedHashMap<>();

        public Integer getTransactionSeconds() {
            return transactionSeconds;
        }

        public void setTransactionSeconds(Integer transactionSeconds) {
            this.transactionSeconds = transactionSeconds;
        }

        public Map<String, EnvironmentTimeout> getEnvironments() {
            return environments;
        }

        public void setEnvironments(Map<String, EnvironmentTimeout> environments) {
            this.environments = environments;
        }
    }

    public static class EnvironmentTimeout {

        @NotNull
        @Positive
        private Integer transactionSeconds;

        public Integer getTransactionSeconds() {
            return transactionSeconds;
        }

        public void setTransactionSeconds(Integer transactionSeconds) {
            this.transactionSeconds = transactionSeconds;
        }
    }
}
