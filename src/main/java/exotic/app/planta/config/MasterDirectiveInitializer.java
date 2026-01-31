package exotic.app.planta.config;

import org.springframework.stereotype.Component;

@Component
public class MasterDirectiveInitializer {

    /**
     * Inicializa la tabla 'master_directive'. Las 2 keys anteriores
     * (Permitir Consumo No Planificado, Permitir Backflush No Planificado) fueron eliminadas;
     * la configuración equivalente pasa por Super Master (super_master_config).
     */
    public void initializeMasterDirectives() {
        // No se crean directivas por defecto; la tabla puede quedar vacía o usarse para otras en el futuro.
    }
}
