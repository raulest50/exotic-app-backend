package exotic.app.planta.service.produccion;

import exotic.app.planta.config.initializers.AreaOperativaInitializer;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCat;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionDocumentoVersionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatVersionRepo;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoCatDTO;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoEdgeDTO;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoLayoutUpdateDTO;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoNodeDTO;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoNodePositionDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RutaProcesoCatServiceLayoutTest {

    @Mock private RutaProcesoCatRepo rutaProcesoCatRepo;
    @Mock private RutaProcesoCatVersionRepo rutaProcesoCatVersionRepo;
    @Mock private CategoriaRepo categoriaRepo;
    @Mock private AreaProduccionRepo areaProduccionRepo;
    @Mock private ProcesoProduccionRepo procesoProduccionRepo;
    @Mock private ProcesoProduccionDocumentoVersionRepo procesoDocumentoVersionRepo;
    @InjectMocks private RutaProcesoCatService service;

    @Test
    void actualizaSoloCoordenadasYAuditoriaSinCrearVersionFuncional() {
        RutaProcesoCatVersion vigente = versionVigente();
        RutaProcesoNode primerNodo = vigente.getNodes().get(0);
        RutaProcesoNode segundoNodo = vigente.getNodes().get(1);
        RutaProcesoEdge edge = vigente.getEdges().get(0);
        when(rutaProcesoCatVersionRepo.findByCategoriaIdAndEstadoForUpdate(
                7, RutaProcesoCatVersion.Estado.VIGENTE)).thenReturn(Optional.of(vigente));

        RutaProcesoCatDTO result = service.updateLayout(
                7,
                31L,
                layout(2, position("almacen", 15.126, 25.124), position("mezcla", 35.0, 45.0)),
                " diagramador ");

        assertThat(result.getVersionId()).isEqualTo(31L);
        assertThat(result.getVersionNumber()).isEqualTo(4);
        assertThat(result.getEstado()).isEqualTo("VIGENTE");
        assertThat(result.getLayoutRevision()).isEqualTo(3);
        assertThat(result.getLayoutActualizadoEn()).isNotNull();
        assertThat(result.getLayoutActualizadoPor()).isEqualTo("diagramador");
        assertThat(primerNodo.getPosicionX()).isEqualTo(15.13);
        assertThat(primerNodo.getPosicionY()).isEqualTo(25.12);
        assertThat(segundoNodo.getPosicionX()).isEqualTo(35.0);
        assertThat(segundoNodo.getPosicionY()).isEqualTo(45.0);
        assertThat(vigente.getNodes()).containsExactly(primerNodo, segundoNodo);
        assertThat(vigente.getEdges()).containsExactly(edge);
        assertThat(edge.getSourceNode()).isSameAs(primerNodo);
        assertThat(edge.getTargetNode()).isSameAs(segundoNodo);
        assertThat(primerNodo.getLabel()).isEqualTo("Almacén");
        assertThat(segundoNodo.getLabel()).isEqualTo("Mezcla");
    }

    @Test
    void noOpNormalizadoNoIncrementaRevisionNiAuditoria() {
        RutaProcesoCatVersion vigente = versionVigente();
        vigente.getNodes().get(0).setPosicionX(10.123);
        vigente.getNodes().get(0).setPosicionY(20.124);
        when(rutaProcesoCatVersionRepo.findByCategoriaIdAndEstadoForUpdate(
                7, RutaProcesoCatVersion.Estado.VIGENTE)).thenReturn(Optional.of(vigente));

        RutaProcesoCatDTO result = service.updateLayout(
                7,
                31L,
                layout(2, position("almacen", 10.12, 20.12), position("mezcla", 30.0, 40.0)),
                "diagramador");

        assertThat(result.getLayoutRevision()).isEqualTo(2);
        assertThat(result.getLayoutActualizadoEn()).isNull();
        assertThat(result.getLayoutActualizadoPor()).isNull();
        assertThat(vigente.getNodes().get(0).getPosicionX()).isEqualTo(10.123);
    }

    @Test
    void rechazaRevisionObsoletaYVersionQueYaNoEsVigente() {
        RutaProcesoCatVersion vigente = versionVigente();
        when(rutaProcesoCatVersionRepo.findByCategoriaIdAndEstadoForUpdate(
                7, RutaProcesoCatVersion.Estado.VIGENTE)).thenReturn(Optional.of(vigente));

        assertThatThrownBy(() -> service.updateLayout(
                7, 31L, layout(1, position("almacen", 10.0, 20.0), position("mezcla", 30.0, 40.0)), "u"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("otro usuario");

        assertThatThrownBy(() -> service.updateLayout(
                7, 30L, layout(2, position("almacen", 10.0, 20.0), position("mezcla", 30.0, 40.0)), "u"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("versión vigente cambió");
    }

    @Test
    void rechazaConjuntosDeNodosIncompletosAdicionalesODuplicados() {
        RutaProcesoCatVersion vigente = versionVigente();
        when(rutaProcesoCatVersionRepo.findByCategoriaIdAndEstadoForUpdate(
                7, RutaProcesoCatVersion.Estado.VIGENTE)).thenReturn(Optional.of(vigente));

        assertThatThrownBy(() -> service.updateLayout(
                7, 31L, layout(2, position("almacen", 10.0, 20.0)), "u"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactamente");

        assertThatThrownBy(() -> service.updateLayout(
                7,
                31L,
                layout(2,
                        position("almacen", 10.0, 20.0),
                        position("mezcla", 30.0, 40.0),
                        position("adicional", 50.0, 60.0)),
                "u"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactamente");

        assertThatThrownBy(() -> service.updateLayout(
                7,
                31L,
                layout(2, position("almacen", 10.0, 20.0), position(" almacen ", 30.0, 40.0)),
                "u"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicados");
    }

    @Test
    void rechazaCoordenadasAusentesONoFinitas() {
        RutaProcesoNodePositionDTO missing = position("almacen", 10.0, 20.0);
        missing.setPosicionX(null);

        assertThatThrownBy(() -> service.updateLayout(
                7, 31L, layout(2, missing, position("mezcla", 30.0, 40.0)), "u"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finitos");
        assertThatThrownBy(() -> service.updateLayout(
                7,
                31L,
                layout(2, position("almacen", Double.NaN, 20.0), position("mezcla", 30.0, 40.0)),
                "u"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finitos");

        verifyNoInteractions(rutaProcesoCatVersionRepo);
    }

    @Test
    void nuevaVersionCompruebaLaMismaVigenteBloqueadaYLaRevisionVisual() {
        RutaProcesoCatVersion vigente = versionVigente();
        Categoria categoria = vigente.getRutaProcesoCat().getCategoria();
        when(categoriaRepo.findById(7)).thenReturn(Optional.of(categoria));
        when(rutaProcesoCatRepo.findByCategoria_CategoriaId(7))
                .thenReturn(Optional.of(vigente.getRutaProcesoCat()));
        when(rutaProcesoCatVersionRepo.findByCategoriaIdAndEstadoForUpdate(
                7, RutaProcesoCatVersion.Estado.VIGENTE)).thenReturn(Optional.of(vigente));

        RutaProcesoCatDTO request = validSemanticRoute();
        request.setVersionId(31L);
        request.setLayoutRevision(1);

        assertThatThrownBy(() -> service.saveRuta(7, request, "diagramador"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disposición visual cambió");

        verify(rutaProcesoCatVersionRepo).findByCategoriaIdAndEstadoForUpdate(
                7, RutaProcesoCatVersion.Estado.VIGENTE);
        verifyNoInteractions(areaProduccionRepo, procesoProduccionRepo);
    }

    private static RutaProcesoCatVersion versionVigente() {
        Categoria categoria = new Categoria();
        categoria.setCategoriaId(7);
        RutaProcesoCat ruta = new RutaProcesoCat();
        ruta.setId(11L);
        ruta.setCategoria(categoria);

        RutaProcesoCatVersion version = new RutaProcesoCatVersion();
        version.setId(31L);
        version.setRutaProcesoCat(ruta);
        version.setVersionNumber(4);
        version.setEstado(RutaProcesoCatVersion.Estado.VIGENTE);
        version.setVigenteDesde(LocalDateTime.of(2026, 9, 1, 8, 0));
        version.setCreadoEn(LocalDateTime.of(2026, 9, 1, 8, 0));
        version.setLayoutRevision(2);

        RutaProcesoNode almacen = persistedNode(100L, "almacen", "Almacén", 10.0, 20.0, version);
        RutaProcesoNode mezcla = persistedNode(101L, "mezcla", "Mezcla", 30.0, 40.0, version);
        version.getNodes().addAll(List.of(almacen, mezcla));

        RutaProcesoEdge edge = new RutaProcesoEdge();
        edge.setId(200L);
        edge.setFrontendId("e-almacen-mezcla");
        edge.setRutaProcesoCatVersion(version);
        edge.setSourceNode(almacen);
        edge.setTargetNode(mezcla);
        version.getEdges().add(edge);
        ruta.getVersions().add(version);
        return version;
    }

    private static RutaProcesoNode persistedNode(
            Long id,
            String frontendId,
            String label,
            double x,
            double y,
            RutaProcesoCatVersion version
    ) {
        RutaProcesoNode node = new RutaProcesoNode();
        node.setId(id);
        node.setFrontendId(frontendId);
        node.setLabel(label);
        node.setPosicionX(x);
        node.setPosicionY(y);
        node.setDuracionEstimadaMinutos(15);
        node.setRequiereJornadaLaboral(true);
        node.setRutaProcesoCatVersion(version);
        return node;
    }

    private static RutaProcesoLayoutUpdateDTO layout(
            int revision,
            RutaProcesoNodePositionDTO... positions
    ) {
        RutaProcesoLayoutUpdateDTO dto = new RutaProcesoLayoutUpdateDTO();
        dto.setLayoutRevision(revision);
        dto.setNodes(List.of(positions));
        return dto;
    }

    private static RutaProcesoNodePositionDTO position(String id, Double x, Double y) {
        RutaProcesoNodePositionDTO dto = new RutaProcesoNodePositionDTO();
        dto.setId(id);
        dto.setPosicionX(x);
        dto.setPosicionY(y);
        return dto;
    }

    private static RutaProcesoCatDTO validSemanticRoute() {
        RutaProcesoNodeDTO almacen = new RutaProcesoNodeDTO();
        almacen.setId("almacen");
        almacen.setAreaOperativaId(AreaOperativaInitializer.ALMACEN_GENERAL_ID);
        almacen.setLabel("Almacén");

        RutaProcesoNodeDTO mezcla = new RutaProcesoNodeDTO();
        mezcla.setId("mezcla");
        mezcla.setAreaOperativaId(12);
        mezcla.setProcesoProduccionId(22);
        mezcla.setLabel("Mezcla");

        RutaProcesoEdgeDTO edge = new RutaProcesoEdgeDTO();
        edge.setId("e-almacen-mezcla");
        edge.setSourceNodeId("almacen");
        edge.setTargetNodeId("mezcla");

        RutaProcesoCatDTO dto = new RutaProcesoCatDTO();
        dto.setCategoriaId(7);
        dto.setNodes(List.of(almacen, mezcla));
        dto.setEdges(List.of(edge));
        return dto;
    }
}
