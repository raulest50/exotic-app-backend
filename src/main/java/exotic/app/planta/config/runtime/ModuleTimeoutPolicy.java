package exotic.app.planta.config.runtime;

import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver.ApplicationRuntimeEnvironment;
import exotic.app.planta.config.runtime.ModuleTimeoutProperties.EnvironmentTimeout;
import exotic.app.planta.config.runtime.ModuleTimeoutProperties.OperationTimeout;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Selects immutable transaction budgets for the environment resolved at startup. */
@Component("moduleTimeoutPolicy")
public class ModuleTimeoutPolicy {

    private static final String REQUIRED_MODULE = "bi";
    private static final String REQUIRED_OPERATION = "production-areas";

    private final Map<String, Map<String, Integer>> transactionTimeouts;

    public ModuleTimeoutPolicy(
            ModuleTimeoutProperties properties,
            ApplicationRuntimeEnvironmentResolver environmentResolver
    ) {
        validateCatalogGroup("production", properties.getProduction());
        validateCatalogGroup("testing", properties.getTesting());

        ApplicationRuntimeEnvironment currentEnvironment = environmentResolver.getCurrentEnvironment();
        Map<String, Map<String, OperationTimeout>> selectedGroup =
                currentEnvironment == ApplicationRuntimeEnvironment.PRODUCTION
                        ? properties.getProduction()
                        : properties.getTesting();
        Map<String, Map<String, Integer>> effectiveTimeouts = new LinkedHashMap<>();
        selectedGroup.forEach((module, operations) -> {
            Map<String, Integer> moduleTimeouts = new LinkedHashMap<>();
            operations.forEach((operation, timeout) -> {
                EnvironmentTimeout override = timeout.getEnvironments().get(currentEnvironment.value());
                moduleTimeouts.put(operation, override == null
                        ? timeout.getTransactionSeconds()
                        : override.getTransactionSeconds());
            });
            effectiveTimeouts.put(module, Map.copyOf(moduleTimeouts));
        });
        transactionTimeouts = Map.copyOf(effectiveTimeouts);
    }

    public int transactionSeconds(String module, String operation) {
        Map<String, Integer> operations = transactionTimeouts.get(module);
        Integer seconds = operations == null ? null : operations.get(operation);
        if (seconds == null) {
            throw new IllegalArgumentException(
                    "No transaction timeout configured for module '" + module
                            + "', operation '" + operation + "'."
            );
        }
        return seconds;
    }

    private static void validateCatalogGroup(
            String group,
            Map<String, Map<String, OperationTimeout>> modules
    ) {
        if (modules == null || modules.isEmpty()) {
            throw new IllegalArgumentException("Module timeout group '" + group + "' must not be empty.");
        }
        Map<String, OperationTimeout> requiredModule = modules.get(REQUIRED_MODULE);
        if (requiredModule == null || requiredModule.get(REQUIRED_OPERATION) == null) {
            throw new IllegalArgumentException(
                    "Missing transaction timeout app.module-timeouts."
                            + group + "." + REQUIRED_MODULE + "." + REQUIRED_OPERATION + "."
            );
        }
        modules.forEach((module, operations) -> operations.forEach((operation, timeout) ->
                timeout.getEnvironments().keySet().forEach(environmentName -> {
                    ApplicationRuntimeEnvironment environment = ApplicationRuntimeEnvironment
                            .fromValue(environmentName)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Unknown timeout environment '" + environmentName + "' in "
                                            + group + "." + module + "." + operation + "."
                            ));
                    boolean productionEnvironment = environment == ApplicationRuntimeEnvironment.PRODUCTION;
                    if (productionEnvironment != "production".equals(group)) {
                        throw new IllegalArgumentException(
                                "Timeout environment '" + environmentName + "' does not belong to group '"
                                        + group + "' for " + module + "." + operation + "."
                        );
                    }
                    if (!environmentName.equals(environment.value())) {
                        throw new IllegalArgumentException(
                                "Timeout environment keys must use canonical names: " + environment.value() + "."
                        );
                    }
                })));
    }
}
