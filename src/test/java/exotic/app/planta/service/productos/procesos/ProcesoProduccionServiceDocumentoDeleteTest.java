package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.repo.producto.procesos.ProcesoProduccionCompletoRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionDocumentoVersionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import exotic.app.planta.repo.producto.procesos.RecursoProduccionRepo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcesoProduccionServiceDocumentoDeleteTest {

    private final ProcesoProduccionRepo procesoRepo = mock(ProcesoProduccionRepo.class);
    private final ProcesoProduccionCompletoRepo completoRepo = mock(ProcesoProduccionCompletoRepo.class);
    private final ProcesoProduccionDocumentoVersionRepo documentoRepo =
            mock(ProcesoProduccionDocumentoVersionRepo.class);
    private final RecursoProduccionRepo recursoRepo = mock(RecursoProduccionRepo.class);
    private final ProcesoRecursoService recursoService = mock(ProcesoRecursoService.class);
    private final ProcesoProduccionService service = new ProcesoProduccionService(
            procesoRepo,
            completoRepo,
            documentoRepo,
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
}
