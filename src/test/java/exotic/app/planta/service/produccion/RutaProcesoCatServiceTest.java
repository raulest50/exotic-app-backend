package exotic.app.planta.service.produccion;

import exotic.app.planta.config.initializers.AreaOperativaInitializer;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatRepo;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoCatDTO;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoEdgeDTO;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoNodeDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RutaProcesoCatServiceTest {

    @Test
    void saveRuta_validDag_savesSuccessfully() {
        RutaProcesoCatService service = createService(0);

        assertDoesNotThrow(() -> service.saveRuta(10, buildLinearRuta()));
    }

    @Test
    void saveRuta_duplicateArea_throwsValidationError() {
        RutaProcesoCatService service = createService(0);
        RutaProcesoCatDTO dto = new RutaProcesoCatDTO();
        dto.setCategoriaId(10);
        dto.setNodes(List.of(
                node("1", AreaOperativaInitializer.ALMACEN_GENERAL_ID, "Almacen General"),
                node("2", 101, "Pesaje"),
                node("3", 101, "Pesaje")
        ));
        dto.setEdges(List.of(
                edge("e1", "1", "2"),
                edge("e2", "2", "3")
        ));

        assertThrows(IllegalArgumentException.class, () -> service.saveRuta(10, dto));
    }

    @Test
    void saveRuta_cycle_throwsValidationError() {
        RutaProcesoCatService service = createService(0);
        RutaProcesoCatDTO dto = new RutaProcesoCatDTO();
        dto.setCategoriaId(10);
        dto.setNodes(List.of(
                node("1", AreaOperativaInitializer.ALMACEN_GENERAL_ID, "Almacen General"),
                node("2", 101, "Pesaje"),
                node("3", 102, "Mezcla")
        ));
        dto.setEdges(List.of(
                edge("e1", "1", "2"),
                edge("e2", "2", "3"),
                edge("e3", "3", "2")
        ));

        assertThrows(IllegalArgumentException.class, () -> service.saveRuta(10, dto));
    }

    @Test
    void saveRuta_activeOrders_throwsConflict() {
        RutaProcesoCatService service = createService(2);

        assertThrows(IllegalStateException.class, () -> service.saveRuta(10, buildLinearRuta()));
    }

    private RutaProcesoCatService createService(long activeOrders) {
        RutaProcesoCatRepo rutaRepo = mock(RutaProcesoCatRepo.class);
        CategoriaRepo categoriaRepo = mock(CategoriaRepo.class);
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        OrdenProduccionRepo ordenRepo = mock(OrdenProduccionRepo.class);

        Categoria categoria = new Categoria();
        categoria.setCategoriaId(10);

        AreaOperativa almacen = new AreaOperativa();
        almacen.setAreaId(AreaOperativaInitializer.ALMACEN_GENERAL_ID);
        almacen.setNombre("Almacen General");

        AreaOperativa areaA = new AreaOperativa();
        areaA.setAreaId(101);
        areaA.setNombre("Pesaje");

        AreaOperativa areaB = new AreaOperativa();
        areaB.setAreaId(102);
        areaB.setNombre("Mezcla");

        when(categoriaRepo.findById(10)).thenReturn(Optional.of(categoria));
        when(rutaRepo.findByCategoria_CategoriaId(10)).thenReturn(Optional.empty());
        when(rutaRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(areaRepo.findById(AreaOperativaInitializer.ALMACEN_GENERAL_ID)).thenReturn(Optional.of(almacen));
        when(areaRepo.findById(101)).thenReturn(Optional.of(areaA));
        when(areaRepo.findById(102)).thenReturn(Optional.of(areaB));
        when(ordenRepo.countActiveByCategoriaId(10)).thenReturn(activeOrders);

        return new RutaProcesoCatService(rutaRepo, categoriaRepo, areaRepo, ordenRepo);
    }

    private RutaProcesoCatDTO buildLinearRuta() {
        RutaProcesoCatDTO dto = new RutaProcesoCatDTO();
        dto.setCategoriaId(10);
        dto.setNodes(List.of(
                node("1", AreaOperativaInitializer.ALMACEN_GENERAL_ID, "Almacen General"),
                node("2", 101, "Pesaje"),
                node("3", 102, "Mezcla")
        ));
        dto.setEdges(List.of(
                edge("e1", "1", "2"),
                edge("e2", "2", "3")
        ));
        return dto;
    }

    private RutaProcesoNodeDTO node(String id, Integer areaId, String nombre) {
        RutaProcesoNodeDTO node = new RutaProcesoNodeDTO();
        node.setId(id);
        node.setAreaOperativaId(areaId);
        node.setAreaOperativaNombre(nombre);
        node.setLabel(nombre);
        node.setPosicionX(0);
        node.setPosicionY(0);
        node.setHasLeftHandle(true);
        node.setHasRightHandle(true);
        return node;
    }

    private RutaProcesoEdgeDTO edge(String id, String source, String target) {
        RutaProcesoEdgeDTO edge = new RutaProcesoEdgeDTO();
        edge.setId(id);
        edge.setSourceNodeId(source);
        edge.setTargetNodeId(target);
        return edge;
    }
}
