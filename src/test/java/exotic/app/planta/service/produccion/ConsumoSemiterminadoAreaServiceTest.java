package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.dto.ConsumoSemiterminadoAreaDTOs.*;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.*;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.service.inventarios.SalidaAlmacenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Invariantes operativas y guardia de stock comun, sin arrancar la aplicacion. */
@ExtendWith(MockitoExtension.class)
class ConsumoSemiterminadoAreaServiceTest {
    @Mock OrdenProduccionRepo ordenProduccionRepo;
    @Mock SeguimientoOrdenAreaRepo seguimientoRepo;
    @Mock MaterialRequirementSnapshotService snapshotService;
    @Mock TransaccionAlmacenRepo movimientoRepo;
    @Mock TransaccionAlmacenHeaderRepo transaccionRepo;
    @Mock ProductoRepo productoRepo;
    @Mock LoteRepo loteRepo;
    @Mock SalidaAlmacenService salidaAlmacenService;
    @Mock BatchRecordProjectionQueueService projectionQueueService;
    @Mock EntityManager entityManager;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @Spy Clock applicationClock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
    @InjectMocks ConsumoSemiterminadoAreaService service;
    @InjectMocks SalidaAlmacenService guardiaStock;

    private User actor;
    private OrdenProduccion orden;
    private SeguimientoOrdenArea seguimiento;
    private SemiTerminado semi;

    @BeforeEach
    void prepararEntidades() {
        actor = new User(); actor.setId(7L); actor.setUsername("operador");
        AreaOperativa area = new AreaOperativa(); area.setAreaId(12);
        area.setNombre("Mezclado"); area.setResponsableArea(actor);
        RutaProcesoCatVersion ruta = new RutaProcesoCatVersion(); ruta.setId(31L);
        RutaProcesoNode nodo = new RutaProcesoNode(); nodo.setId(101L);
        nodo.setAreaOperativa(area); nodo.setRutaProcesoCatVersion(ruta);
        orden = new OrdenProduccion(); orden.setOrdenId(10);
        orden.setRutaProcesoCatVersion(ruta); orden.setRequerimientosMaterialesJson("[]");
        seguimiento = new SeguimientoOrdenArea(); seguimiento.setId(1001L);
        seguimiento.setOrdenProduccion(orden); seguimiento.setRutaProcesoNode(nodo);
        seguimiento.setAreaOperativa(area); seguimiento.setEstado(SeguimientoOrdenArea.ESTADO_ESPERA);
        semi = new SemiTerminado(); semi.setProductoId("S1"); semi.setNombre("Base neutra");
        semi.setTipoUnidades("KG"); semi.setRequiereOrdenFabricacion(true);
        ReflectionTestUtils.setField(guardiaStock, "entityManager", entityManager);
    }

    @Test
    void autorizaAlResponsableAntesDeConsultarUnaClaveDeReintento() {
        contextoBloqueado();
        User otro = new User(); otro.setId(8L);

        assertThrows(AccessDeniedException.class,
                () -> service.registrar(10, 1001L, solicitud("2"), otro));
        verifyNoInteractions(transaccionRepo, salidaAlmacenService, snapshotService);
    }

    @Test
    void noAceptaUnSeguimientoDeOtraOrden() {
        contextoBloqueado();
        OrdenProduccion otra = new OrdenProduccion(); otra.setOrdenId(99);
        seguimiento.setOrdenProduccion(otra);

        assertThrows(NoSuchElementException.class,
                () -> service.registrar(10, 1001L, solicitud("2"), actor));
        verifyNoInteractions(transaccionRepo, salidaAlmacenService);
    }

    @Test
    void completarUnaEtapaImpideNuevosRegistros() {
        contextoBloqueado(); requisitos();
        seguimiento.setEstado(SeguimientoOrdenArea.ESTADO_COMPLETADO);

        assertThrows(IllegalStateException.class,
                () -> service.registrar(10, 1001L, solicitud("2"), actor));
        verify(transaccionRepo, never()).saveAndFlush(any());
        verifyNoInteractions(salidaAlmacenService, productoRepo);
    }

    @Test
    void noEntregaMasQueLaCantidadPendienteDeLaEtapa() {
        contextoBloqueado(); requisitos();
        when(movimientoRepo.findDispensadoPorArea(10, 12,
                TransaccionAlmacen.TipoEntidadCausante.OD, Movimiento.TipoMovimiento.DISPENSACION))
                .thenReturn(Collections.singletonList(new Object[]{"S1", 3.0}));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.registrar(10, 1001L, solicitud("8"), actor));

        assertTrue(error.getMessage().contains("saldo pendiente"));
        verify(transaccionRepo, never()).saveAndFlush(any());
        verifyNoInteractions(salidaAlmacenService);
    }

    @Test
    void unRegistroYTodosSusReintentosConservanUnaSolaSalida() {
        contextoBloqueado(); requisitos(); productosYLote();
        AtomicReference<TransaccionAlmacen> persistida = persistenciaConfirmada();
        RegistroRequest request = solicitud("2");

        RegistroResponse primera = service.registrar(10, 1001L, request, actor);
        seguimiento.setEstado(SeguimientoOrdenArea.ESTADO_COMPLETADO);
        RegistroResponse replay = service.registrar(10, 1001L, request, actor);

        assertFalse(primera.reutilizado());
        assertTrue(replay.reutilizado());
        assertEquals(55, replay.transaccionId());
        assertFalse(replay.contexto().puedeRegistrar());
        assertEquals(TransaccionAlmacen.TipoEntidadCausante.OD, persistida.get().getTipoEntidadCausante());
        assertEquals(1, persistida.get().getMovimientosTransaccion().size());
        Movimiento movimiento = persistida.get().getMovimientosTransaccion().getFirst();
        assertEquals(-2.0, movimiento.getCantidad());
        assertEquals(12, movimiento.getAreaOperativa().getAreaId());
        assertEquals(Movimiento.TipoMovimiento.DISPENSACION, movimiento.getTipoMovimiento());
        assertTrue(movimiento.isAfectaInventario());
        verify(transaccionRepo, times(1)).saveAndFlush(any());
        verify(salidaAlmacenService, times(1)).validarYBloquearLotes(anyList());
        verify(projectionQueueService, times(1)).solicitarActualizacion(orden);

        request.getLotes().getFirst().setCantidad("3");
        assertThrows(IllegalStateException.class,
                () -> service.registrar(10, 1001L, request, actor));
        verify(transaccionRepo, times(1)).saveAndFlush(any());
    }

    @Test
    void faltaDeStockNoPersisteCabeceraNiSolicitaProyeccion() {
        contextoBloqueado(); requisitos(); productosYLote();
        doThrow(new IllegalStateException("Saldo insuficiente"))
                .when(salidaAlmacenService).validarYBloquearLotes(anyList());

        assertThrows(IllegalStateException.class,
                () -> service.registrar(10, 1001L, solicitud("2"), actor));

        verify(transaccionRepo, never()).saveAndFlush(any());
        verifyNoInteractions(projectionQueueService);
        assertEquals(0, orden.getEstadoOrden());
    }

    @Test
    void noConsultaLotesDeUnProductoAjenoALaEtapa() {
        contextoLectura(); requisitos();

        assertThrows(IllegalArgumentException.class,
                () -> service.lotes(10, 1001L, "OTRO", actor));
        verifyNoInteractions(movimientoRepo, salidaAlmacenService);
    }

    @Test
    void unaOrdenHistoricaNoSeReinterpretaComoConsumoPorArea() {
        contextoLectura(); orden.setRequerimientosMaterialesJson(null);

        Contexto contexto = service.preparar(10, 1001L, actor);

        assertTrue(contexto.legacy());
        assertFalse(contexto.puedeRegistrar());
        assertTrue(contexto.requerimientos().isEmpty());
        verifyNoInteractions(snapshotService, salidaAlmacenService);
    }

    @Test
    void cantidadesInvalidasNoAlcanzanLaPersistencia() {
        contextoBloqueado();
        for (String cantidad : List.of("0", "-1", "NaN", "1e2", "0.00001", "999999999999999")) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.registrar(10, 1001L, solicitud(cantidad), actor), cantidad);
        }
        verifyNoInteractions(transaccionRepo, salidaAlmacenService, productoRepo);
    }

    @Test
    void guardiaComunSumaLineasRepetidasAntesDeVerificarStock() {
        Lote lote = lote(71L);
        when(loteRepo.findByIdForUpdate(71L)).thenReturn(Optional.of(lote));
        when(movimientoRepo.findTotalCantidadByProductoIdAndLoteIdAndAlmacen("S1", 71L,
                Movimiento.Almacen.GENERAL)).thenReturn(1.0);

        assertThrows(IllegalStateException.class, () -> guardiaStock.validarYBloquearLotes(
                List.of(salida(lote, "0.6"), salida(lote, "0.6"))));
        verify(loteRepo, times(1)).findByIdForUpdate(71L);
    }

    @Test
    void guardiaComunNoConfundeRuidoDeDoubleConFaltaDeStock() {
        Lote lote = lote(71L);
        when(loteRepo.findByIdForUpdate(71L)).thenReturn(Optional.of(lote));
        when(movimientoRepo.findTotalCantidadByProductoIdAndLoteIdAndAlmacen("S1", 71L,
                Movimiento.Almacen.GENERAL)).thenReturn(0.3 - 0.1);

        assertDoesNotThrow(() -> guardiaStock.validarYBloquearLotes(List.of(salida(lote, "0.2000"))));
    }

    @Test
    void guardiaComunBloqueaLotesEnOrdenEstable() {
        Lote mayor = lote(72L); Lote menor = lote(71L);
        when(loteRepo.findByIdForUpdate(71L)).thenReturn(Optional.of(menor));
        when(loteRepo.findByIdForUpdate(72L)).thenReturn(Optional.of(mayor));
        when(movimientoRepo.findTotalCantidadByProductoIdAndLoteIdAndAlmacen(eq("S1"), anyLong(),
                eq(Movimiento.Almacen.GENERAL))).thenReturn(1.0);

        guardiaStock.validarYBloquearLotes(List.of(salida(mayor, "0.2"), salida(menor, "0.3")));

        InOrder ordenLocks = inOrder(loteRepo);
        ordenLocks.verify(loteRepo).findByIdForUpdate(71L);
        ordenLocks.verify(loteRepo).findByIdForUpdate(72L);
    }

    @Test
    void guardiaComunNoUsaUnLoteDeOtroProducto() {
        Lote lote = lote(71L);
        SemiTerminado otro = new SemiTerminado(); otro.setProductoId("S2"); lote.setProducto(otro);
        when(loteRepo.findByIdForUpdate(71L)).thenReturn(Optional.of(lote));

        assertThrows(IllegalArgumentException.class,
                () -> guardiaStock.validarYBloquearLotes(List.of(salida(lote, "0.2"))));
        verifyNoInteractions(movimientoRepo);
    }

    private void contextoBloqueado() {
        when(ordenProduccionRepo.findByIdForUpdate(10)).thenReturn(Optional.of(orden));
        when(seguimientoRepo.findByIdForUpdate(1001L)).thenReturn(Optional.of(seguimiento));
    }

    private void contextoLectura() {
        when(ordenProduccionRepo.findById(10)).thenReturn(Optional.of(orden));
        when(seguimientoRepo.findById(1001L)).thenReturn(Optional.of(seguimiento));
    }

    private void requisitos() {
        when(snapshotService.requerimientosEtapa(orden, 101L)).thenReturn(List.of(
                new MaterialRequirementSnapshotService.RequirementView("S1", "Base neutra", "SEMITERMINADO",
                        "KG", true, false, BigDecimal.TEN, 101L, 12)));
    }

    private void productosYLote() {
        when(productoRepo.findById("S1")).thenReturn(Optional.of(semi));
        when(loteRepo.getReferenceById(71L)).thenReturn(lote(71L));
    }

    private AtomicReference<TransaccionAlmacen> persistenciaConfirmada() {
        AtomicReference<TransaccionAlmacen> persistida = new AtomicReference<>();
        when(transaccionRepo.findByIdempotencyKey(any())).thenAnswer(inv -> Optional.ofNullable(persistida.get()));
        when(transaccionRepo.saveAndFlush(any())).thenAnswer(inv -> {
            TransaccionAlmacen transaccion = inv.getArgument(0);
            transaccion.setTransaccionId(55); persistida.set(transaccion); return transaccion;
        });
        when(transaccionRepo.findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                TransaccionAlmacen.TipoEntidadCausante.OD, 10))
                .thenAnswer(inv -> persistida.get() == null ? List.of() : List.of(persistida.get()));
        return persistida;
    }

    private RegistroRequest solicitud(String cantidad) {
        RegistroRequest request = new RegistroRequest(); request.setIdempotencyKey(UUID.randomUUID());
        LoteRequest item = new LoteRequest(); item.setProductoId("S1"); item.setLoteId(71L); item.setCantidad(cantidad);
        request.setLotes(List.of(item)); return request;
    }

    private Lote lote(Long id) {
        Lote lote = new Lote(); lote.setId(id); lote.setBatchNumber("BASE-" + id); lote.setProducto(semi); return lote;
    }

    private Movimiento salida(Lote lote, String cantidad) {
        Movimiento movimiento = new Movimiento(); movimiento.setLote(lote); movimiento.setProducto(semi);
        movimiento.setCantidad(new BigDecimal(cantidad).negate().doubleValue());
        movimiento.setAlmacen(Movimiento.Almacen.GENERAL); movimiento.setAfectaInventario(true);
        movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.DISPENSACION); return movimiento;
    }
}
