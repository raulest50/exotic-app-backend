package exotic.app.planta.config.runtime;

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ApplicationRuntimeEnvironmentSupport {

    private static final String RUNTIME_ENVIRONMENT_PROPERTY = "app.runtime-environment";
    private static final String PRODUCTION_FLAG_NAME = "PRODUCTION";

    private ApplicationRuntimeEnvironmentSupport() {
    }

    public static ApplicationRuntimeEnvironment resolve(Environment environment) {
        ApplicationRuntimeEnvironment explicit = ApplicationRuntimeEnvironment.fromValue(
                environment.getProperty(RUNTIME_ENVIRONMENT_PROPERTY)
        ).orElse(null);
        if (explicit != null) {
            return explicit;
        }

        if ("true".equals(ApplicationRuntimeEnvironment.normalize(System.getenv(PRODUCTION_FLAG_NAME)))) {
            return ApplicationRuntimeEnvironment.PRODUCTION;
        }

        Set<String> activeProfiles = new LinkedHashSet<>();
        activeProfiles.addAll(Arrays.stream(environment.getActiveProfiles())
                .map(ApplicationRuntimeEnvironment::normalize)
                .filter(profile -> !profile.isBlank())
                .toList());

        String activeProfilesProperty = ApplicationRuntimeEnvironment.normalize(
                environment.getProperty("spring.profiles.active")
        );
        if (!activeProfilesProperty.isBlank()) {
            activeProfiles.addAll(Arrays.stream(activeProfilesProperty.split(","))
                    .map(ApplicationRuntimeEnvironment::normalize)
                    .filter(profile -> !profile.isBlank())
                    .toList());
        }

        String activeProfilesEnv = ApplicationRuntimeEnvironment.normalize(System.getenv("SPRING_PROFILES_ACTIVE"));
        if (!activeProfilesEnv.isBlank()) {
            activeProfiles.addAll(Arrays.stream(activeProfilesEnv.split(","))
                    .map(ApplicationRuntimeEnvironment::normalize)
                    .filter(profile -> !profile.isBlank())
                    .toList());
        }

        if (activeProfiles.contains("prod") || activeProfiles.contains("production")) {
            return ApplicationRuntimeEnvironment.PRODUCTION;
        }
        if (activeProfiles.contains("staging")) {
            return ApplicationRuntimeEnvironment.STAGING;
        }
        if (activeProfiles.contains("dev") || activeProfiles.contains("local")) {
            return ApplicationRuntimeEnvironment.LOCAL;
        }

        String hostname = ApplicationRuntimeEnvironment.normalize(System.getenv("HOSTNAME"));
        if (hostname.isBlank() || !hostname.startsWith("render-")) {
            return ApplicationRuntimeEnvironment.LOCAL;
        }

        return ApplicationRuntimeEnvironment.PRODUCTION;
    }
}
