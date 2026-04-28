package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.dto.InsumoWithStockDTO;
import exotic.app.planta.model.producto.manufacturing.packaging.CasePack;
import exotic.app.planta.model.producto.manufacturing.packaging.InsumoEmpaque;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCat;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatRepo;
import exotic.app.planta.service.productos.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AreaOperativaPanelDetalleServiceTest {

    @Test
    void getDetalleOperativoOrden_returnsAggregatedRouteAndBom() {
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        RutaProcesoCatRepo rutaRepo = mock(RutaProcesoCatRepo.class);
        ProductoService productoService = mock(ProductoService.class);

        AreaOperativaPanelDetalleService service = new AreaOperativaPanelDetalleService(
                seguimientoRepo,
                areaRepo,
                rutaRepo,
                productoService
        );

        Fixture fixture = buildFixture();

        when(areaRepo.findAllByResponsableArea_Id(99L)).thenReturn(List.of(fixture.areaPesaje));
        when(seguimientoRepo.findDetalleByOrdenId(55)).thenReturn(List.of(fixture.segAlmacen, fixture.segPesaje));
        when(rutaRepo.findByCategoria_CategoriaId(7)).thenReturn(Optional.of(fixture.ruta));
        when(productoService.getInsumosWithStock("TER-001")).thenReturn(List.of(fixture.insumoSemi));

        AreaOperativaPanelDetalleService.AreaOperativaOrdenDetalleDTO result =
                service.getDetalleOperativoOrden(55, 99L);

        assertEquals(55, result.getOrden().getOrdenId());
        assertEquals(2, result.getSeguimiento().size());
        assertEquals(2, result.getRutaProceso().getNodes().size());
        assertEquals(1, result.getRutaProceso().getEdges().size());
        assertEquals(1, result.getBom().getReceta().size());
        assertEquals(1, result.getBom().getEmpaque().size());
        assertEquals(10.0, result.getBom().getReceta().get(0).getCantidadTotalRequerida());
        assertEquals(20.0, result.getBom().getReceta().get(0).getSubInsumos().get(0).getCantidadTotalRequerida());
        assertEquals(10.0, result.getBom().getEmpaque().get(0).getCantidadTotalRequerida());
    }

    @Test
    void getDetalleOperativoOrden_forbiddenWhenOrderIsNotFromResponsibleArea() {
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        RutaProcesoCatRepo rutaRepo = mock(RutaProcesoCatRepo.class);
        ProductoService productoService = mock(ProductoService.class);

        AreaOperativaPanelDetalleService service = new AreaOperativaPanelDetalleService(
                seguimientoRepo,
                areaRepo,
                rutaRepo,
                productoService
        );

        Fixture fixture = buildFixture();
        when(areaRepo.findAllByResponsableArea_Id(99L)).thenReturn(List.of(fixture.areaExtrusion));
        when(seguimientoRepo.findDetalleByOrdenId(55)).thenReturn(List.of(fixture.segAlmacen, fixture.segPesaje));

        assertThrows(AccessDeniedException.class, () -> service.getDetalleOperativoOrden(55, 99L));
    }

    @Test
    void getDetalleOperativoOrden_notFoundWhenNoSeguimientoExists() {
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        RutaProcesoCatRepo rutaRepo = mock(RutaProcesoCatRepo.class);
        ProductoService productoService = mock(ProductoService.class);

        AreaOperativaPanelDetalleService service = new AreaOperativaPanelDetalleService(
                seguimientoRepo,
                areaRepo,
                rutaRepo,
                productoService
        );

        when(areaRepo.findAllByResponsableArea_Id(99L)).thenReturn(List.of(new AreaOperativa()));
        when(seguimientoRepo.findDetalleByOrdenId(55)).thenReturn(List.of());

        assertThrows(NoSuchElementException.class, () -> service.getDetalleOperativoOrden(55, 99L));
    }

    private Fixture buildFixture() {
        Fixture fixture = new Fixture();

        fixture.areaAlmacen = new AreaOperativa();
        fixture.areaAlmacen.setAreaId(-1);
        fixture.areaAlmacen.setNombre("Almacen General");

        fixture.areaPesaje = new AreaOperativa();
        fixture.areaPesaje.setAreaId(10);
        fixture.areaPesaje.setNombre("Pesaje");

        fixture.areaExtrusion = new AreaOperativa();
        fixture.areaExtrusion.setAreaId(22);
        fixture.areaExtrusion.setNombre("Extrusion");

        Categoria categoria = new Categoria();
        categoria.setCategoriaId(7);
        categoria.setCategoriaNombre("Cuidado capilar");

        Terminado terminado = new Terminado();
        terminado.setProductoId("TER-001");
        terminado.setNombre("Shampoo");
        terminado.setCategoria(categoria);

        CasePack casePack = new CasePack();
        Material materialEmpaque = new Material();
        materialEmpaque.setProductoId("MAT-EMP");
        materialEmpaque.setNombre("Botella");
        materialEmpaque.setTipoUnidades("U");
        materialEmpaque.setInventareable(true);

        InsumoEmpaque insumoEmpaque = new InsumoEmpaque();
        insumoEmpaque.setMaterial(materialEmpaque);
        insumoEmpaque.setCantidad(5.0);
        insumoEmpaque.setUom("U");
        casePack.setInsumosEmpaque(List.of(insumoEmpaque));
        terminado.setCasePack(casePack);

        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(55);
        orden.setProducto(terminado);
        orden.setCantidadProducir(2.0);
        orden.setLoteAsignado("L-2026-001");

        fixture.ruta = new RutaProcesoCat();
        fixture.ruta.setCategoria(categoria);

        RutaProcesoNode nodeAlmacen = new RutaProcesoNode();
        nodeAlmacen.setId(1L);
        nodeAlmacen.setFrontendId("1");
        nodeAlmacen.setLabel("Almacen");
        nodeAlmacen.setAreaOperativa(fixture.areaAlmacen);
        nodeAlmacen.setPosicionX(0);
        nodeAlmacen.setPosicionY(0);
        nodeAlmacen.setRutaProcesoCat(fixture.ruta);

        RutaProcesoNode nodePesaje = new RutaProcesoNode();
        nodePesaje.setId(2L);
        nodePesaje.setFrontendId("2");
        nodePesaje.setLabel("Pesaje");
        nodePesaje.setAreaOperativa(fixture.areaPesaje);
        nodePesaje.setPosicionX(300);
        nodePesaje.setPosicionY(0);
        nodePesaje.setRutaProcesoCat(fixture.ruta);

        RutaProcesoEdge edge = new RutaProcesoEdge();
        edge.setId(100L);
        edge.setFrontendId("e1-2");
        edge.setSourceNode(nodeAlmacen);
        edge.setTargetNode(nodePesaje);
        edge.setRutaProcesoCat(fixture.ruta);

        fixture.ruta.setNodes(List.of(nodeAlmacen, nodePesaje));
        fixture.ruta.setEdges(List.of(edge));

        fixture.segAlmacen = new SeguimientoOrdenArea();
        fixture.segAlmacen.setId(1000L);
        fixture.segAlmacen.setOrdenProduccion(orden);
        fixture.segAlmacen.setRutaProcesoNode(nodeAlmacen);
        fixture.segAlmacen.setAreaOperativa(fixture.areaAlmacen);
        fixture.segAlmacen.setEstado(2);
        fixture.segAlmacen.setPosicionSecuencia(0);

        fixture.segPesaje = new SeguimientoOrdenArea();
        fixture.segPesaje.setId(1001L);
        fixture.segPesaje.setOrdenProduccion(orden);
        fixture.segPesaje.setRutaProcesoNode(nodePesaje);
        fixture.segPesaje.setAreaOperativa(fixture.areaPesaje);
        fixture.segPesaje.setEstado(4);
        fixture.segPesaje.setPosicionSecuencia(1);

        fixture.insumoSemi = new InsumoWithStockDTO();
        fixture.insumoSemi.setInsumoId(1);
        fixture.insumoSemi.setProductoId("SEMI-01");
        fixture.insumoSemi.setProductoNombre("Base");
        fixture.insumoSemi.setCantidadRequerida(5.0);
        fixture.insumoSemi.setTipoUnidades("KG");
        fixture.insumoSemi.setInventareable(true);
        fixture.insumoSemi.setTipoProducto(InsumoWithStockDTO.TipoProducto.S);

        InsumoWithStockDTO subMaterial = new InsumoWithStockDTO();
        subMaterial.setInsumoId(2);
        subMaterial.setProductoId("MAT-01");
        subMaterial.setProductoNombre("Tensoactivo");
        subMaterial.setCantidadRequerida(2.0);
        subMaterial.setTipoUnidades("KG");
        subMaterial.setInventareable(true);
        subMaterial.setTipoProducto(InsumoWithStockDTO.TipoProducto.M);
        fixture.insumoSemi.setSubInsumos(List.of(subMaterial));

        return fixture;
    }

    private static class Fixture {
        private AreaOperativa areaAlmacen;
        private AreaOperativa areaPesaje;
        private AreaOperativa areaExtrusion;
        private RutaProcesoCat ruta;
        private SeguimientoOrdenArea segAlmacen;
        private SeguimientoOrdenArea segPesaje;
        private InsumoWithStockDTO insumoSemi;
    }
}
