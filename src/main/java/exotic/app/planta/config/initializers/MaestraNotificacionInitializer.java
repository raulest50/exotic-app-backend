package exotic.app.planta.config.initializers;

import exotic.app.planta.model.notificaciones.MaestraNotificacion;
import exotic.app.planta.repo.notificaciones.MaestraNotificacionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MaestraNotificacionInitializer {

    private final MaestraNotificacionRepo maestraNotificacionRepo;

    public void initializeMaestraNotificaciones() {
        if (maestraNotificacionRepo.count() == 0) {
            List<MaestraNotificacion> notificaciones = List.of(
                    new MaestraNotificacion(
                            1,
                            "Alerta de Punto de Reorden",
                            "Notificación enviada cuando el stock de un material alcanza su punto de reorden, " +
                            "indicando que se debe generar una Orden de Compra de Materiales.",
                            new ArrayList<>()
                    )
            );
            maestraNotificacionRepo.saveAll(notificaciones);

        }
    }
}
