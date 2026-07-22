package exotic.app.planta.service.bi;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalDia;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.MpsSemanalDiaRepo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InformeProduccionServiceTest {

    @Test
    void loadsMpsInBulkAndKeepsHighestPrecedenceForTheDate() {
        LocalDate date = LocalDate.of(2026, 7, 21);
        TransaccionAlmacenRepo movementRepo = mock(TransaccionAlmacenRepo.class);
        MasterProductionScheduleSemanalRepo mpsRepo =
                mock(MasterProductionScheduleSemanalRepo.class);
        MpsSemanalDiaRepo dayRepo = mock(MpsSemanalDiaRepo.class);
        CategoriaRepo categoryRepo = mock(CategoriaRepo.class);
        InformeProduccionService service = new InformeProduccionService(
                movementRepo,
                mpsRepo,
                dayRepo,
                categoryRepo);

        MasterProductionScheduleSemanal revisionTwo = mps(22, 2, date);
        MasterProductionScheduleSemanal revisionOne = mps(11, 1, date);
        MpsSemanalDia selectedDay = new MpsSemanalDia();
        selectedDay.setFecha(date);
        selectedDay.setMpsSemanal(revisionTwo);

        when(mpsRepo.findAllOverlappingRange(date, date))
                .thenReturn(List.of(revisionTwo, revisionOne));
        when(dayRepo.findAllByMpsIdsAndDateRange(List.of(22), date, date))
                .thenReturn(List.of(selectedDay));
        when(movementRepo.findIngresosTerminadoPorFechaEfectiva(
                eq(date),
                eq(date),
                any(),
                any(),
                eq(Movimiento.TipoMovimiento.BACKFLUSH)))
                .thenReturn(List.of());
        when(movementRepo.sumIngresosTerminadoPorFechaEfectiva(
                any(),
                any(),
                any(),
                any(),
                eq(Movimiento.TipoMovimiento.BACKFLUSH)))
                .thenReturn(12d);
        when(categoryRepo.findAllById(anyCollection())).thenReturn(List.of());

        var report = service.obtenerReporte(date, date);

        assertEquals(List.of(22), report.getMpsIds());
        assertEquals(12d, report.getResumen().getUnidadesProducidasPeriodoAnterior());
        verify(mpsRepo).findAllOverlappingRange(date, date);
        verify(dayRepo).findAllByMpsIdsAndDateRange(List.of(22), date, date);
        verify(mpsRepo, never()).findAllContainingDate(any());
        verify(dayRepo, never()).findByMpsIdAndFecha(any(), any());
    }

    private MasterProductionScheduleSemanal mps(
            int id,
            int revision,
            LocalDate date
    ) {
        MasterProductionScheduleSemanal mps = new MasterProductionScheduleSemanal();
        mps.setMpsId(id);
        mps.setRevisionNumero(revision);
        mps.setWeekStartDate(date.minusDays(1));
        mps.setWeekEndDate(date.plusDays(1));
        return mps;
    }
}
