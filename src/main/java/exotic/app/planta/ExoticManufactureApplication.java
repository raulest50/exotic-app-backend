package exotic.app.planta;

import exotic.app.planta.config.TimeZoneConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class ExoticManufactureApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(TimeZoneConfig.APP_TIME_ZONE_ID));
		SpringApplication.run(ExoticManufactureApplication.class, args);
	}

}
