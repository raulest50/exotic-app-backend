package exotic.app.planta.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeZoneConfig {

    public static final String APP_TIME_ZONE_ID = "America/Bogota";

    @Bean
    public ZoneId applicationZoneId() {
        return ZoneId.of(APP_TIME_ZONE_ID);
    }

    @Bean
    public Clock applicationClock(ZoneId applicationZoneId) {
        return Clock.system(applicationZoneId);
    }
}
