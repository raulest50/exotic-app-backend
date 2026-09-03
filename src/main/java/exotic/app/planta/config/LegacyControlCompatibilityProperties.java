package exotic.app.planta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Controls the temporary write bridge for the V077 quality/process-control API.
 * The bridge remains the safe default until all legacy clients have been inventoried
 * and moved to the scoped process-control facade.
 */
@Component
@ConfigurationProperties("app.control-engine.legacy")
public class LegacyControlCompatibilityProperties {

    private WriteMode writeMode = WriteMode.BRIDGE;

    public WriteMode getWriteMode() {
        return writeMode;
    }

    public void setWriteMode(WriteMode writeMode) {
        this.writeMode = writeMode == null ? WriteMode.BRIDGE : writeMode;
    }

    public boolean bridgeEnabled() {
        return writeMode == WriteMode.BRIDGE;
    }

    public enum WriteMode {
        BRIDGE,
        RETIRED
    }
}
