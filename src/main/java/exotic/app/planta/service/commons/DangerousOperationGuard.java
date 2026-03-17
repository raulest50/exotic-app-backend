package exotic.app.planta.service.commons;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class DangerousOperationGuard {

    private static final String PRODUCTION_ENV_FLAG = "true";
    private static final String PRODUCTION_PROFILE = "prod";

    private final Environment environment;

    public void assertNotProduction(String operationName) {
        if (isProductionEnvironment()) {
            throw new IllegalStateException(buildBlockedMessage(operationName));
        }
    }

    public boolean isProductionEnvironment() {
        String productionFlag = normalize(System.getenv("PRODUCTION"));
        if (PRODUCTION_ENV_FLAG.equals(productionFlag)) {
            return true;
        }

        String profilesEnv = normalize(System.getenv("SPRING_PROFILES_ACTIVE"));
        if (!profilesEnv.isEmpty()) {
            boolean envContainsProd = Arrays.stream(profilesEnv.split(","))
                    .map(this::normalize)
                    .anyMatch(PRODUCTION_PROFILE::equals);
            if (envContainsProd) {
                return true;
            }
        }

        return Arrays.stream(environment.getActiveProfiles())
                .map(this::normalize)
                .anyMatch(PRODUCTION_PROFILE::equals);
    }

    public String buildBlockedMessage(String operationName) {
        return operationName + " está bloqueada en producción. Requiere PRODUCTION != TRUE y SPRING_PROFILES_ACTIVE != prod.";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
