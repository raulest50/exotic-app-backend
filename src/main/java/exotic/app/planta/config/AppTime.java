package exotic.app.planta.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Fuente centralizada de tiempo para el backend.
 * Evita depender implícitamente del timezone del contenedor.
 */
public final class AppTime {

    private static volatile Clock clock = Clock.system(ZoneId.of(TimeZoneConfig.APP_TIME_ZONE_ID));

    private AppTime() {
    }

    public static void setClock(Clock newClock) {
        clock = newClock;
    }

    public static Clock clock() {
        return clock;
    }

    public static ZoneId zoneId() {
        return clock.getZone();
    }

    public static Instant instant() {
        return clock.instant();
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    public static YearMonth currentYearMonth() {
        return YearMonth.now(clock);
    }
}
