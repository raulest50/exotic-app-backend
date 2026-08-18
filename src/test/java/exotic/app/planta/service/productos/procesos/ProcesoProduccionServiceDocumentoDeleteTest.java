package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionCompleto;
import exotic.app.planta.model.producto.manufacturing.procesos.nodo.NodoProceso;
import exotic.app.planta.model.producto.manufacturing.procesos.nodo.ProcesoFabricacionNodo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionCompletoRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionDocumentoVersionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import exotic.app.planta.repo.producto.procesos.RecursoProduccionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoNodeRepo;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcesoProduccionServiceDocumentoDeleteTest {

    private final ProcesoProduccionRepo procesoRepo = mock(ProcesoProduccionRepo.class);
    private final ProcesoProduccionCompletoRepo completoRepo = mock(ProcesoProduccionCompletoRepo.class);
    private final ProcesoProduccionDocumentoVersionRepo documentoRepo =
            mock(ProcesoProduccionDocumentoVersionRepo.class);
    private final RutaProcesoNodeRepo rutaProcesoNodeRepo = mock(RutaProcesoNodeRepo.class);
    private final RecursoProduccionRepo recursoRepo = mock(RecursoProduccionRepo.class);
    private final ProcesoRecursoService recursoService = mock(ProcesoRecursoService.class);
    private final ProcesoProduccionService service = new ProcesoProduccionService(
            procesoRepo,
            completoRepo,
            documentoRepo,
            rutaProcesoNodeRepo,
            recursoRepo,
            recursoService
    );

    @Test
    void bloqueaEliminarProcesoConHistorialDocumental() {
        when(procesoRepo.existsById(8)).thenReturn(true);
        when(documentoRepo.countByProcesoProcesoId(8)).thenReturn(3L);

        assertThatThrownBy(() -> service.deleteProcesoProduccion(8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("historial documental");
        verify(procesoRepo, never()).deleteById(8);
    }

    @Test
    void informaQueProcesoDocumentadoNoEsEliminable() {
        when(procesoRepo.existsById(8)).thenReturn(true);
        when(documentoRepo.countByProcesoProcesoId(8)).thenReturn(3L);

        Map<String, Object> result = service.isProcesoProduccionDeletable(8);

        assertThat(result.get("deletable")).isEqualTo(false);
        assertThat(result.get("documentVersionsCount")).isEqualTo(3L);
    }

    @Test
    void bloqueaEliminarProcesoReferenciadoPorRutaVersionada() {
        when(procesoRepo.existsById(8)).thenReturn(true);
        when(rutaProcesoNodeRepo.countByProcesoProduccion_ProcesoId(8)).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteProcesoProduccion(8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rutas de producción");
        verify(procesoRepo, never()).deleteById(8);
    }

    @Test
    void informaQueProcesoSinReferenciasEsEliminable() {
        givenUndocumentedProcess(8);
        when(completoRepo.count(any(Specification.class))).thenReturn(0L);

        Map<String, Object> result = service.isProcesoProduccionDeletable(8);

        assertThat(result).containsEntry("deletable", true);
    }

    @Test
    void informaCuantosProcesosCompletosReferencianElProceso() {
        givenUndocumentedProcess(8);
        when(completoRepo.count(any(Specification.class))).thenReturn(2L);

        Map<String, Object> result = service.isProcesoProduccionDeletable(8);

        assertThat(result)
                .containsEntry("deletable", false)
                .containsEntry("referencesCount", 2L);
    }

    @Test
    void bloqueaEliminarProcesoReferenciadoEnProcesoCompleto() {
        givenUndocumentedProcess(8);
        when(completoRepo.count(any(Specification.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteProcesoProduccion(8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("procesos completos");
        verify(procesoRepo, never()).deleteById(8);
    }

    @Test
    void eliminaProcesoSinDocumentosNiReferencias() {
        givenUndocumentedProcess(8);
        when(completoRepo.count(any(Specification.class))).thenReturn(0L);

        service.deleteProcesoProduccion(8);

        verify(procesoRepo).deleteById(8);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void construyeSpecificationConListJoinParaLaColeccionDeNodos() {
        givenUndocumentedProcess(8);
        when(completoRepo.count(any(Specification.class))).thenReturn(0L);
        service.isProcesoProduccionDeletable(8);

        ArgumentCaptor<Specification<ProcesoProduccionCompleto>> specificationCaptor =
                ArgumentCaptor.forClass(Specification.class);
        verify(completoRepo).count(specificationCaptor.capture());

        Root<ProcesoProduccionCompleto> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        ListJoin<ProcesoProduccionCompleto, ProcesoFabricacionNodo> nodeJoin = mock(ListJoin.class);
        ListJoin<ProcesoProduccionCompleto, NodoProceso> procesoNodeJoin = mock(ListJoin.class);
        Path<Object> procesoPath = mock(Path.class);
        Path<Object> procesoIdPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        doReturn(nodeJoin).when(root).joinList("nodes");
        when(criteriaBuilder.treat(nodeJoin, NodoProceso.class)).thenReturn(procesoNodeJoin);
        when(procesoNodeJoin.get("procesoProduccion")).thenReturn(procesoPath);
        when(procesoPath.get("procesoId")).thenReturn(procesoIdPath);
        when(criteriaBuilder.equal(procesoIdPath, 8)).thenReturn(predicate);

        Predicate actual = specificationCaptor.getValue().toPredicate(root, query, criteriaBuilder);

        assertThat(actual).isSameAs(predicate);
        verify(query).distinct(true);
        verify(root).joinList("nodes");
        verify(criteriaBuilder).treat(nodeJoin, NodoProceso.class);
    }

    private void givenUndocumentedProcess(int id) {
        when(procesoRepo.existsById(id)).thenReturn(true);
        when(documentoRepo.countByProcesoProcesoId(id)).thenReturn(0L);
    }
}
