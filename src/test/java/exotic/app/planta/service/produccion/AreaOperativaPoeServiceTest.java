package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoPdfService;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AreaOperativaPoeServiceTest {

    @Mock
    private SeguimientoOrdenAreaRepo seguimientoRepo;
    @Mock
    private OrdenFabricacionOperacionRepo operacionRepo;
    @Mock
    private ProcesoProduccionDocumentoService documentoService;
    @Mock
    private ProcesoProduccionDocumentoPdfService pdfService;
    @InjectMocks
    private AreaOperativaPoeService service;

    @Test
    void entregaLaVersionCongeladaAunqueHayaSidoRetirada() {
        ProcesoProduccionDocumentoVersion documento = documentoRetirado();
        SeguimientoOrdenArea seguimiento = org.mockito.Mockito.mock(SeguimientoOrdenArea.class);
        when(seguimiento.getAreaOperativa()).thenReturn(area(9L));
        when(seguimiento.getPoeDocumentoVersion()).thenReturn(documento);
        when(seguimientoRepo.findPoeDetalleByIdAndOrdenId(81L, 701))
                .thenReturn(Optional.of(seguimiento));
        ProcesoProduccionDocumentoService.DescargaDocumento descarga = descarga();
        when(documentoService.getDescarga(33, 15L)).thenReturn(descarga);
        ProcesoProduccionDocumentoPdfService.DocumentoPdf esperado = pdf();
        when(pdfService.renderizar(descarga)).thenReturn(esperado);

        assertThat(service.getDescarga(701, 81L, 9L)).isSameAs(esperado);
        verify(documentoService).getDescarga(33, 15L);
    }

    @Test
    void rechazaElPoeDeUnaEtapaAsignadaAOtroResponsable() {
        SeguimientoOrdenArea seguimiento = org.mockito.Mockito.mock(SeguimientoOrdenArea.class);
        when(seguimiento.getAreaOperativa()).thenReturn(area(10L));
        when(seguimientoRepo.findPoeDetalleByIdAndOrdenId(81L, 701))
                .thenReturn(Optional.of(seguimiento));

        assertThatThrownBy(() -> service.getDescarga(701, 81L, 9L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("responsable del area");
        verifyNoInteractions(documentoService, pdfService);
    }

    @Test
    void aplicaLaMismaAutorizacionALaOperacionDeFabricacion() {
        OrdenFabricacionOperacion operacion =
                org.mockito.Mockito.mock(OrdenFabricacionOperacion.class);
        when(operacion.getAreaOperativa()).thenReturn(area(10L));
        when(operacionRepo.findPoeDetalle(55L, 66L)).thenReturn(Optional.of(operacion));

        assertThatThrownBy(() -> service.getDescargaFabricacion(55L, 66L, 9L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("responsable del area");
        verifyNoInteractions(documentoService, pdfService);
    }

    @Test
    void entregaElPoeDeFabricacionAlResponsableDeLaOperacion() {
        ProcesoProduccionDocumentoVersion documento = documentoRetirado();
        OrdenFabricacionOperacion operacion =
                org.mockito.Mockito.mock(OrdenFabricacionOperacion.class);
        when(operacion.getAreaOperativa()).thenReturn(area(9L));
        when(operacion.getPoeDocumentoVersion()).thenReturn(documento);
        when(operacionRepo.findPoeDetalle(55L, 66L)).thenReturn(Optional.of(operacion));
        ProcesoProduccionDocumentoService.DescargaDocumento descarga = descarga();
        when(documentoService.getDescarga(33, 15L)).thenReturn(descarga);
        ProcesoProduccionDocumentoPdfService.DocumentoPdf esperado = pdf();
        when(pdfService.renderizar(descarga)).thenReturn(esperado);

        assertThat(service.getDescargaFabricacion(55L, 66L, 9L)).isSameAs(esperado);
        verify(documentoService).getDescarga(33, 15L);
    }

    private static AreaOperativa area(Long responsableId) {
        User responsable = new User();
        responsable.setId(responsableId);
        AreaOperativa area = new AreaOperativa();
        area.setResponsableArea(responsable);
        return area;
    }

    private static ProcesoProduccionDocumentoVersion documentoRetirado() {
        ProcesoProduccion proceso = org.mockito.Mockito.mock(ProcesoProduccion.class);
        when(proceso.getProcesoId()).thenReturn(33);
        ProcesoProduccionDocumentoVersion documento =
                new ProcesoProduccionDocumentoVersion();
        documento.setId(15L);
        documento.setProceso(proceso);
        documento.setEstado(ProcesoProduccionDocumentoVersion.Estado.RETIRADA);
        return documento;
    }

    private static ProcesoProduccionDocumentoService.DescargaDocumento descarga() {
        byte[] bytes = new byte[]{1, 2, 3};
        return new ProcesoProduccionDocumentoService.DescargaDocumento(
                new ByteArrayResource(bytes), "poe.pdf", "application/pdf",
                (long) bytes.length, "hash");
    }

    private static ProcesoProduccionDocumentoPdfService.DocumentoPdf pdf() {
        byte[] bytes = new byte[]{4, 5, 6};
        return new ProcesoProduccionDocumentoPdfService.DocumentoPdf(
                new ByteArrayResource(bytes), "poe.pdf", (long) bytes.length);
    }
}
