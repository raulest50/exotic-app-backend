package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.dto.OcmCierreDTOs.Config;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.IngresoOCM_DTA;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.compras.OcmCierreConfigService;
import exotic.app.planta.service.compras.OcmCierreService;
import exotic.app.planta.service.compras.RecepcionCompletaOcmService;
import exotic.app.planta.service.contabilidad.ContabilidadService;
import exotic.app.planta.service.produccion.ProduccionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MovimientosServiceOcmCierreTest {
    private OrdenCompraRepo ordenes;
    private TransaccionAlmacenHeaderRepo transacciones;
    private OcmCierreConfigService configuracion;
    private OcmCierreService cierre;
    private RecepcionCompletaOcmService recepcion;
    private ContabilidadService contabilidad;
    private RecepcionOcmPolicyService limites;
    private OrdenCompraMateriales orden;
    private IngresoOCM_DTA request;
    private MovimientosService service;

    @BeforeEach
    void preparar() {
        ordenes = mock(OrdenCompraRepo.class);
        transacciones = mock(TransaccionAlmacenHeaderRepo.class);
        configuracion = mock(OcmCierreConfigService.class);
        cierre = mock(OcmCierreService.class);
        recepcion = mock(RecepcionCompletaOcmService.class);
        contabilidad = mock(ContabilidadService.class);
        limites = mock(RecepcionOcmPolicyService.class);
        var materiales = mock(MaterialRepo.class);
        var lotes = mock(MaterialLoteGenerationService.class);
        var clock = Clock.fixed(Instant.parse("2026-09-22T15:00:00Z"), ZoneId.of("America/Bogota"));
        service = new MovimientosService(mock(TransaccionAlmacenRepo.class), mock(ProductoRepo.class), transacciones,
                ordenes, materiales, mock(LoteRepo.class), mock(UserRepository.class), contabilidad, limites, lotes,
                mock(ProduccionService.class), mock(OrdenProduccionRepo.class), clock, configuracion, cierre, recepcion);

        orden = new OrdenCompraMateriales();
        orden.setOrdenCompraId(7);
        orden.setEstado(2);
        var material = new Material();
        material.setProductoId("A");
        var movimiento = new Movimiento();
        movimiento.setProducto(material);
        movimiento.setCantidad(1);
        movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.COMPRA);
        movimiento.setAlmacen(Movimiento.Almacen.GENERAL);
        var transaccion = new TransaccionAlmacen();
        transaccion.setMovimientosTransaccion(List.of(movimiento));
        request = new IngresoOCM_DTA(transaccion, orden, null, "Prueba");
        when(configuracion.bloquearParaOperacion()).thenReturn(Config.desactivada());
        when(ordenes.findByOrdenCompraIdForUpdate(7)).thenReturn(Optional.of(orden));
        when(limites.resolverLimiteEfectivoRecepcionesParciales(orden)).thenReturn(2);
        when(materiales.findById("A")).thenReturn(Optional.of(material));
    }

    @Test
    void guardaMovimientosAntesDeEvaluarCierreYMantieneLaRespuestaExistente() {
        var response = service.createDocIngreso(request, null);
        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(TransaccionAlmacen.class, response.getBody());
        var transaccion = (TransaccionAlmacen) response.getBody();
        assertEquals(TransaccionAlmacen.EstadoContable.PENDIENTE, transaccion.getEstadoContable());
        var ordenOperaciones = inOrder(configuracion, ordenes, recepcion, transacciones, cierre);
        ordenOperaciones.verify(configuracion).bloquearParaOperacion();
        ordenOperaciones.verify(ordenes).findByOrdenCompraIdForUpdate(7);
        ordenOperaciones.verify(recepcion).estaCompleta(orden);
        ordenOperaciones.verify(transacciones).saveAndFlush(transaccion);
        ordenOperaciones.verify(cierre).actualizarRecepcion(orden, false, Config.desactivada());
        verifyNoInteractions(contabilidad);
    }

    @Test
    void noDevuelveExitoSiFallaElRegistroDeLaFechaOCierre() {
        var failure = new IllegalStateException("Fallo al actualizar cierre");
        doThrow(failure).when(cierre).actualizarRecepcion(orden, false, Config.desactivada());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.createDocIngreso(request, null)));
        // Unit test of exception propagation; PostgreSQL rollback must be verified in staging.
    }

    @Test
    void conservaElLimiteDeRecepcionesYRechazaUnaOcmYaCerrada() {
        when(transacciones.countByTipoEntidadCausanteAndIdEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.OCM, 7))
                .thenReturn(2L);
        assertEquals(409, service.createDocIngreso(request, null).getStatusCode().value());
        orden.setEstado(3);
        assertEquals(409, service.createDocIngreso(request, null).getStatusCode().value());
        verifyNoInteractions(cierre, recepcion);
        verify(transacciones, never()).saveAndFlush(any());
    }
}
