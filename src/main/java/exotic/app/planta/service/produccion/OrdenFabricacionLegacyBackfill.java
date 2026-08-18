package exotic.app.planta.service.produccion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Backfill idempotente y conservador de la proyección operativa de OF legadas. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrdenFabricacionLegacyBackfill {

    private final OrdenFabricacionOperacionService operacionService;

    @EventListener(ApplicationReadyEvent.class)
    public void completarProyeccionesLegadas() {
        for (Long ordenId : operacionService.listarOrdenesPendientesBackfillLegado()) {
            try {
                operacionService.backfillProyeccionLegada(ordenId);
            } catch (RuntimeException error) {
                log.error("No se pudo completar la proyeccion legada de la OF {}.",
                        ordenId, error);
            }
        }
    }
}
