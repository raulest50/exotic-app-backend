package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.*;
import exotic.app.planta.model.produccion.fabricacion.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.fabricacion.MpsFabricacionSemanalRepo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class MpsFabricacionEmisionServiceTest {
    @Mock MpsFabricacionSemanalRepo programaRepo;
    @Mock MpsFabricacionService programaService;
    @Mock OrdenFabricacionService ordenService;
    private MpsFabricacionEmisionService service;
    private final LocalDate week = LocalDate.of(2026, 11, 9);
    private MpsFabricacionSemanal programa;
    private final User actor = new User();

    @BeforeEach void setup() {
        actor.setUsername("aprobador");
        programa = new MpsFabricacionSemanal(); programa.setId(1L); programa.setWeekStartDate(week); programa.setVersion(3L);
        when(programaRepo.findByWeekForUpdate(week)).thenReturn(Optional.of(programa));
        service = new MpsFabricacionEmisionService(programaRepo, programaService, ordenService,
                Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test void borradorNoEmiteNingunaOf() {
        assertThrows(ResponseStatusException.class, () -> service.emitir(week, 3L, actor));
        verifyNoInteractions(ordenService);
    }

    @Test void reintentoTrasConfirmarNoDuplicaOfAunqueConserveVersionAnterior() {
        programa.setEstado(EstadoMpsSemanal.CERRADO);
        service.emitir(week, 2L, actor);
        verifyNoInteractions(ordenService);
        verify(programaService, never()).actualizar(any(), any(), anyBoolean());
    }

    @Test void tresTandasEmitenTresOfYUnaCanceladaNoSeEmite() {
        programa.setEstado(EstadoMpsSemanal.APROBADO);
        for (long id = 1; id <= 3; id++) {
            var d = new MpsFabricacionDetalle(); d.setId(id); d.setMps(programa); programa.getDetalles().add(d);
        }
        var cancelada = new MpsFabricacionDetalle(); cancelada.setEstado(EstadoMpsSemanalItem.CANCELADO);
        programa.getDetalles().add(cancelada);
        service.emitir(week, 3L, actor);
        verify(ordenService, times(3)).crearDesdeMps(any(), eq(actor));
        verify(ordenService, never()).crearDesdeMps(eq(cancelada), any());
        assertEquals(EstadoMpsSemanal.CERRADO, programa.getEstado());
        assertNotNull(programa.getFechaEmision());
    }

    @Test void fallaUnaTandaNoMarcaElProgramaComoEmitido() {
        programa.setEstado(EstadoMpsSemanal.APROBADO);
        var d = new MpsFabricacionDetalle(); programa.getDetalles().add(d);
        when(ordenService.crearDesdeMps(d, actor)).thenThrow(new IllegalStateException("Version de manufactura ausente"));
        assertThrows(IllegalStateException.class, () -> service.emitir(week, 3L, actor));
        assertEquals(EstadoMpsSemanal.APROBADO, programa.getEstado());
        assertNull(programa.getFechaEmision());
    }
}
