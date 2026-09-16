package exotic.app.planta.service.productos.procesos;

import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcesoProduccionDocumentoPdfServiceTest {

    private final ProcesoProduccionDocumentoPdfService service =
            new ProcesoProduccionDocumentoPdfService();

    @Test
    void conservaUnPdfValidoConSuNombreNormalizado() throws Exception {
        byte[] source = pdf("Contenido del POE");

        ProcesoProduccionDocumentoPdfService.DocumentoPdf result = service.renderizar(
                descarga(source, "poe-mezcla.PDF", "application/pdf", sha256(source)));

        assertThat(result.fileName()).isEqualTo("poe-mezcla.pdf");
        assertThat(result.contentLength()).isEqualTo(source.length);
        assertThat(result.resource().getByteArray()).containsExactly(source);
    }

    @Test
    void convierteDocxSinModificarElArchivoFuente() throws Exception {
        byte[] source = docx("Instruccion operativa congelada");
        byte[] original = source.clone();

        ProcesoProduccionDocumentoPdfService.DocumentoPdf result = service.renderizar(
                descarga(source, "poe-envasado.docx",
                        ProcesoProduccionDocumentoPdfService.DOCX_CONTENT_TYPE,
                        sha256(source)));

        assertThat(source).containsExactly(original);
        assertThat(result.fileName()).isEqualTo("poe-envasado.pdf");
        PdfReader reader = new PdfReader(result.resource().getByteArray());
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            assertThat(PdfTextExtractor.getTextFromPage(reader, 1))
                    .contains("Instruccion operativa congelada");
        } finally {
            reader.close();
        }
    }

    @Test
    void rechazaUnArchivoQueNoCoincideConElHashCongelado() throws Exception {
        byte[] source = pdf("Contenido alterado");

        assertThatThrownBy(() -> service.renderizar(
                descarga(source, "poe.pdf", "application/pdf", "0".repeat(64))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHA-256");
    }

    @Test
    void informaElFalloCuandoElDocxCongeladoNoPuedeConvertirse() throws Exception {
        byte[] source = "archivo-docx-corrupto".getBytes();

        assertThatThrownBy(() -> service.renderizar(
                descarga(source, "poe-corrupto.docx",
                        ProcesoProduccionDocumentoPdfService.DOCX_CONTENT_TYPE,
                        sha256(source))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("convertir");
    }

    private static ProcesoProduccionDocumentoService.DescargaDocumento descarga(
            byte[] bytes,
            String fileName,
            String contentType,
            String sha256
    ) {
        return new ProcesoProduccionDocumentoService.DescargaDocumento(
                new ByteArrayResource(bytes), fileName, contentType,
                (long) bytes.length, sha256);
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

    private static byte[] docx(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
        }
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
