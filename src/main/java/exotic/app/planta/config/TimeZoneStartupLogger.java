package exotic.app.planta.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class TimeZoneStartupLogger implements ApplicationRunner {

    private final Clock applicationClock;
    private final ZoneId applicationZoneId;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        AppTime.setClock(applicationClock);

        log.info(
                "Timezone initialized. jvmDefault={}, appZoneId={}, clockZone={}, currentBogotaTime={}",
                ZoneId.systemDefault(),
                applicationZoneId,
                applicationClock.getZone(),
                LocalDateTime.now(applicationClock)
        );

        try {
            String dbTimezone = jdbcTemplate.queryForObject("select current_setting('TIMEZONE')", String.class);
            log.info("PostgreSQL session timezone: {}", dbTimezone);
        } catch (Exception ex) {
            log.warn("Could not read PostgreSQL session timezone at startup: {}", ex.getMessage());
        }
    }
}
