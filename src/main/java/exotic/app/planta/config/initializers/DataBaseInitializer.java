package exotic.app.planta.config.initializers;

import exotic.app.planta.repo.usuarios.AccesoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class DataBaseInitializer {

    private final UsersInitializer usersInitializer;
    private final CargaMasiva cargaMasiva;
    private final CuentasInitializer cuentasInitializer;
    private final MasterDirectiveInitializer masterDirectiveInitializer;
    private final SuperMasterConfigInitializer superMasterConfigInitializer;
    private final MaestraNotificacionInitializer maestraNotificacionInitializer;
    private final AccesoRepository accesoRepository;

    private static final Logger log = LoggerFactory.getLogger(DataBaseInitializer.class);

    @Bean
    CommandLineRunner initDatabase() {
        return args -> {
            superMasterConfigInitializer.initializeSuperMasterConfig();
            maestraNotificacionInitializer.initializeMaestraNotificaciones();
            if (accesoRepository.count() == 0) {
                log.info("Database is empty. Performing initial data setup...");
                usersInitializer.initializeUsers();
                //cargaMasiva.executeCargaMasiva();
                cuentasInitializer.initializeCuentas();
                masterDirectiveInitializer.initializeMasterDirectives();
            } else {
                log.info("Database is already initialized. Skipping insert initialization.");
            }
        };
    }
}
