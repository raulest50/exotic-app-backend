package exotic.app.planta.service.commons;

import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DangerousOperationGuard {

    private final ApplicationRuntimeEnvironmentResolver applicationRuntimeEnvironmentResolver;

    public void assertNotProduction(String operationName) {
        if (isProductionEnvironment()) {
            throw new IllegalStateException(buildBlockedMessage(operationName));
        }
    }

    public void assertLocalOrStagingOnly(String operationName) {
        if (!isLocalOrStaging()) {
            throw new UnsupportedOperationException(buildLocalOrStagingOnlyBlockedMessage(operationName));
        }
    }

    public boolean isProductionEnvironment() {
        return applicationRuntimeEnvironmentResolver.isProduction();
    }

    public boolean isLocalOrStaging() {
        return applicationRuntimeEnvironmentResolver.isLocalOrStaging();
    }

    public String buildBlockedMessage(String operationName) {
        return operationName + " está bloqueada en producción. Entorno actual: "
                + applicationRuntimeEnvironmentResolver.getCurrentEnvironment().value()
                + ".";
    }

    public String buildLocalOrStagingOnlyBlockedMessage(String operationName) {
        return operationName + " no está soportada en producción. Solo está disponible en local o staging. Entorno actual: "
                + applicationRuntimeEnvironmentResolver.getCurrentEnvironment().value()
                + ".";
    }
}
