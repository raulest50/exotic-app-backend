package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.*;
import exotic.app.planta.model.produccion.fabricacion.*;
import exotic.app.planta.model.produccion.dto.MpsFabricacionDTOs.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.fabricacion.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MpsFabricacionObservacionServiceTest {
    @Mock MpsFabricacionService programaService;
    @Mock MpsFabricacionSemanalRepo programaRepo;
    @Mock MpsFabricacionObservacionRepo observacionRepo;
    private MpsFabricacionObservacionService service;
    private final LocalDate week = LocalDate.of(2026, 11, 9);
    private MpsFabricacionSemanal programa;
    private final User actor = new User();

    @BeforeEach void setup() {
        programa = new MpsFabricacionSemanal(); programa.setId(1L); programa.setWeekStartDate(week);
        actor.setUsername("revisor");
        service = new MpsFabricacionObservacionService(programaService, programaRepo, observacionRepo,
                Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test void atenderBloqueaPadreAntesDeCargarLaObservacion() {
        var o = new MpsFabricacionObservacion(); o.setObservacionId(8L); o.setMps(programa);
        when(observacionRepo.findWeekStartDateById(8L)).thenReturn(Optional.of(week));
        when(programaService.bloquear(week, 2L)).thenReturn(programa);
        when(observacionRepo.findById(8L)).thenReturn(Optional.of(o));
        var request = new AtenderObservacionRequest(); request.setVersion(2L); request.setRespuestaCorreccion("Ajustada la tanda");
        service.atender(8L, request, actor);
        var order = inOrder(observacionRepo, programaService);
        order.verify(observacionRepo).findWeekStartDateById(8L);
        order.verify(programaService).bloquear(week, 2L);
        order.verify(observacionRepo).findById(8L);
        assertEquals(EstadoMpsSemanalObservacion.ATENDIDA, o.getEstado());
        assertEquals("Ajustada la tanda", o.getRespuestaCorreccion());
    }

    @Test void observacionConcurrenteNoSeAgregaDespuesDeAprobar() {
        programa.setEstado(EstadoMpsSemanal.APROBADO);
        when(programaService.bloquear(week, 2L)).thenReturn(programa);
        var request = new CrearObservacionRequest(); request.setVersion(2L); request.setMensaje("Cambio de fecha");
        assertThrows(ResponseStatusException.class, () -> service.crear(week, request, actor));
        verifyNoInteractions(observacionRepo);
    }

    @Test void cerrarExigeQueLaObservacionEsteAtendida() {
        var o = new MpsFabricacionObservacion(); o.setMps(programa);
        when(observacionRepo.findWeekStartDateById(8L)).thenReturn(Optional.of(week));
        when(programaService.bloquear(week, 2L)).thenReturn(programa);
        when(observacionRepo.findById(8L)).thenReturn(Optional.of(o));
        var request = new VersionRequest(); request.setVersion(2L);
        assertThrows(ResponseStatusException.class, () -> service.cerrar(8L, request, actor));
        assertEquals(EstadoMpsSemanalObservacion.ABIERTA, o.getEstado());
    }
}
