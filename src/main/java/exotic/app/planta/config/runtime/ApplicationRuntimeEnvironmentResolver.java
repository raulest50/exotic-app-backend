package exotic.app.planta.config.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ApplicationRuntimeEnvironmentResolver {

    private static final String RUNTIME_ENVIRONMENT_PROPERTY = "app.runtime-environment";
    private static final String PRODUCTION_FLAG_NAME = "PRODUCTION";

    private final Environment environment;

    public ApplicationRuntimeEnvironment getCurrentEnvironment() {
        ApplicationRuntimeEnvironment explicit = ApplicationRuntimeEnvironment.fromValue(
                environment.getProperty(RUNTIME_ENVIRONMENT_PROPERTY)
        ).orElse(null);
        if (explicit != null) {
            return explicit;
        }

        if ("true".equals(normalize(System.getenv(PRODUCTION_FLAG_NAME)))) {
            return ApplicationRuntimeEnvironment.PRODUCTION;
        }

        Set<String> activeProfiles = new LinkedHashSet<>();
        activeProfiles.addAll(Arrays.stream(environment.getActiveProfiles())
                .map(ApplicationRuntimeEnvironmentResolver::normalize)
                .filter(profile -> !profile.isBlank())
                .toList());

        String activeProfilesProperty = normalize(environment.getProperty("spring.profiles.active"));
        if (!activeProfilesProperty.isBlank()) {
            activeProfiles.addAll(Arrays.stream(activeProfilesProperty.split(","))
                    .map(ApplicationRuntimeEnvironmentResolver::normalize)
                    .filter(profile -> !profile.isBlank())
                    .toList());
        }

        String activeProfilesEnv = normalize(System.getenv("SPRING_PROFILES_ACTIVE"));
        if (!activeProfilesEnv.isBlank()) {
            activeProfiles.addAll(Arrays.stream(activeProfilesEnv.split(","))
                    .map(ApplicationRuntimeEnvironmentResolver::normalize)
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

        String hostname = normalize(System.getenv("HOSTNAME"));
        if (hostname.isBlank() || !hostname.startsWith("render-")) {
            return ApplicationRuntimeEnvironment.LOCAL;
        }

        return ApplicationRuntimeEnvironment.PRODUCTION;
    }

    public boolean isLocal() {
        return getCurrentEnvironment() == ApplicationRuntimeEnvironment.LOCAL;
    }

    public boolean isLocalOrStaging() {
        return getCurrentEnvironment().isLocalOrStaging();
    }

    public boolean isProduction() {
        return getCurrentEnvironment() == ApplicationRuntimeEnvironment.PRODUCTION;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public enum ApplicationRuntimeEnvironment {
        LOCAL("local"),
        STAGING("staging"),
        PRODUCTION("production");

        private final String value;

        ApplicationRuntimeEnvironment(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public boolean isLocalOrStaging() {
            return this == LOCAL || this == STAGING;
        }

        public static Optional<ApplicationRuntimeEnvironment> fromValue(String value) {
            String normalized = normalize(value);
            return Arrays.stream(values())
                    .filter(environment -> environment.value.equals(normalized))
                    .findFirst();
        }
    }
}
