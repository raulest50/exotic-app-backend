package exotic.app.planta.config.runtime;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

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

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
