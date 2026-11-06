package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.*;
import exotic.app.planta.model.produccion.fabricacion.*;
import exotic.app.planta.model.produccion.dto.MpsFabricacionDTOs.*;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.produccion.fabricacion.*;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class MpsFabricacionAprobadoEditServiceTest {
    @Mock MpsFabricacionService programaService;
    @Mock MpsFabricacionDetalleRepo detalleRepo;
    @Mock SemiTerminadoRepo semiRepo;
    @Mock OrdenFabricacionRepo ordenRepo;
    @Mock OrdenFabricacionService ordenService;
    @Mock MpsFabricacionEmisionService emisionService;
    @Mock MasterDirectiveService masterDirectiveService;
    @Mock EntityManager entityManager;
    @InjectMocks MpsFabricacionAprobadoEditService service;
    private final LocalDate week = LocalDate.of(2026, 11, 9);
    private MpsFabricacionSemanal programa;
    private MpsFabricacionDetalle detalle;
    private final User actor = new User();

    @BeforeEach void setup() {
        programa = new MpsFabricacionSemanal(); programa.setId(1L); programa.setWeekStartDate(week);
        programa.setEstado(EstadoMpsSemanal.CERRADO);
        var semi = new SemiTerminado(); semi.setProductoId("S1"); semi.setTipoUnidades("KG"); semi.setRequiereOrdenFabricacion(true);
        detalle = new MpsFabricacionDetalle(); detalle.setId(1L); detalle.setMps(programa); detalle.setSemiTerminado(semi);
        detalle.setCantidad(new BigDecimal("500")); detalle.setUnidadMedida("KG");
        programa.getDetalles().add(detalle);
        when(programaService.bloquear(week, 3L)).thenReturn(programa);
    }

    @Test void agregarTandaDelMismoSemiEnSemanaEmitidaGeneraSoloSuOfSinReaprobar() {
        when(semiRepo.findById("S1")).thenReturn(Optional.of(detalle.getSemiTerminado()));
        service.agregar(week, request("500"), actor);
        verify(emisionService).emitirTanda(any(), eq(actor));
        assertEquals(EstadoMpsSemanal.CERRADO, programa.getEstado());
        verifyNoInteractions(masterDirectiveService);
    }

    @Test void noCambiaCantidadDeUnaTandaEmitidaNiSuExpediente() {
        var orden = new OrdenFabricacion(); orden.setOrdenFabricacionId(8L); detalle.setOrdenFabricacion(orden);
        when(detalleRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(detalle));
        when(ordenRepo.findByIdForUpdate(8L)).thenReturn(Optional.of(orden));
        when(ordenService.esCancelable(orden)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.editar(week, 1L, request("600"), actor));
        assertEquals(new BigDecimal("500"), detalle.getCantidad());
        verify(ordenService, never()).reprogramarDesdeMps(any());
    }

    @Test void cancelarConEjecucionSeRechazaSinQuitarLaTanda() {
        var orden = new OrdenFabricacion(); orden.setOrdenFabricacionId(8L); detalle.setOrdenFabricacion(orden);
        when(detalleRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(detalle));
        when(ordenRepo.findByIdForUpdate(8L)).thenReturn(Optional.of(orden));
        assertThrows(ResponseStatusException.class, () -> service.cancelar(week, 1L, 3L, actor));
        assertEquals(EstadoMpsSemanalItem.ACTIVO, detalle.getEstado());
        assertEquals(1, programa.getDetalles().size());
        verify(ordenService, never()).cancelarDesdeMps(any(), any());
    }

    private LineaRequest request(String cantidad) {
        var r = new LineaRequest(); r.setVersion(3L); r.setSemiTerminadoId("S1");
        r.setCantidad(new BigDecimal(cantidad)); r.setFechaInicio(week.atTime(8, 0)); r.setFechaFinal(week.atTime(16, 0));
        return r;
    }
}
