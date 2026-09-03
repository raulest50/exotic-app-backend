package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchRecordPdfAnnexServiceTest {

    @Mock
    private ProcesoProduccionDocumentoService procesoDocumentoService;

    private ObjectMapper objectMapper;
    private BatchRecordPdfAnnexService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new BatchRecordPdfAnnexService(procesoDocumentoService, objectMapper);
    }

    @Test
    void componeOrdenDispensacionYPoeEnUnSoloPdf() throws Exception {
        byte[] poe = pdf("CONTENIDO CONTROLADO DEL POE");
        String hash = sha256(poe);
        ObjectNode root = expediente(hash);
        when(procesoDocumentoService.getDescarga(10, 100L))
                .thenReturn(new ProcesoProduccionDocumentoService.DescargaDocumento(
                        new ByteArrayResource(poe),
                        "POE-mezclado.pdf",
                        "application/pdf",
                        (long) poe.length,
                        hash));

        byte[] result = service.componer(pdf("EXPEDIENTE PRINCIPAL"), root);
        String samplePath = System.getenv("BATCH_RECORD_PDF_SAMPLE_PATH");
        if (samplePath != null && !samplePath.isBlank()) {
            Path target = Path.of(samplePath);
            Files.createDirectories(target.getParent());
            Files.write(target, result);
        }

        PdfReader reader = new PdfReader(result);
        try {
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(PdfTextExtractor.getTextFromPage(reader, page));
            }
            assertTrue(reader.getNumberOfPages() >= 6);
            assertTrue(text.toString().contains("EXPEDIENTE PRINCIPAL"));
            assertTrue(text.toString().contains("ORDEN DE PRODUCCIÓN"));
            assertTrue(text.toString().contains("DISPENSACIÓN DE MATERIALES"));
            assertTrue(text.toString().contains("CONTENIDO CONTROLADO DEL POE"));
            assertNotNull(reader.getCatalog()
                    .getAsDict(PdfName.NAMES)
                    .getAsDict(PdfName.EMBEDDEDFILES));
        } finally {
            reader.close();
        }
        verify(procesoDocumentoService).getDescarga(10, 100L);
    }

    @Test
    void rechazaUnPoeQueNoCoincideConElHashCongelado() throws Exception {
        byte[] poe = pdf("POE ALTERADO");
        ObjectNode root = expediente("0".repeat(64));
        when(procesoDocumentoService.getDescarga(10, 100L))
                .thenReturn(new ProcesoProduccionDocumentoService.DescargaDocumento(
                        new ByteArrayResource(poe),
                        "POE-mezclado.pdf",
                        "application/pdf",
                        (long) poe.length,
                        sha256(poe)));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.componer(pdf("EXPEDIENTE PRINCIPAL"), root));

        assertNotNull(exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("hash congelado"));
    }

    @Test
    void conviertePoeDocxYConservaElArchivoFuenteEmbebido() throws Exception {
        byte[] poe = docx("INSTRUCCION CONTROLADA DESDE DOCX");
        String hash = sha256(poe);
        ObjectNode root = expediente(hash);
        ((ObjectNode) root.path("etapas").path(0).path("poe"))
                .put("nombreArchivo", "POE-mezclado.docx");
        when(procesoDocumentoService.getDescarga(10, 100L))
                .thenReturn(new ProcesoProduccionDocumentoService.DescargaDocumento(
                        new ByteArrayResource(poe),
                        "POE-mezclado.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        (long) poe.length,
                        hash));

        byte[] result = service.componer(pdf("EXPEDIENTE PRINCIPAL"), root);

        PdfReader reader = new PdfReader(result);
        try {
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(PdfTextExtractor.getTextFromPage(reader, page));
            }
            assertTrue(text.toString().contains("INSTRUCCION CONTROLADA DESDE DOCX"));
            assertNotNull(reader.getCatalog()
                    .getAsDict(PdfName.NAMES)
                    .getAsDict(PdfName.EMBEDDEDFILES));
        } finally {
            reader.close();
        }
    }

    private ObjectNode expediente(String poeHash) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("codigo", "BR-OP-77");
        root.put("estado", "PENDIENTE_REVISION");
        root.put("esquemaVersion", "batch-record-v4");

        ObjectNode orden = root.putObject("orden");
        orden.put("tipo", "ORDEN_PRODUCCION");
        orden.put("id", 77);
        orden.put("estado", 3);
        orden.put("fechaCreacion", "2026-09-01T08:00:00");
        orden.put("observaciones", "Orden documental de prueba");

        ObjectNode producto = root.putObject("producto");
        producto.put("id", "PT-001");
        producto.put("nombre", "Producto de prueba");
        producto.put("unidad", "KG");
        root.putObject("lote").put("numero", "LOT-001");
        ObjectNode cantidades = root.putObject("cantidades");
        cantidades.put("planificada", 20);
        cantidades.put("unidad", "KG");
        root.put("requerimientosMaterialesJson", """
                [{"productoId":"MP-1","productoNombre":"Materia prima",\
                "tipoProducto":"material","cantidad":10,"unidadMedida":"KG",\
                "consumoDirecto":false}]
                """);

        ArrayNode etapas = root.putArray("etapas");
        ObjectNode etapa = etapas.addObject();
        etapa.put("secuencia", 1);
        etapa.put("nombre", "Mezclado");
        etapa.put("areaNombre", "Producción");
        etapa.put("estado", "COMPLETADA");
        ObjectNode poe = etapa.putObject("poe");
        poe.put("procesoProduccionId", 10);
        poe.put("procesoProduccionNombre", "Mezclado");
        poe.put("documentoVersionId", 100);
        poe.put("version", 4);
        poe.put("nombreArchivo", "POE-mezclado.pdf");
        poe.put("sha256", poeHash);

        ArrayNode dispensaciones = root.putArray("dispensaciones");
        ObjectNode dispensacion = dispensaciones.addObject();
        dispensacion.put("transaccionId", 901);
        dispensacion.put("tipo", "OD");
        dispensacion.put("fechaTransaccion", "2026-09-01T09:00:00");
        dispensacion.put("observaciones", "Dispensación verificada");
        ObjectNode realizador = dispensacion.putArray("usuariosRealizadores").addObject();
        realizador.put("nombre", "Operario de prueba");
        realizador.put("username", "operario");
        ObjectNode movimiento = dispensacion.putArray("movimientos").addObject();
        movimiento.put("productoId", "MP-1");
        movimiento.put("productoNombre", "Materia prima");
        movimiento.put("loteOrigen", "MP-LOT-9");
        movimiento.put("cantidad", 10);
        movimiento.put("unidad", "KG");
        movimiento.put("areaOperativa", "Producción");
        return root;
    }

    private byte[] pdf(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        document.add(new Paragraph(text));
        document.close();
        return output.toByteArray();
    }

    private byte[] docx(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
        }
        return output.toByteArray();
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
