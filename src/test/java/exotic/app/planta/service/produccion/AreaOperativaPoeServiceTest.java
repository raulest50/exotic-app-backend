package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.access.AccessDeniedException;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AreaOperativaPoeServiceTest {

    @Test
    void getDescarga_returnsFrozenPoeForResponsibleArea() {
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        ProcesoProduccionDocumentoService documentoService =
                mock(ProcesoProduccionDocumentoService.class);
        AreaOperativaPoeService service = new AreaOperativaPoeService(
                seguimientoRepo,
                documentoService
        );
        SeguimientoOrdenArea seguimiento = seguimientoConPoe(55, 1001L, 99L);
        ProcesoProduccionDocumentoService.DescargaDocumento descarga =
                new ProcesoProduccionDocumentoService.DescargaDocumento(
                        new ByteArrayResource(new byte[]{1}),
                        "POE.pdf",
                        "application/pdf",
                        1L,
                        "a".repeat(64)
                );
        when(seguimientoRepo.findPoeDetalleByIdAndOrdenId(1001L, 55))
                .thenReturn(Optional.of(seguimiento));
        when(documentoService.getDescarga(701, 9001L)).thenReturn(descarga);

        assertEquals(descarga, service.getDescarga(55, 1001L, 99L));
        verify(documentoService).getDescarga(701, 9001L);
    }

    @Test
    void getDescarga_forbidsLeaderFromAnotherArea() {
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        AreaOperativaPoeService service = new AreaOperativaPoeService(
                seguimientoRepo,
                mock(ProcesoProduccionDocumentoService.class)
        );
        when(seguimientoRepo.findPoeDetalleByIdAndOrdenId(1001L, 55))
                .thenReturn(Optional.of(seguimientoConPoe(55, 1001L, 99L)));

        assertThrows(AccessDeniedException.class,
                () -> service.getDescarga(55, 1001L, 77L));
    }

    @Test
    void getDescarga_rejectsStageWithoutFrozenPoe() {
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        AreaOperativaPoeService service = new AreaOperativaPoeService(
                seguimientoRepo,
                mock(ProcesoProduccionDocumentoService.class)
        );
        SeguimientoOrdenArea seguimiento = seguimientoConPoe(55, 1001L, 99L);
        seguimiento.setPoeDocumentoVersion(null);
        when(seguimientoRepo.findPoeDetalleByIdAndOrdenId(1001L, 55))
                .thenReturn(Optional.of(seguimiento));

        assertThrows(NoSuchElementException.class,
                () -> service.getDescarga(55, 1001L, 99L));
    }

    private SeguimientoOrdenArea seguimientoConPoe(int ordenId, Long seguimientoId, Long responsableId) {
        User responsable = new User();
        responsable.setId(responsableId);

        AreaOperativa area = new AreaOperativa();
        area.setAreaId(10);
        area.setResponsableArea(responsable);

        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(ordenId);

        ProcesoProduccion proceso = new ProcesoProduccion();
        proceso.setProcesoId(701);
        proceso.setNombre("Pesaje");

        ProcesoProduccionDocumentoVersion documento = new ProcesoProduccionDocumentoVersion();
        documento.setId(9001L);
        documento.setProceso(proceso);
        documento.setVersion(1);

        SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
        seguimiento.setId(seguimientoId);
        seguimiento.setOrdenProduccion(orden);
        seguimiento.setAreaOperativa(area);
        seguimiento.setPoeDocumentoVersion(documento);
        return seguimiento;
    }
}
