package exotic.app.planta.service.productos.procesos;

import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersionResponse;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionDocumentoVersionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcesoProduccionDocumentoServiceTest {

    private final ProcesoProduccionRepo procesoRepo = mock(ProcesoProduccionRepo.class);
    private final ProcesoProduccionDocumentoVersionRepo documentoRepo =
            mock(ProcesoProduccionDocumentoVersionRepo.class);
    private final ProcesoProduccionDocumentoStorage storage =
            mock(ProcesoProduccionDocumentoStorage.class);

    private ProcesoProduccionDocumentoService service;
    private ProcesoProduccion proceso;

    @BeforeEach
    void setUp() throws Exception {
        service = new ProcesoProduccionDocumentoService(procesoRepo, documentoRepo, storage);
        proceso = new ProcesoProduccion();
        proceso.setProcesoId(15);
        proceso.setNombre("Envasado");

        when(procesoRepo.findByIdForUpdate(15)).thenReturn(Optional.of(proceso));
        when(documentoRepo.findVigenteForUpdate(
                15,
                ProcesoProduccionDocumentoVersion.Estado.VIGENTE
        )).thenReturn(Optional.empty());
        when(documentoRepo.findMaxVersionByProcesoId(15)).thenReturn(0);
        when(storage.store(any(), any(), any()))
                .thenReturn(new ProcesoProduccionDocumentoStorage.StoredFile(
                        "procesos-produccion/15/documentos/documento.pdf"
                ));
        when(documentoRepo.save(any())).thenAnswer(invocation -> {
            ProcesoProduccionDocumentoVersion value = invocation.getArgument(0);
            value.setId(101L);
            return value;
        });
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void creaPrimeraVersionPdfConMotivoPredeterminado() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "archivo",
                "poe-envasado.pdf",
                "application/pdf",
                validPdf()
        );

        ProcesoProduccionDocumentoVersionResponse result = service.crearNuevaVersion(
                15,
                file,
                " ",
                "calidad"
        );

        assertThat(result.version()).isEqualTo(1);
        assertThat(result.estado()).isEqualTo(ProcesoProduccionDocumentoVersion.Estado.VIGENTE);
        assertThat(result.motivoCambio()).isEqualTo("Carga inicial");
        assertThat(result.contentType()).isEqualTo(ProcesoProduccionDocumentoService.PDF_CONTENT_TYPE);
        verify(procesoRepo).findByIdForUpdate(15);
    }

    @Test
    void aceptaDocumentoDocxReal() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "archivo",
                "procedimiento.docx",
                ProcesoProduccionDocumentoService.DOCX_CONTENT_TYPE,
                validDocx()
        );

        ProcesoProduccionDocumentoVersionResponse result = service.crearNuevaVersion(
                15,
                file,
                null,
                "calidad"
        );

        assertThat(result.contentType()).isEqualTo(ProcesoProduccionDocumentoService.DOCX_CONTENT_TYPE);
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(storage).store(org.mockito.ArgumentMatchers.eq(15), contentCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(".docx"));
        assertThat(contentCaptor.getValue()).isNotEmpty();
    }

    @Test
    void aceptaArchivoValidoEnElLimiteExactoDeDosMegabytes() throws Exception {
        byte[] originalPdf = validPdf();
        byte[] exactLimitPdf = Arrays.copyOf(
                originalPdf,
                (int) ProcesoProduccionDocumentoService.MAX_FILE_SIZE_BYTES
        );
        Arrays.fill(exactLimitPdf, originalPdf.length, exactLimitPdf.length, (byte) ' ');
        MockMultipartFile file = new MockMultipartFile(
                "archivo",
                "limite.pdf",
                "application/pdf",
                exactLimitPdf
        );

        ProcesoProduccionDocumentoVersionResponse result = service.crearNuevaVersion(
                15,
                file,
                null,
                "calidad"
        );

        assertThat(result.tamanoBytes()).isEqualTo(ProcesoProduccionDocumentoService.MAX_FILE_SIZE_BYTES);
    }

    @Test
    void rechazaArchivoRenombradoComoPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "archivo",
                "falso.pdf",
                "application/pdf",
                "no-es-un-pdf".getBytes()
        );

        assertThatThrownBy(() -> service.crearNuevaVersion(15, file, null, "calidad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("firma PDF");
        verify(storage, never()).store(any(), any(), any());
    }

    @Test
    void rechazaDocxConMacros() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "archivo",
                "procedimiento.docx",
                ProcesoProduccionDocumentoService.DOCX_CONTENT_TYPE,
                docxWithMacroMarker()
        );

        assertThatThrownBy(() -> service.crearNuevaVersion(15, file, null, "calidad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("macros");
        verify(storage, never()).store(any(), any(), any());
    }

    @Test
    void rechazaArchivosMayoresDeDosMegabytes() throws Exception {
        byte[] tooLarge = new byte[(int) ProcesoProduccionDocumentoService.MAX_FILE_SIZE_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile(
                "archivo",
                "grande.pdf",
                "application/pdf",
                tooLarge
        );

        assertThatThrownBy(() -> service.crearNuevaVersion(15, file, null, "calidad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 MB");
        verify(storage, never()).store(any(), any(), any());
    }

    @Test
    void exigeMotivoAlReemplazarUnaVersion() throws Exception {
        ProcesoProduccionDocumentoVersion vigente = new ProcesoProduccionDocumentoVersion();
        vigente.setId(90L);
        vigente.setProceso(proceso);
        vigente.setVersion(1);
        vigente.setEstado(ProcesoProduccionDocumentoVersion.Estado.VIGENTE);
        when(documentoRepo.findVigenteForUpdate(
                15,
                ProcesoProduccionDocumentoVersion.Estado.VIGENTE
        )).thenReturn(Optional.of(vigente));

        MockMultipartFile file = new MockMultipartFile(
                "archivo",
                "revision.pdf",
                "application/pdf",
                validPdf()
        );

        assertThatThrownBy(() -> service.crearNuevaVersion(15, file, " ", "calidad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
        verify(storage, never()).store(any(), any(), any());
    }

    @Test
    void reemplazoRetiraVigenteYCreaLaSiguienteVersion() throws Exception {
        ProcesoProduccionDocumentoVersion vigente = new ProcesoProduccionDocumentoVersion();
        vigente.setId(90L);
        vigente.setProceso(proceso);
        vigente.setVersion(1);
        vigente.setEstado(ProcesoProduccionDocumentoVersion.Estado.VIGENTE);
        when(documentoRepo.findVigenteForUpdate(
                15,
                ProcesoProduccionDocumentoVersion.Estado.VIGENTE
        )).thenReturn(Optional.of(vigente));
        when(documentoRepo.findMaxVersionByProcesoId(15)).thenReturn(1);
        MockMultipartFile file = new MockMultipartFile(
                "archivo",
                "revision.pdf",
                "application/pdf",
                validPdf()
        );

        ProcesoProduccionDocumentoVersionResponse result = service.crearNuevaVersion(
                15,
                file,
                "Actualización de instrucciones",
                "calidad"
        );

        assertThat(result.version()).isEqualTo(2);
        assertThat(result.motivoCambio()).isEqualTo("Actualización de instrucciones");
        assertThat(vigente.getEstado()).isEqualTo(ProcesoProduccionDocumentoVersion.Estado.RETIRADA);
        assertThat(vigente.getVigenteHasta()).isNotNull();
    }

    @Test
    void programaLimpiezaDelArchivoSiLaTransaccionRevierte() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        MockMultipartFile file = new MockMultipartFile(
                "archivo",
                "poe.pdf",
                "application/pdf",
                validPdf()
        );

        service.crearNuevaVersion(15, file, null, "calidad");
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();

        assertThat(synchronizations).hasSize(1);
        synchronizations.getFirst().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).deleteIfExists("procesos-produccion/15/documentos/documento.pdf");
    }

    private static byte[] validPdf() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        document.add(new Paragraph("Procedimiento operativo estandar"));
        document.close();
        return output.toByteArray();
    }

    private static byte[] validDocx() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Procedimiento operativo estandar");
            document.write(output);
        }
        return output.toByteArray();
    }

    private static byte[] docxWithMacroMarker() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addZipEntry(zip, "[Content_Types].xml", "<Types></Types>".getBytes());
            addZipEntry(zip, "word/document.xml", "<document></document>".getBytes());
            addZipEntry(zip, "word/vbaProject.bin", new byte[]{1, 2, 3});
        }
        return output.toByteArray();
    }

    private static void addZipEntry(ZipOutputStream zip, String name, byte[] content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
}
