package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.organizacion.AlcanceMps;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.produccion.dto.MpsFabricacionDTOs.*;
import exotic.app.planta.model.produccion.fabricacion.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.produccion.fabricacion.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MpsFabricacionServiceTest {
    private static final LocalDate WEEK = LocalDate.of(2026, 9, 28);
    @Mock private MpsFabricacionSemanalRepo programaRepo;
    @Mock private SemiTerminadoRepo semiRepo;
    @Mock private ManufacturingVersionRepo manufacturingRepo;
    @Mock private OrdenFabricacionRepo ordenRepo;
    @Mock private LoteRepo loteRepo;
    @Mock private OrdenFabricacionService ordenService;
    private MpsFabricacionService service;
    private final User actor = new User();

    @BeforeEach
    void setup() {
        actor.setUsername("planificador");
        service = new MpsFabricacionService(programaRepo, semiRepo, manufacturingRepo, ordenRepo,
                loteRepo, ordenService, new ObjectMapper(), Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void consultarSemanaVaciaNoCreaProgramaNiOrdenes() {
        var dto = service.consultar(WEEK, null);
        assertNull(dto.getId());
        assertNull(dto.getVersion());
        assertTrue(dto.getPropuestas().isEmpty());
        assertEquals(WEEK.plusDays(6), dto.getWeekEndDate());
        verify(programaRepo, never()).saveAndFlush(any());
        verifyNoInteractions(ordenRepo, loteRepo, ordenService);
    }

    @Test
    void rechazaRevisionDesactualizadaAntesDeCambiarLineas() {
        var programa = programa();
        when(programaRepo.findByWeekForUpdate(WEEK)).thenReturn(Optional.of(programa));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.guardar(WEEK, request(2L, List.of()), actor));
        assertEquals(409, error.getStatusCode().value());
        verify(programaRepo, never()).saveAndFlush(any());
        verifyNoInteractions(semiRepo, ordenRepo, loteRepo, ordenService);
    }

    @Test
    void unaSemanaCreadaPorOtroUsuarioRechazaLaVersionNula() {
        when(programaRepo.findByWeekForUpdate(WEEK)).thenReturn(Optional.of(programa()));
        assertThrows(ResponseStatusException.class, () -> service.guardar(WEEK, request(null, List.of()), actor));
        verify(programaRepo, never()).saveAndFlush(any());
    }

    @Test
    void primeraPropuestaConservaCantidadDecimalYNoEmiteFabricacion() {
        when(semiRepo.findById("S1")).thenReturn(Optional.of(semi("S1")));
        var dto = service.guardar(WEEK, request(null, List.of(linea(null, "S1"))), actor);
        assertEquals(new BigDecimal("12.3456"), dto.getPropuestas().get(0).getCantidad());
        assertEquals("KG", dto.getPropuestas().get(0).getUnidadMedida());
        verify(programaRepo).saveAndFlush(argThat(p -> p.getCreadoPor().equals("planificador")
                && p.getDetalles().size() == 1 && p.getDetalles().get(0).getMps() == p));
        verifyNoInteractions(ordenRepo, loteRepo, ordenService);
    }

    @Test
    void reemplazarPropuestasConservaIdsExistentesYRetiraSoloLasOmitidas() {
        var programa = programa();
        var mantener = detalle(11L, semi("S1"));
        programa.getDetalles().addAll(List.of(mantener, detalle(12L, semi("S2"))));
        when(programaRepo.findByWeekForUpdate(WEEK)).thenReturn(Optional.of(programa));
        when(semiRepo.findById("S1")).thenReturn(Optional.of(mantener.getSemiTerminado()));
        when(semiRepo.findById("S3")).thenReturn(Optional.of(semi("S3")));
        service.guardar(WEEK, request(3L, List.of(linea(11L, "S1"), linea(null, "S3"))), actor);
        assertEquals(2, programa.getDetalles().size());
        assertSame(mantener, programa.getDetalles().get(0));
        assertEquals("S3", programa.getDetalles().get(1).getSemiTerminado().getProductoId());
        assertTrue(programa.getActualizadoEn().isAfter(LocalDateTime.of(2026, 9, 28, 12, 0)));
        verifyNoInteractions(ordenRepo, loteRepo, ordenService);
    }

    @Test
    void rechazaLineaDeOtraSemanaYSemiterminadoSinIndicadorOf() {
        when(programaRepo.findByWeekForUpdate(WEEK)).thenReturn(Optional.of(programa()));
        assertThrows(IllegalArgumentException.class,
                () -> service.guardar(WEEK, request(3L, List.of(linea(999L, "S1"))), actor));
        var semi = semi("S1");
        semi.setRequiereOrdenFabricacion(false);
        when(semiRepo.findById("S1")).thenReturn(Optional.of(semi));
        assertThrows(IllegalArgumentException.class,
                () -> service.guardar(WEEK, request(3L, List.of(linea(null, "S1"))), actor));
        verify(programaRepo, never()).saveAndFlush(any());
    }

    @Test
    void rechazaCambioDeUnidadSinReinterpretarCantidadAnterior() {
        var programa = programa();
        programa.getDetalles().add(detalle(11L, semi("S1")));
        when(programaRepo.findByWeekForUpdate(WEEK)).thenReturn(Optional.of(programa));
        var cambiado = semi("S1");
        cambiado.setTipoUnidades("L");
        when(semiRepo.findById("S1")).thenReturn(Optional.of(cambiado));
        assertThrows(IllegalArgumentException.class,
                () -> service.guardar(WEEK, request(3L, List.of(linea(11L, "S1"))), actor));
        assertEquals("KG", programa.getDetalles().get(0).getUnidadMedida());
        verify(programaRepo, never()).saveAndFlush(any());
    }

    @Test
    void inicioDebeEstarEnSemanaPeroFinalPuedeEstarEnLaSiguiente() {
        var linea = linea(null, "S1");
        linea.setFechaInicio(WEEK.plusWeeks(1).atStartOfDay());
        assertThrows(IllegalArgumentException.class, () -> service.guardar(WEEK, request(null, List.of(linea)), actor));
        linea.setFechaInicio(WEEK.atStartOfDay());
        linea.setFechaFinal(WEEK.minusDays(1).atStartOfDay());
        assertThrows(IllegalArgumentException.class, () -> service.guardar(WEEK, request(null, List.of(linea)), actor));
        linea.setFechaFinal(WEEK.plusWeeks(1).atStartOfDay());
        when(semiRepo.findById("S1")).thenReturn(Optional.of(semi("S1")));
        assertEquals(linea.getFechaFinal(), service.guardar(WEEK, request(null, List.of(linea)), actor)
                .getPropuestas().get(0).getFechaFinal());
    }

    @Test
    void propuestasPorRutaUsanManufacturaActualYUnaRutaAusenteNoDaVisibilidad() {
        var programa = programa();
        var visible = detalle(1L, semi("S1"));
        var oculta = detalle(2L, semi("S2"));
        programa.getDetalles().addAll(List.of(visible, oculta));
        when(programaRepo.findByWeekStartDate(WEEK)).thenReturn(Optional.of(programa));
        var manufacturing = new ManufacturingVersions();
        manufacturing.setProcesoProduccionJson("{\"nodes\":[{\"nodeType\":\"PROCESO\",\"areaOperativaId\":7}]}");
        when(manufacturingRepo.findTopByProductoOrderByVersionNumberDesc(visible.getSemiTerminado()))
                .thenReturn(Optional.of(manufacturing));
        var result = service.consultar(WEEK, area());
        assertEquals(List.of("S1"), result.getPropuestas().stream().map(LineaResponse::getSemiTerminadoId).toList());
        assertEquals(2, programa.getDetalles().size(), "Filtrar una consulta no elimina propuestas persistidas");
    }

    @Test
    void ordenesFiltranAntesDePaginarYExplicanFechaDeCreacionComoRespaldo() {
        var orden = new OrdenFabricacion();
        orden.setOrdenFabricacionId(19L);
        orden.setSemiTerminado(semi("S1"));
        orden.setFechaCreacion(WEEK.atTime(10, 0));
        when(ordenRepo.findSemanaMps(WEEK.atStartOfDay(), WEEK.plusWeeks(1).atStartOfDay(), 7, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(orden), PageRequest.of(0, 20), 1));
        var result = service.ordenes(WEEK, 0, 20, area());
        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().get(0).isUsaFechaCreacion());
        assertEquals(orden.getFechaCreacion(), result.getContent().get(0).getFechaInicioSemana());
    }

    @Test
    void detalleDeOfAjenaAlAreaEsRechazadoAntesDeCargarlo() {
        assertThrows(AccessDeniedException.class, () -> service.detalleOrden(19L, area()));
        verifyNoInteractions(ordenService);
    }

    @Test
    void rechazaSemanaNoIsoYPaginacionFueraDeLimites() {
        assertThrows(IllegalArgumentException.class, () -> service.consultar(WEEK.plusDays(1), null));
        assertThrows(IllegalArgumentException.class, () -> service.ordenes(WEEK, -1, 20, null));
        assertThrows(IllegalArgumentException.class, () -> service.ordenes(WEEK, 0, 101, null));
        verifyNoInteractions(programaRepo, ordenRepo);
    }

    private MpsFabricacionSemanal programa() {
        var p = new MpsFabricacionSemanal();
        p.setId(1L);
        p.setWeekStartDate(WEEK);
        p.setVersion(3);
        p.setActualizadoEn(LocalDateTime.of(2026, 9, 28, 12, 0));
        return p;
    }

    private SemiTerminado semi(String id) {
        var semi = new SemiTerminado();
        semi.setProductoId(id);
        semi.setNombre(id);
        semi.setTipoUnidades("KG");
        semi.setRequiereOrdenFabricacion(true);
        return semi;
    }

    private MpsFabricacionDetalle detalle(Long id, SemiTerminado semi) {
        var d = new MpsFabricacionDetalle();
        d.setId(id);
        d.setSemiTerminado(semi);
        d.setUnidadMedida("KG");
        return d;
    }

    private LineaRequest linea(Long id, String semiId) {
        var l = new LineaRequest();
        l.setId(id);
        l.setSemiTerminadoId(semiId);
        l.setCantidad(new BigDecimal("12.3456"));
        l.setFechaInicio(WEEK.atTime(8, 0));
        l.setFechaFinal(WEEK.atTime(16, 0));
        return l;
    }

    private GuardarRequest request(Long version, List<LineaRequest> lineas) {
        var r = new GuardarRequest();
        r.setVersion(version);
        r.setPropuestas(lineas);
        return r;
    }

    private AreaOperativa area() {
        var area = new AreaOperativa();
        area.setAreaId(7);
        area.setAlcanceMps(AlcanceMps.SOLO_RUTA);
        return area;
    }
}
