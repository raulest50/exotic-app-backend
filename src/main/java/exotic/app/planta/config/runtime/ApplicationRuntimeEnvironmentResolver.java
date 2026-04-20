package exotic.app.planta.config.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationRuntimeEnvironmentResolver {

    private final Environment environment;

    public ApplicationRuntimeEnvironment getCurrentEnvironment() {
        return ApplicationRuntimeEnvironmentSupport.resolve(environment);
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
}
