package exotic.app.planta.service.compras.proveedor.metricas;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeadTimeProveedorKpiSchedulerTest {

    @Test
    void refreshWeekly_delegatesToRefreshAllProveedores() {
        LeadTimeProveedorKpiRefreshService refreshService = mock(LeadTimeProveedorKpiRefreshService.class);
        when(refreshService.refreshAllProveedores())
                .thenReturn(new LeadTimeProveedorKpiRefreshService.LeadTimeProveedorKpiRefreshSummary(1, 1, 0, 0, 0));

        LeadTimeProveedorKpiScheduler scheduler = new LeadTimeProveedorKpiScheduler(refreshService);

        scheduler.refreshWeekly();

        verify(refreshService).refreshAllProveedores();
    }
}
