package exotic.app.planta.service.compras.proveedor.metricas;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeadTimeProveedorKpiScheduler {

    private final LeadTimeProveedorKpiRefreshService refreshService;

    @Scheduled(cron = "0 0 18 * * SUN", zone = "America/Bogota")
    public void refreshWeekly() {
        log.info("LeadTimeProveedorKPI: iniciando refresh semanal.");
        LeadTimeProveedorKpiRefreshService.LeadTimeProveedorKpiRefreshSummary summary =
                refreshService.refreshAllProveedores();
        log.info(
                "LeadTimeProveedorKPI: refresh semanal completado. evaluados={}, vigentes={}, desactualizados={}, sinInformacion={}, fallidos={}",
                summary.proveedoresEvaluados(),
                summary.vigentes(),
                summary.desactualizados(),
                summary.sinInformacion(),
                summary.fallidos()
        );
    }
}
