package exotic.app.planta.service.compras;

import exotic.app.planta.model.compras.dto.OcmCierreDTOs.Modo;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OcmCierreScheduler {
    private final OcmCierreConfigService configuracion;
    private final OrdenCompraRepo ordenes;
    private final OcmCierreService cierre;
    private final Clock applicationClock;
    private int afterId;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cerrarPendientes() {
        var config = configuracion.consultar();
        if (config.modo() == Modo.DESACTIVADO) {
            afterId = 0;
            return;
        }
        LocalDateTime dueBefore = LocalDateTime.now(applicationClock);
        if (config.modo() == Modo.PLAZO) dueBefore = dueBefore.minusDays(config.dias());
        if (dueBefore.isBefore(config.activadoDesde())) return;
        var ids = ordenes.findAutomaticClosureIds(config.activadoDesde(), dueBefore, afterId, PageRequest.of(0, 100));
        for (int id : ids) {
            try {
                // Re-reads configuration and quantities under locks in its own transaction.
                cierre.cerrarAutomaticamente(id);
            } catch (RuntimeException error) {
                log.error("No fue posible cerrar automáticamente la OCM {}. Se reintentará en otro ciclo.", id, error);
            }
            afterId = id;
        }
        if (ids.size() < 100) afterId = 0;
    }
}
