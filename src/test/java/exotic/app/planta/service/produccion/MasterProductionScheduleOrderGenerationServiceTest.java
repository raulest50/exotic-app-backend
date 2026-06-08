package exotic.app.planta.service.produccion;
import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.dto.GenerarOdpDesdeMpsRequestDTO;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.MpsSemanalLotePlanificadoRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterProductionScheduleOrderGenerationServiceTest {

    @Test
    void generarOrdenesDesdeSemanaAprobada_rejectsWeekWithGeneratedOrders() {
        MasterProductionScheduleSemanalRepo mpsRepo = mock(MasterProductionScheduleSemanalRepo.class);
        MpsSemanalLotePlanificadoRepo lotePlanificadoRepo = mock(MpsSemanalLotePlanificadoRepo.class);
        OrdenProduccionRepo ordenRepo = mock(OrdenProduccionRepo.class);
        ProduccionService produccionService = mock(ProduccionService.class);
        MasterProductionScheduleOrderGenerationService service = new MasterProductionScheduleOrderGenerationService(
                mpsRepo,
                lotePlanificadoRepo,
                ordenRepo,
                produccionService
        );

        LocalDate weekStartDate = LocalDate.of(2026, 6, 1);
        MasterProductionScheduleSemanal mps = new MasterProductionScheduleSemanal();
        mps.setMpsId(77);
        mps.setWeekStartDate(weekStartDate);
        mps.setEstado(EstadoMpsSemanal.APROBADO);

        when(mpsRepo.findByWeekStartDate(weekStartDate)).thenReturn(Optional.of(mps));
        when(ordenRepo.existsByMpsSemanal_MpsId(77)).thenReturn(true);

        GenerarOdpDesdeMpsRequestDTO request = new GenerarOdpDesdeMpsRequestDTO();
        request.setWeekStartDate(weekStartDate);

        assertThrows(IllegalStateException.class, () -> service.generarOrdenesDesdeSemanaAprobada(request, "planner"));
    }
}
