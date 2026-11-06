package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.model.producto.*;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.*;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatVersionRepo;
import exotic.app.planta.service.controles.ControlWorkflowService;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BatchRecordProductoProxyTest {

    @Test
    void aceptaTerminadoCargadoMedianteProxySinRelajarLasInvariantes() {
        Terminado terminado = terminado();
        BatchRecord record = record(proxy(terminado), terminado);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(record, "validarInvariantes"));

        Material material = new Material();
        material.setProductoId(terminado.getProductoId());
        record.setProductoResultado(proxy(material));
        assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(record, "validarInvariantes"));

        record.setProductoResultado(proxy(terminado));
        Terminado otro = terminado();
        otro.setProductoId("OTRO");
        record.getLoteResultado().setProducto(otro);
        assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(record, "validarInvariantes"));
    }

    @Test
    void asignaPlanPorCategoriaCuandoElProductoEsUnProxy() {
        Terminado terminado = terminado();
        Categoria categoria = new Categoria();
        categoria.setCategoriaId(1);
        terminado.setCategoria(categoria);
        BatchRecord record = record(proxy(terminado), terminado);
        record.setId(1L);
        record.getOrdenProduccion().setFechaCreacion(LocalDateTime.of(2026, 9, 21, 12, 0));

        PlanControl plan = new PlanControl();
        plan.setId(1L);
        plan.setCodigo("TEST");
        plan.setAmbito(AmbitoControl.CALIDAD);
        VersionPlanControl version = new VersionPlanControl();
        version.setId(1L);
        version.setPlan(plan);
        version.setNumero(1);
        version.setEstado(EstadoVersionPlanControl.VIGENTE);
        version.setPublicadaEn(LocalDateTime.of(2026, 9, 20, 12, 0));
        AplicabilidadPlanControl regla = new AplicabilidadPlanControl();
        regla.setCategoria(categoria);
        regla.setTipoOrden(TipoOrdenControl.OP);
        regla.setPuntoAplicacion(PuntoAplicacionControl.LOTE_FINAL);
        version.setAplicabilidades(List.of(regla));

        VersionPlanControlRepo versiones = mock(VersionPlanControlRepo.class);
        ControlRequeridoRepo requeridos = mock(ControlRequeridoRepo.class);
        when(versiones.findByEstadoIn(any())).thenReturn(List.of(version));
        when(requeridos.save(any(ControlRequerido.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ControlWorkflowService workflow = new ControlWorkflowService(versiones, requeridos,
                mock(DesviacionControlRepo.class), mock(RevalidacionControlRepo.class));

        List<ControlRequerido> result = workflow.materializarRequisitos(record);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getCategoriaIdSnapshot());
        assertSame(version, result.get(0).getVersionPlan());
    }

    @Test
    void conservaEmpaqueYConsumoDirectoAlCongelarProductosConProxy() {
        Terminado terminado = terminado();
        ManufacturingVersions version = new ManufacturingVersions();
        version.setProducto(terminado);
        version.setInsumosJson("[]");
        version.setCasePackJson("""
                {"unitsPerCase": 10, "insumosEmpaque": [
                    {"materialId": "CAJA", "cantidad": 1, "uom": "UND"}
                ]}
                """);
        Material caja = new Material();
        caja.setProductoId("CAJA");
        caja.setNombre("Caja");
        caja.setConsumoDirecto(true);
        Producto cajaProxy = proxy(caja);
        ProductoRepo productos = mock(ProductoRepo.class);
        when(productos.findById("CAJA")).thenReturn(Optional.of(cajaProxy));
        MaterialRequirementSnapshotService service = new MaterialRequirementSnapshotService(
                productos, mock(ManufacturingVersionRepo.class), new ObjectMapper(),
                mock(RutaProcesoCatVersionRepo.class));

        var requirements = service.leer(service.construirJson(proxy(terminado), version, BigDecimal.TEN));

        assertEquals(1, requirements.size());
        assertEquals("MATERIAL_EMPAQUE", requirements.get(0).tipoProducto());
        assertEquals(0, BigDecimal.ONE.compareTo(requirements.get(0).cantidad()));
        assertTrue(requirements.get(0).consumoDirecto());
    }

    @Test
    void snapshotOperativoConservaCantidadUnidadYEtapaAunqueCambienRecetaYRuta() {
        OperativoFixture fixture = operativo(true);
        fixture.service().congelarParaOrdenProduccion(fixture.orden());

        fixture.version().setInsumosJson("[]");
        fixture.nodo().getSemiterminadosConsumidos().clear();
        fixture.semi().setRequiereOrdenFabricacion(false);
        fixture.semi().setTipoUnidades("L");

        var requisitos = fixture.service().requerimientosEtapa(fixture.orden(), 101L);
        assertEquals(1, requisitos.size());
        assertEquals(0, new BigDecimal("12.500000").compareTo(requisitos.getFirst().cantidad()));
        assertEquals("KG", requisitos.getFirst().unidadMedida());
        assertEquals(12, requisitos.getFirst().areaOperativaId());
        assertTrue(requisitos.getFirst().esSemiterminadoFisico());
        assertEquals(1, fixture.service().requerimientosAlmacen(fixture.orden()).size());
        assertEquals("M1", fixture.service().requerimientosAlmacen(fixture.orden()).getFirst().productoId());
        assertThrows(IllegalStateException.class,
                () -> fixture.service().congelarParaOrdenProduccion(fixture.orden()));
    }

    @Test
    void semiterminadoFisicoSinEtapaImpideEmitirLaOp() {
        OperativoFixture fixture = operativo(false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.service().congelarParaOrdenProduccion(fixture.orden()));

        assertTrue(error.getMessage().contains("S1"));
        assertNull(fixture.orden().getRequerimientosMaterialesJson());
    }

    @Test
    void noAtribuyeRequerimientosActualesAUnaOrdenHistorica() {
        OperativoFixture fixture = operativo(true);
        fixture.orden().setOrdenId(77);

        assertThrows(IllegalStateException.class,
                () -> fixture.service().congelarParaOrdenProduccion(fixture.orden()));
        assertNull(fixture.orden().getRequerimientosMaterialesJson());
    }

    private OperativoFixture operativo(boolean asignar) {
        Terminado terminado = terminado();
        terminado.setTipoUnidades("U");
        SemiTerminado semi = new SemiTerminado();
        semi.setProductoId("S1");
        semi.setNombre("Base neutra");
        semi.setTipoUnidades("KG");
        semi.setRequiereOrdenFabricacion(true);
        Material material = new Material();
        material.setProductoId("M1");
        material.setTipoUnidades("KG");
        ProductoRepo productos = mock(ProductoRepo.class);
        when(productos.findById("S1")).thenReturn(Optional.of(semi));
        when(productos.findById("M1")).thenReturn(Optional.of(material));
        MaterialRequirementSnapshotService service = new MaterialRequirementSnapshotService(
                productos, mock(ManufacturingVersionRepo.class), new ObjectMapper(),
                mock(RutaProcesoCatVersionRepo.class));
        ManufacturingVersions version = new ManufacturingVersions();
        version.setProducto(terminado);
        version.setInsumosJson("""
                [{"productoId":"S1","cantidadRequerida":1.25},
                 {"productoId":"M1","cantidadRequerida":0.1}]
                """);
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(12);
        RutaProcesoCatVersion ruta = new RutaProcesoCatVersion();
        ruta.setId(31L);
        RutaProcesoNode nodo = new RutaProcesoNode();
        nodo.setId(101L);
        nodo.setAreaOperativa(area);
        nodo.setRutaProcesoCatVersion(ruta);
        if (asignar) nodo.getSemiterminadosConsumidos().add(semi);
        ruta.getNodes().add(nodo);
        OrdenProduccion orden = new OrdenProduccion();
        orden.setProducto(terminado);
        orden.setManufacturingVersion(version);
        orden.setRutaProcesoCatVersion(ruta);
        orden.setCantidadProducir(10);
        return new OperativoFixture(service, orden, semi, nodo, version);
    }

    private record OperativoFixture(MaterialRequirementSnapshotService service, OrdenProduccion orden,
                                    SemiTerminado semi, RutaProcesoNode nodo, ManufacturingVersions version) {}

    private Terminado terminado() {
        Terminado terminado = new Terminado();
        terminado.setProductoId("TEST-PT");
        return terminado;
    }

    private Producto proxy(Producto implementation) {
        Producto proxy = mock(Producto.class, withSettings().extraInterfaces(HibernateProxy.class));
        LazyInitializer initializer = mock(LazyInitializer.class);
        when(((HibernateProxy) proxy).asHibernateProxy()).thenReturn((HibernateProxy) proxy);
        when(((HibernateProxy) proxy).getHibernateLazyInitializer()).thenReturn(initializer);
        when(initializer.getImplementation()).thenReturn(implementation);
        when(proxy.getProductoId()).thenReturn(implementation.getProductoId());
        return proxy;
    }

    private BatchRecord record(Producto resultado, Terminado terminado) {
        ManufacturingVersions version = new ManufacturingVersions();
        version.setId(1L);
        version.setProducto(terminado);
        OrdenProduccion orden = new OrdenProduccion();
        orden.setProducto(terminado);
        orden.setManufacturingVersion(version);
        Lote lote = new Lote();
        lote.setProducto(terminado);
        lote.setOrdenProduccion(orden);
        BatchRecord record = new BatchRecord();
        record.setCodigo("BR-TEST");
        record.setProductoResultado(resultado);
        record.setManufacturingVersion(version);
        record.setOrdenProduccion(orden);
        record.setLoteResultado(lote);
        record.setCreadoPor(new User());
        record.setCantidadPlanificada(BigDecimal.TEN);
        record.setUnidadMedida("UND");
        return record;
    }
}
