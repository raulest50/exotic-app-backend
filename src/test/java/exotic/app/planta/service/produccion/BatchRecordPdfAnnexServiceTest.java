package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PRStream;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfNameTree;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordFirmaRepo;
import exotic.app.planta.repo.usuarios.FirmaVisualUsuarioVersionRepo;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoPdfService;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoService;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoService.ConsultaDocumento;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoService.DescargaDocumento;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoStorage.ArchivoNoDisponibleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BatchRecordPdfAnnexServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProcesoProduccionDocumentoService documentos = mock(ProcesoProduccionDocumentoService.class);
    private final BatchRecordPdfAnnexService service = new BatchRecordPdfAnnexService(
            documentos, new ProcesoProduccionDocumentoPdfService(), mapper);

    @Test
    void conservaAnexoValidoYArchivoFuenteAunqueOtroNoEsteDisponible() throws Exception {
        byte[] source = pdf("POE ORIGINAL DISPONIBLE");
        ObjectNode root = root();
        poe(root, 501, sha(source));
        poe(root, 502, "hash-del-ausente");
        poe(root, 501, sha(source)); // La misma versión solo se incorpora una vez.
        when(documentos.consultarParaAnexo(12, 501L)).thenReturn(disponible(source, "application/pdf"));
        when(documentos.consultarParaAnexo(12, 502L))
                .thenReturn(new ConsultaDocumento(null, new ArchivoNoDisponibleException()));
        String before = root.toString();

        PdfReader reader = new PdfReader(generar(root));
        try {
            String text = texto(reader);
            assertThat(text).contains("CONTENIDO EXPEDIENTE", "POE ORIGINAL DISPONIBLE",
                    "ANEXO - POE NO INCORPORADO", "NO INCORPORADO:", "502");
            assertThat(text.indexOf("POE ORIGINAL DISPONIBLE"))
                    .isLessThan(text.indexOf("ANEXO - POE NO INCORPORADO"));
            assertAdvertenciasEnTodasLasPaginas(reader);
            assertThat(reader.getInfo().get("Title")).contains("anexos incompletos");
            Map<String, PdfObject> attachments = adjuntos(reader);
            assertThat(attachments).hasSize(1);
            PdfDictionary file = (PdfDictionary) PdfReader.getPdfObject(attachments.values().iterator().next());
            PRStream stream = (PRStream) PdfReader.getPdfObject(file.getAsDict(PdfName.EF).get(PdfName.F));
            assertThat(PdfReader.getStreamBytes(stream)).containsExactly(source);
        } finally {
            reader.close();
        }
        assertThat(root.toString()).isEqualTo(before);
        verify(documentos, times(1)).consultarParaAnexo(12, 501L);
    }

    @Test
    void generaPortadaYExpedienteCuandoTodosLosPoeFaltan() throws Exception {
        ObjectNode root = root();
        poe(root, 501, "hash-1");
        poe(root, 502, "hash-2");
        when(documentos.consultarParaAnexo(12, 501L))
                .thenReturn(new ConsultaDocumento(null, new ArchivoNoDisponibleException()));
        when(documentos.consultarParaAnexo(12, 502L))
                .thenReturn(new ConsultaDocumento(null, new NoSuchElementException("No existe la versión documental.")));
        BatchRecordPdfAnnexService.AnexosPreparados anexos = service.preparar(root);
        BatchRecordMainPdfRenderer renderer = new BatchRecordMainPdfRenderer(
                mock(BatchRecordFirmaRepo.class), mock(FirmaVisualUsuarioVersionRepo.class), mapper);
        byte[] logo = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        byte[] main = renderer.render(root, new BatchRecordMainPdfRenderer.RenderContext(
                "VISTA ACTUAL NO CONTROLADA", null, true, null, BatchRecordService.PLANTILLA_PDF_VERSION,
                new BatchRecordMainPdfRenderer.LogoDocumental(logo, 1, "logo"), anexos.numeroIncidencias()));

        PdfReader reader = new PdfReader(service.componer(main, root, anexos));
        try {
            assertThat(PdfTextExtractor.getTextFromPage(reader, 1))
                    .contains(BatchRecordPdfAnnexService.AVISO_INCOMPLETO, "2 POE no incorporado(s)", "BR-OP-44");
            assertThat(texto(reader)).contains("ANEXO - ORDEN DE PRODUCCIÓN", "Documento de consulta",
                    "No existe la versión documental.", "no modifica la revisión");
            assertAdvertenciasEnTodasLasPaginas(reader);
            assertThat(adjuntos(reader)).isEmpty();
        } finally {
            reader.close();
        }
    }

    enum Fallo { VACIO, ILEGIBLE, HASH_INCORRECTO, SIN_HASH, FORMATO, PDF_CORRUPTO, DOCX_CORRUPTO }

    @ParameterizedTest
    @EnumSource(Fallo.class)
    void omiteSoloDocumentosQueNoPuedenValidarse(Fallo fallo) throws Exception {
        byte[] source = switch (fallo) {
            case VACIO -> new byte[0];
            case PDF_CORRUPTO, DOCX_CORRUPTO -> "no es un documento".getBytes(StandardCharsets.UTF_8);
            default -> pdf("CONTENIDO NO DEBE INCORPORARSE");
        };
        String contentType = switch (fallo) {
            case DOCX_CORRUPTO -> ProcesoProduccionDocumentoPdfService.DOCX_CONTENT_TYPE;
            case FORMATO -> "application/octet-stream";
            default -> "application/pdf";
        };
        String hash = fallo == Fallo.SIN_HASH ? null : sha(source);
        ObjectNode root = root();
        poe(root, 501, fallo == Fallo.HASH_INCORRECTO ? "0".repeat(64) : hash);
        Resource resource = fallo == Fallo.ILEGIBLE ? new AbstractResource() {
            @Override public String getDescription() { return "recurso de prueba"; }
            @Override public InputStream getInputStream() throws IOException {
                throw new IOException("/ruta/interna/que/no/debe/exponerse");
            }
        } : new ByteArrayResource(source);
        when(documentos.consultarParaAnexo(12, 501L)).thenReturn(new ConsultaDocumento(
                new DescargaDocumento(resource, "poe", contentType, (long) source.length, hash), null));

        PdfReader reader = new PdfReader(generar(root));
        try {
            assertThat(texto(reader)).contains("CONTENIDO EXPEDIENTE", "ANEXO - POE NO INCORPORADO")
                    .doesNotContain("CONTENIDO NO DEBE INCORPORARSE", "/ruta/interna");
            assertThat(adjuntos(reader)).isEmpty();
            assertAdvertenciasEnTodasLasPaginas(reader);
        } finally {
            reader.close();
        }
    }

    @Test
    void distingueReferenciaInvalidaDeUnaEtapaSinPoe() throws Exception {
        ObjectNode root = root();
        root.withArray("etapas").addObject().put("nombre", "Sin POE");
        root.withArray("etapas").addObject().putNull("poe");
        assertThat(service.preparar(root).numeroIncidencias()).isZero();
        PdfReader reader = new PdfReader(generar(root));
        try {
            assertThat(texto(reader)).contains("El expediente no registra versiones de POE asociadas.")
                    .doesNotContain(BatchRecordPdfAnnexService.AVISO_INCOMPLETO);
        } finally {
            reader.close();
        }
        root.withArray("etapas").addObject().put("nombre", "Referencia rota")
                .putObject("poe").put("documentoVersionId", 501);
        assertThat(service.preparar(root).numeroIncidencias()).isEqualTo(1);
        verifyNoInteractions(documentos);
    }

    @Test
    void recuperaGeneracionCompletaAlVolverAEstarDisponibleElArchivo() throws Exception {
        byte[] source = pdf("POE RESTAURADO");
        ObjectNode root = root();
        poe(root, 501, null); // Compatibilidad histórica: hash de la versión documental exacta.
        when(documentos.consultarParaAnexo(12, 501L))
                .thenReturn(new ConsultaDocumento(null, new ArchivoNoDisponibleException()))
                .thenReturn(disponible(source, "application/pdf"));
        assertThat(service.preparar(root).numeroIncidencias()).isEqualTo(1);

        PdfReader reader = new PdfReader(generar(root));
        try {
            assertThat(texto(reader)).contains("POE RESTAURADO")
                    .doesNotContain(BatchRecordPdfAnnexService.AVISO_INCOMPLETO, "NO INCORPORADO");
            assertThat(reader.getInfo().get("Title")).isEqualTo("Expediente completo BR-OP-44");
            assertThat(adjuntos(reader)).hasSize(1);
        } finally {
            reader.close();
        }
    }

    @Test
    void noOcultaErroresDeBaseDeDatosNiDeProgramacion() throws Exception {
        byte[] source = pdf("POE");
        ObjectNode root = root();
        poe(root, 501, sha(source));
        DataAccessResourceFailureException databaseFailure = new DataAccessResourceFailureException("BD no disponible");
        when(documentos.consultarParaAnexo(12, 501L)).thenThrow(databaseFailure);
        assertThatThrownBy(() -> service.preparar(root)).isSameAs(databaseFailure);

        doReturn(disponible(source, "application/pdf")).when(documentos).consultarParaAnexo(12, 501L);
        ProcesoProduccionDocumentoPdfService converter = mock(ProcesoProduccionDocumentoPdfService.class);
        NullPointerException bug = new NullPointerException("fallo inesperado");
        when(converter.convertirARepresentacionPdf(any(), any(), any())).thenThrow(bug);
        BatchRecordPdfAnnexService withBrokenConverter = new BatchRecordPdfAnnexService(documentos, converter, mapper);
        assertThatThrownBy(() -> withBrokenConverter.preparar(root)).isSameAs(bug);
    }

    private ObjectNode root() {
        ObjectNode root = mapper.createObjectNode().put("codigo", "BR-OP-44");
        root.putObject("orden").put("tipo", "ORDEN_PRODUCCION").put("id", 44);
        root.putArray("etapas");
        return root;
    }

    private void poe(ObjectNode root, long id, String hash) {
        root.withArray("etapas").addObject().putObject("poe")
                .put("procesoProduccionId", 12).put("documentoVersionId", id).put("version", 1)
                .put("procesoProduccionNombre", "Mezcla").put("nombreArchivo", "poe.pdf").put("sha256", hash);
    }

    private ConsultaDocumento disponible(byte[] source, String contentType) throws Exception {
        return new ConsultaDocumento(new DescargaDocumento(new ByteArrayResource(source), "poe.pdf",
                contentType, (long) source.length, sha(source)), null);
    }

    private byte[] generar(ObjectNode root) throws Exception {
        return service.componer(pdf("CONTENIDO EXPEDIENTE"), root, service.preparar(root));
    }

    private static byte[] pdf(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        document.add(new Paragraph(text));
        document.close();
        return output.toByteArray();
    }

    private static String sha(byte[] source) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
    }

    private static String texto(PdfReader reader) throws Exception {
        StringBuilder result = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            result.append(PdfTextExtractor.getTextFromPage(reader, page)).append('\n');
        }
        return result.toString();
    }

    private static void assertAdvertenciasEnTodasLasPaginas(PdfReader reader) throws Exception {
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            assertThat(PdfTextExtractor.getTextFromPage(reader, page))
                    .as("advertencia y paginación en página %s", page)
                    .contains(BatchRecordPdfAnnexService.AVISO_INCOMPLETO, "página " + page + " de ");
        }
    }

    private static Map<String, PdfObject> adjuntos(PdfReader reader) {
        PdfDictionary names = reader.getCatalog().getAsDict(PdfName.NAMES);
        if (names == null || names.getAsDict(PdfName.EMBEDDEDFILES) == null) return Map.of();
        return PdfNameTree.readTree(names.getAsDict(PdfName.EMBEDDEDFILES));
    }
}
