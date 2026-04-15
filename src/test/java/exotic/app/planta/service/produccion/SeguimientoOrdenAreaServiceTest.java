package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCat;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeguimientoOrdenAreaServiceTest {

    @Test
    void inicializarSeguimiento_creaEstadosInicialesYBitacora() {
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        SeguimientoOrdenAreaEventoRepo eventoRepo = mock(SeguimientoOrdenAreaEventoRepo.class);
        RutaProcesoCatRepo rutaProcesoCatRepo = mock(RutaProcesoCatRepo.class);
        UserRepository userRepository = mock(UserRepository.class);
        MutableClock clock = new MutableClock("2026-04-15T10:15:30Z");

        mockSeguimientoSave(seguimientoRepo);
        mockEventoSave(eventoRepo);

        SeguimientoOrdenAreaService service = new SeguimientoOrdenAreaService(
                seguimientoRepo,
                eventoRepo,
                rutaProcesoCatRepo,
                userRepository,
                clock
        );

        Categoria categoria = new Categoria();
        categoria.setCategoriaId(10);

        Terminado terminado = new Terminado();
        terminado.setProductoId("T-001");
        terminado.setNombre("Producto terminado");
        terminado.setCategoria(categoria);

        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(77);
        orden.setProducto(terminado);

        AreaOperativa areaMezcla = new AreaOperativa();
        areaMezcla.setAreaId(1);
        areaMezcla.setNombre("Mezcla");

        AreaOperativa areaEmpaque = new AreaOperativa();
        areaEmpaque.setAreaId(2);
        areaEmpaque.setNombre("Empaque");

        RutaProcesoNode nodoInicial = new RutaProcesoNode();
        nodoInicial.setId(100L);
        nodoInicial.setAreaOperativa(areaMezcla);
        nodoInicial.setLabel("Mezcla");

        RutaProcesoNode nodoSucesor = new RutaProcesoNode();
        nodoSucesor.setId(200L);
        nodoSucesor.setAreaOperativa(areaEmpaque);
        nodoSucesor.setLabel("Empaque");

        RutaProcesoEdge edge = new RutaProcesoEdge();
        edge.setSourceNode(nodoInicial);
        edge.setTargetNode(nodoSucesor);

        RutaProcesoCat ruta = new RutaProcesoCat();
        ruta.setNodes(List.of(nodoInicial, nodoSucesor));
        ruta.setEdges(List.of(edge));

        when(rutaProcesoCatRepo.findByCategoria_CategoriaId(10)).thenReturn(Optional.of(ruta));

        service.inicializarSeguimiento(orden);

        ArgumentCaptor<SeguimientoOrdenArea> seguimientoCaptor = ArgumentCaptor.forClass(SeguimientoOrdenArea.class);
        verify(seguimientoRepo, times(2)).save(seguimientoCaptor.capture());

        SeguimientoOrdenArea seguimientoInicial = seguimientoCaptor.getAllValues().stream()
                .filter(item -> item.getRutaProcesoNode().getId().equals(100L))
                .findFirst()
                .orElseThrow();
        SeguimientoOrdenArea seguimientoSucesor = seguimientoCaptor.getAllValues().stream()
                .filter(item -> item.getRutaProcesoNode().getId().equals(200L))
                .findFirst()
                .orElseThrow();

        assertEquals(EstadoSeguimientoOrdenArea.ESPERA, seguimientoInicial.getEstadoEnum());
        assertEquals(LocalDateTime.ofInstant(clock.instant(), clock.getZone()), seguimientoInicial.getFechaEstadoActual());
        assertEquals(LocalDateTime.ofInstant(clock.instant(), clock.getZone()), seguimientoInicial.getFechaVisible());

        assertEquals(EstadoSeguimientoOrdenArea.COLA, seguimientoSucesor.getEstadoEnum());
        assertEquals(LocalDateTime.ofInstant(clock.instant(), clock.getZone()), seguimientoSucesor.getFechaEstadoActual());
        assertNull(seguimientoSucesor.getFechaVisible());

        ArgumentCaptor<SeguimientoOrdenAreaEvento> eventoCaptor = ArgumentCaptor.forClass(SeguimientoOrdenAreaEvento.class);
        verify(eventoRepo, times(2)).save(eventoCaptor.capture());

        assertEquals(List.of(1, 0), eventoCaptor.getAllValues().stream().map(SeguimientoOrdenAreaEvento::getEstadoDestino).toList());
        assertEquals(List.of(null, null), eventoCaptor.getAllValues().stream().map(SeguimientoOrdenAreaEvento::getEstadoOrigen).toList());
    }

    @Test
    void reportarCompletado_rechazaSeguimientoQueNoEsteEnProceso() {
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        SeguimientoOrdenAreaEventoRepo eventoRepo = mock(SeguimientoOrdenAreaEventoRepo.class);
        RutaProcesoCatRepo rutaProcesoCatRepo = mock(RutaProcesoCatRepo.class);
        UserRepository userRepository = mock(UserRepository.class);
        MutableClock clock = new MutableClock("2026-04-15T10:15:30Z");

        SeguimientoOrdenAreaService service = new SeguimientoOrdenAreaService(
                seguimientoRepo,
                eventoRepo,
                rutaProcesoCatRepo,
                userRepository,
                clock
        );

        when(seguimientoRepo.findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(7, 11, EstadoSeguimientoOrdenArea.EN_PROCESO.getCode()))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.reportarCompletado(7, 11, 99L, "cerrar")
        );

        assertEquals("No se encontro seguimiento en proceso para orden 7 y area 11", exception.getMessage());
    }

    @Test
    void cicloProceso_registraEventosYActualizaEstadoActual() {
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        SeguimientoOrdenAreaEventoRepo eventoRepo = mock(SeguimientoOrdenAreaEventoRepo.class);
        RutaProcesoCatRepo rutaProcesoCatRepo = mock(RutaProcesoCatRepo.class);
        UserRepository userRepository = mock(UserRepository.class);
        MutableClock clock = new MutableClock("2026-04-15T10:00:00Z");

        mockSeguimientoSave(seguimientoRepo);
        mockEventoSave(eventoRepo);

        SeguimientoOrdenAreaService service = new SeguimientoOrdenAreaService(
                seguimientoRepo,
                eventoRepo,
                rutaProcesoCatRepo,
                userRepository,
                clock
        );

        User actor = new User();
        actor.setId(50L);
        actor.setNombreCompleto("Operario 1");
        when(userRepository.findById(50L)).thenReturn(Optional.of(actor));

        SeguimientoOrdenArea seguimiento = buildSeguimiento(900L, 25, 3, 700L, EstadoSeguimientoOrdenArea.ESPERA);
        seguimiento.setFechaVisible(LocalDateTime.of(2026, 4, 15, 8, 0));
        seguimiento.setFechaEstadoActual(LocalDateTime.of(2026, 4, 15, 8, 0));

        when(seguimientoRepo.findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int estadoSolicitado = invocation.getArgument(2);
                    return seguimiento.getEstado() == estadoSolicitado ? Optional.of(seguimiento) : Optional.empty();
                });

        clock.setInstant("2026-04-15T10:00:00Z");
        service.reportarEnProceso(25, 3, 50L, "inicio");
        assertEquals(EstadoSeguimientoOrdenArea.EN_PROCESO, seguimiento.getEstadoEnum());
        assertEquals(LocalDateTime.of(2026, 4, 15, 10, 0), seguimiento.getFechaEstadoActual());

        clock.setInstant("2026-04-15T10:30:00Z");
        service.pausarProceso(25, 3, 50L, "pausa");
        assertEquals(EstadoSeguimientoOrdenArea.ESPERA, seguimiento.getEstadoEnum());
        assertEquals(LocalDateTime.of(2026, 4, 15, 10, 30), seguimiento.getFechaEstadoActual());

        clock.setInstant("2026-04-15T10:45:00Z");
        service.reportarEnProceso(25, 3, 50L, "reanuda");
        assertEquals(EstadoSeguimientoOrdenArea.EN_PROCESO, seguimiento.getEstadoEnum());
        assertEquals(LocalDateTime.of(2026, 4, 15, 10, 45), seguimiento.getFechaEstadoActual());

        clock.setInstant("2026-04-15T11:15:00Z");
        service.reportarCompletado(25, 3, 50L, "terminado");
        assertEquals(EstadoSeguimientoOrdenArea.COMPLETADO, seguimiento.getEstadoEnum());
        assertEquals(LocalDateTime.of(2026, 4, 15, 11, 15), seguimiento.getFechaEstadoActual());
        assertEquals(LocalDateTime.of(2026, 4, 15, 11, 15), seguimiento.getFechaCompletado());
        assertEquals(actor, seguimiento.getUsuarioReporta());
        assertEquals("terminado", seguimiento.getObservaciones());

        ArgumentCaptor<SeguimientoOrdenAreaEvento> eventoCaptor = ArgumentCaptor.forClass(SeguimientoOrdenAreaEvento.class);
        verify(eventoRepo, times(4)).save(eventoCaptor.capture());

        assertEquals(
                List.of(
                        EstadoSeguimientoOrdenArea.ESPERA.getCode(),
                        EstadoSeguimientoOrdenArea.EN_PROCESO.getCode(),
                        EstadoSeguimientoOrdenArea.ESPERA.getCode(),
                        EstadoSeguimientoOrdenArea.EN_PROCESO.getCode()
                ),
                eventoCaptor.getAllValues().stream().map(SeguimientoOrdenAreaEvento::getEstadoOrigen).toList()
        );
        assertEquals(
                List.of(
                        EstadoSeguimientoOrdenArea.EN_PROCESO.getCode(),
                        EstadoSeguimientoOrdenArea.ESPERA.getCode(),
                        EstadoSeguimientoOrdenArea.EN_PROCESO.getCode(),
                        EstadoSeguimientoOrdenArea.COMPLETADO.getCode()
                ),
                eventoCaptor.getAllValues().stream().map(SeguimientoOrdenAreaEvento::getEstadoDestino).toList()
        );
    }

    @Test
    void completarSeguimiento_promueveSucesorDeColaAEspera() {
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        SeguimientoOrdenAreaEventoRepo eventoRepo = mock(SeguimientoOrdenAreaEventoRepo.class);
        RutaProcesoCatRepo rutaProcesoCatRepo = mock(RutaProcesoCatRepo.class);
        UserRepository userRepository = mock(UserRepository.class);
        MutableClock clock = new MutableClock("2026-04-15T12:00:00Z");

        mockSeguimientoSave(seguimientoRepo);
        mockEventoSave(eventoRepo);

        SeguimientoOrdenAreaService service = new SeguimientoOrdenAreaService(
                seguimientoRepo,
                eventoRepo,
                rutaProcesoCatRepo,
                userRepository,
                clock
        );

        User actor = new User();
        actor.setId(88L);
        actor.setNombreCompleto("Operario 2");
        when(userRepository.findById(88L)).thenReturn(Optional.of(actor));

        SeguimientoOrdenArea seguimientoActual = buildSeguimiento(300L, 40, 5, 500L, EstadoSeguimientoOrdenArea.EN_PROCESO);
        seguimientoActual.setFechaEstadoActual(LocalDateTime.of(2026, 4, 15, 11, 0));

        SeguimientoOrdenArea sucesor = buildSeguimiento(301L, 40, 6, 600L, EstadoSeguimientoOrdenArea.COLA);
        sucesor.setFechaEstadoActual(LocalDateTime.of(2026, 4, 15, 9, 0));

        when(seguimientoRepo.findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int areaId = invocation.getArgument(1);
                    int estadoSolicitado = invocation.getArgument(2);
                    if (areaId == 5 && seguimientoActual.getEstado() == estadoSolicitado) {
                        return Optional.of(seguimientoActual);
                    }
                    return Optional.empty();
                });
        when(seguimientoRepo.findSucesoresPendientes(40, 500L)).thenReturn(List.of(sucesor));
        when(seguimientoRepo.countPredecesoresNoCompletados(40, 600L)).thenReturn(0L);

        service.reportarCompletado(40, 5, 88L, "ok");

        assertEquals(EstadoSeguimientoOrdenArea.COMPLETADO, seguimientoActual.getEstadoEnum());
        assertEquals(EstadoSeguimientoOrdenArea.ESPERA, sucesor.getEstadoEnum());
        assertNotNull(sucesor.getFechaVisible());
        assertEquals(sucesor.getFechaVisible(), sucesor.getFechaEstadoActual());

        ArgumentCaptor<SeguimientoOrdenAreaEvento> eventoCaptor = ArgumentCaptor.forClass(SeguimientoOrdenAreaEvento.class);
        verify(eventoRepo, times(2)).save(eventoCaptor.capture());

        assertEquals(
                List.of(
                        EstadoSeguimientoOrdenArea.COMPLETADO.getCode(),
                        EstadoSeguimientoOrdenArea.ESPERA.getCode()
                ),
                eventoCaptor.getAllValues().stream().map(SeguimientoOrdenAreaEvento::getEstadoDestino).toList()
        );
    }

    private void mockSeguimientoSave(SeguimientoOrdenAreaRepo seguimientoRepo) {
        AtomicLong ids = new AtomicLong(1);
        when(seguimientoRepo.save(any(SeguimientoOrdenArea.class)))
                .thenAnswer(invocation -> {
                    SeguimientoOrdenArea seguimiento = invocation.getArgument(0);
                    if (seguimiento.getId() == null) {
                        seguimiento.setId(ids.getAndIncrement());
                    }
                    return seguimiento;
                });
    }

    private void mockEventoSave(SeguimientoOrdenAreaEventoRepo eventoRepo) {
        AtomicLong ids = new AtomicLong(1);
        when(eventoRepo.save(any(SeguimientoOrdenAreaEvento.class)))
                .thenAnswer(invocation -> {
                    SeguimientoOrdenAreaEvento evento = invocation.getArgument(0);
                    if (evento.getId() == null) {
                        evento.setId(ids.getAndIncrement());
                    }
                    return evento;
                });
    }

    private SeguimientoOrdenArea buildSeguimiento(
            Long seguimientoId,
            int ordenId,
            int areaId,
            Long nodeId,
            EstadoSeguimientoOrdenArea estado
    ) {
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(areaId);
        area.setNombre("Area " + areaId);

        RutaProcesoNode node = new RutaProcesoNode();
        node.setId(nodeId);
        node.setLabel("Nodo " + nodeId);

        Terminado producto = new Terminado();
        producto.setProductoId("T-" + ordenId);
        producto.setNombre("Producto " + ordenId);

        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(ordenId);
        orden.setProducto(producto);
        orden.setCantidadProducir(12);

        SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
        seguimiento.setId(seguimientoId);
        seguimiento.setOrdenProduccion(orden);
        seguimiento.setAreaOperativa(area);
        seguimiento.setRutaProcesoNode(node);
        seguimiento.setEstadoEnum(estado);
        return seguimiento;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zoneId = ZoneId.of("UTC");

        private MutableClock(String isoInstant) {
            this.instant = Instant.parse(isoInstant);
        }

        private void setInstant(String isoInstant) {
            this.instant = Instant.parse(isoInstant);
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
