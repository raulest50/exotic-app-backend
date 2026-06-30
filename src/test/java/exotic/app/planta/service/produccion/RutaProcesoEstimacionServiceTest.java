package exotic.app.planta.service.produccion;

import exotic.app.planta.model.empresa.JornadaLaboralBloque;
import exotic.app.planta.model.empresa.JornadaLaboralVersion;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.repo.empresa.JornadaLaboralVersionRepo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RutaProcesoEstimacionServiceTest {

    @Test
    void estimarOrden_usesCriticalPathForParallelBranches() {
        JornadaLaboralVersionRepo jornadaRepo = mock(JornadaLaboralVersionRepo.class);
        when(jornadaRepo.findFirstByEstadoOrderByVersionDesc(JornadaLaboralVersion.Estado.VIGENTE))
                .thenReturn(Optional.empty());
        RutaProcesoEstimacionService service = new RutaProcesoEstimacionService(jornadaRepo);
        OrdenProduccion orden = new OrdenProduccion();
        orden.setFechaLanzamiento(LocalDateTime.of(2026, 6, 29, 8, 0));

        RutaProcesoCatVersion version = new RutaProcesoCatVersion();
        orden.setRutaProcesoCatVersion(version);

        RutaProcesoNode root = node(1L, version);
        RutaProcesoNode branchLong = node(2L, version);
        RutaProcesoNode branchShort = node(3L, version);
        version.setNodes(List.of(root, branchLong, branchShort));
        version.setEdges(List.of(edge(root, branchLong, version), edge(root, branchShort, version)));

        RutaProcesoEstimacionService.RutaProcesoEstimacionDTO result = service.estimarOrden(
                orden,
                List.of(
                        seguimiento(root, orden, 60, false, 0),
                        seguimiento(branchLong, orden, 120, false, 1),
                        seguimiento(branchShort, orden, 30, false, 2)
                )
        );

        assertEquals(LocalDateTime.of(2026, 6, 29, 11, 0), result.getFechaFinalEstimada());
        assertEquals(180L, result.getDuracionCalendarioRutaCriticaMinutos());
    }

    @Test
    void estimarOrden_consumesOnlyWorkingBlocksWhenRequired() {
        RutaProcesoEstimacionService service = new RutaProcesoEstimacionService(mock(JornadaLaboralVersionRepo.class));
        OrdenProduccion orden = new OrdenProduccion();
        orden.setFechaLanzamiento(LocalDateTime.of(2026, 6, 29, 16, 0));
        orden.setJornadaLaboralVersion(jornadaLaboral());

        RutaProcesoCatVersion version = new RutaProcesoCatVersion();
        orden.setRutaProcesoCatVersion(version);

        RutaProcesoNode node = node(1L, version);
        version.setNodes(List.of(node));
        version.setEdges(List.of());

        RutaProcesoEstimacionService.RutaProcesoEstimacionDTO result = service.estimarOrden(
                orden,
                List.of(seguimiento(node, orden, 120, true, 0))
        );

        assertEquals(LocalDateTime.of(2026, 6, 30, 9, 0), result.getFechaFinalEstimada());
        assertEquals(1020L, result.getDuracionCalendarioRutaCriticaMinutos());
    }

    private static RutaProcesoNode node(Long id, RutaProcesoCatVersion version) {
        RutaProcesoNode node = new RutaProcesoNode();
        node.setId(id);
        node.setRutaProcesoCatVersion(version);
        return node;
    }

    private static RutaProcesoEdge edge(
            RutaProcesoNode source,
            RutaProcesoNode target,
            RutaProcesoCatVersion version
    ) {
        RutaProcesoEdge edge = new RutaProcesoEdge();
        edge.setSourceNode(source);
        edge.setTargetNode(target);
        edge.setRutaProcesoCatVersion(version);
        return edge;
    }

    private static SeguimientoOrdenArea seguimiento(
            RutaProcesoNode node,
            OrdenProduccion orden,
            int duracionMinutos,
            boolean requiereJornada,
            int posicion
    ) {
        SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
        seguimiento.setRutaProcesoNode(node);
        seguimiento.setOrdenProduccion(orden);
        seguimiento.setDuracionEstimadaMinutos(duracionMinutos);
        seguimiento.setRequiereJornadaLaboral(requiereJornada);
        seguimiento.setPosicionSecuencia(posicion);
        return seguimiento;
    }

    private static JornadaLaboralVersion jornadaLaboral() {
        JornadaLaboralVersion version = new JornadaLaboralVersion();
        version.setId(77L);
        version.setBloques(List.of(
                bloque(version, 1, 0, LocalTime.of(8, 0), LocalTime.of(17, 0)),
                bloque(version, 2, 0, LocalTime.of(8, 0), LocalTime.of(17, 0))
        ));
        return version;
    }

    private static JornadaLaboralBloque bloque(
            JornadaLaboralVersion version,
            int diaSemana,
            int orden,
            LocalTime inicio,
            LocalTime fin
    ) {
        JornadaLaboralBloque bloque = new JornadaLaboralBloque();
        bloque.setJornadaLaboralVersion(version);
        bloque.setDiaSemana(diaSemana);
        bloque.setOrden(orden);
        bloque.setHoraInicio(inicio);
        bloque.setHoraFin(fin);
        return bloque;
    }
}
