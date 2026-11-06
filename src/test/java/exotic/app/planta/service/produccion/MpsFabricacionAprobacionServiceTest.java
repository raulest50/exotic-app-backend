package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.*;
import exotic.app.planta.model.produccion.fabricacion.*;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.fabricacion.MpsFabricacionObservacionRepo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class MpsFabricacionAprobacionServiceTest {
    @Mock MpsFabricacionService programaService;
    @Mock MpsFabricacionObservacionRepo observacionRepo;
    private final LocalDate week = LocalDate.of(2026, 11, 9);
    private final User actor = new User();
    private MpsFabricacionSemanal programa;
    private MpsFabricacionAprobacionService service;

    @BeforeEach void setup() {
        actor.setUsername("aprobador");
        programa = new MpsFabricacionSemanal(); programa.setId(1L); programa.setWeekStartDate(week);
        var semi = new SemiTerminado(); semi.setRequiereOrdenFabricacion(true);
        var detalle = new MpsFabricacionDetalle(); detalle.setSemiTerminado(semi); programa.getDetalles().add(detalle);
        when(programaService.bloquear(week, 3L)).thenReturn(programa);
        service = new MpsFabricacionAprobacionService(programaService, observacionRepo,
                Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test void observacionAtendidaPendienteDeAceptacionImpideAprobar() {
        when(observacionRepo.countByMps_IdAndEstadoIn(eq(1L), any())).thenReturn(1L);
        assertThrows(ResponseStatusException.class, () -> service.aprobar(week, 3L, actor));
        assertEquals(EstadoMpsSemanal.BORRADOR, programa.getEstado());
        verify(programaService, never()).actualizar(any(), any(), anyBoolean());
    }

    @Test void aprobarRegistraAutorYFechaSinEmitirOrdenes() {
        service.aprobar(week, 3L, actor);
        assertEquals(EstadoMpsSemanal.APROBADO, programa.getEstado());
        assertEquals("aprobador", programa.getAprobadoPorUsername());
        assertEquals(LocalDateTime.of(2026, 11, 6, 12, 0), programa.getFechaAprobacion());
        assertNull(programa.getFechaEmision());
        assertNull(programa.getDetalles().getFirst().getOrdenFabricacion());
    }

    @Test void noApruebaUnaSemanaSinTandasActivas() {
        programa.getDetalles().getFirst().setEstado(EstadoMpsSemanalItem.CANCELADO);
        assertThrows(IllegalArgumentException.class, () -> service.aprobar(week, 3L, actor));
    }
}
