package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaterialRequirementSnapshotServiceTest {

    @Test
    void construirJson_conservaComoHojaElSemiterminadoConOrdenPropia() {
        ProductoRepo productoRepo = mock(ProductoRepo.class);
        ManufacturingVersionRepo versionRepo = mock(ManufacturingVersionRepo.class);
        MaterialRequirementSnapshotService service = new MaterialRequirementSnapshotService(
                productoRepo, versionRepo, new ObjectMapper());

        Terminado terminado = terminado("TER-001", "U");
        SemiTerminado granel = semi("GRANEL-001", "KG", true);
        ManufacturingVersions version = version(
                terminado,
                "[{\"productoId\":\"GRANEL-001\",\"cantidadRequerida\":2}]");
        when(productoRepo.findById("GRANEL-001")).thenReturn(Optional.of(granel));

        var requirements = service.leer(service.construirJson(
                terminado, version, new BigDecimal("10")));

        assertEquals(1, requirements.size());
        assertEquals("GRANEL-001", requirements.getFirst().productoId());
        assertEquals(new BigDecimal("20.000000"), requirements.getFirst().cantidad());
        assertTrue(requirements.getFirst().inventareable());
        assertTrue(service.requiereRegistroDispensacion(
                service.construirJson(terminado, version, new BigDecimal("10"))));
    }

    @Test
    void calcularOrdenesFabricacion_agregaDemandasAnidadasSinPerderNiveles() {
        ProductoRepo productoRepo = mock(ProductoRepo.class);
        ManufacturingVersionRepo versionRepo = mock(ManufacturingVersionRepo.class);
        MaterialRequirementSnapshotService service = new MaterialRequirementSnapshotService(
                productoRepo, versionRepo, new ObjectMapper());

        Terminado terminado = terminado("TER-001", "U");
        SemiTerminado granel = semi("GRANEL-001", "KG", true);
        SemiTerminado concentrado = semi("CONC-001", "KG", true);
        ManufacturingVersions versionTerminado = version(
                terminado,
                "[{\"productoId\":\"GRANEL-001\",\"cantidadRequerida\":2}]");
        ManufacturingVersions versionGranel = version(
                granel,
                "[{\"productoId\":\"CONC-001\",\"cantidadRequerida\":3}]");
        ManufacturingVersions versionConcentrado = version(concentrado, "[]");
        when(productoRepo.findById("GRANEL-001")).thenReturn(Optional.of(granel));
        when(productoRepo.findById("CONC-001")).thenReturn(Optional.of(concentrado));
        when(versionRepo.findTopByProductoOrderByVersionNumberDesc(granel))
                .thenReturn(Optional.of(versionGranel));
        when(versionRepo.findTopByProductoOrderByVersionNumberDesc(concentrado))
                .thenReturn(Optional.of(versionConcentrado));

        Map<SemiTerminado, BigDecimal> demandas = service.calcularOrdenesFabricacion(
                versionTerminado, new BigDecimal("10"));

        assertEquals(new BigDecimal("20.000000"), demandas.get(granel));
        assertEquals(new BigDecimal("60.000000"), demandas.get(concentrado));
        assertFalse(demandas.isEmpty());
    }

    private Terminado terminado(String id, String unidad) {
        Terminado producto = new Terminado();
        producto.setProductoId(id);
        producto.setNombre(id);
        producto.setTipoUnidades(unidad);
        producto.setInventareable(true);
        return producto;
    }

    private SemiTerminado semi(String id, String unidad, boolean requiereOrden) {
        SemiTerminado producto = new SemiTerminado();
        producto.setProductoId(id);
        producto.setNombre(id);
        producto.setTipoUnidades(unidad);
        producto.setInventareable(requiereOrden);
        producto.setRequiereOrdenFabricacion(requiereOrden);
        return producto;
    }

    private ManufacturingVersions version(
            exotic.app.planta.model.producto.Producto producto,
            String insumosJson
    ) {
        ManufacturingVersions version = new ManufacturingVersions();
        version.setProducto(producto);
        version.setInsumosJson(insumosJson);
        return version;
    }
}
