package exotic.app.planta.config;

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
    private final AccesoRepository accesoRepository;

    private static final Logger log = LoggerFactory.getLogger(DataBaseInitializer.class);

    @Bean
    CommandLineRunner initDatabase() {
        return args -> {
            superMasterConfigInitializer.initializeSuperMasterConfig();
            // Check if the accesos table is empty (you can also check other key tables if desired)
            // this check is to see if db is already initialized.
            if (accesoRepository.count() == 0) { // if not initialized, then it does the carga masiva
                log.info("Database is empty. Performing initial data setup...");
                usersInitializer.initializeUsers();
                //cargaMasiva.executeCargaMasiva();
                cuentasInitializer.initializeCuentas();
                masterDirectiveInitializer.initializeMasterDirectives();
            } else { // if already initialzed then do nothing
                log.info("Database is already initialized. Skipping insert initialization.");
            }
        };
    }
}
