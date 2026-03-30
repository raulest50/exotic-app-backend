package exotic.app.planta.config.initializers;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AreaOperativaInitializer {

    public static final String ALMACEN_GENERAL_NOMBRE = "Almacen General";
    public static final int ALMACEN_GENERAL_ID = -1;

    private static final Logger log = LoggerFactory.getLogger(AreaOperativaInitializer.class);

    private final AreaProduccionRepo areaProduccionRepo;

    public void verifyAlmacenGeneral() {
        Optional<AreaOperativa> almacenGeneral = areaProduccionRepo.findByNombre(ALMACEN_GENERAL_NOMBRE);

        if (almacenGeneral.isPresent()) {
            AreaOperativa area = almacenGeneral.get();
            if (area.getAreaId() == ALMACEN_GENERAL_ID) {
                log.info("Almacen General verificado correctamente (ID: {})", ALMACEN_GENERAL_ID);
            } else {
                log.warn("Almacen General existe pero con ID diferente al esperado. ID actual: {}, ID esperado: {}",
                        area.getAreaId(), ALMACEN_GENERAL_ID);
            }
        } else {
            log.warn("Almacen General no existe en la base de datos. La migración V018 debería haberlo creado.");
        }
    }

    public Optional<AreaOperativa> getAlmacenGeneral() {
        return areaProduccionRepo.findByNombre(ALMACEN_GENERAL_NOMBRE);
    }
}
