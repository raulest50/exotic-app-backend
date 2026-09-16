package exotic.app.planta.service.productos.procesos;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class ProcesoProduccionDocumentoPdfService {

    public static final String PDF_CONTENT_TYPE = "application/pdf";
    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final BaseColor PRIMARY = new BaseColor(38, 74, 97);

    public DocumentoPdf renderizar(
            ProcesoProduccionDocumentoService.DescargaDocumento descarga
    ) {
        byte[] contenido;
        try (InputStream input = descarga.resource().getInputStream()) {
            contenido = input.readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible leer el archivo fuente del POE.", exception);
        }

        validarIntegridad(contenido, descarga.sha256());
        byte[] pdf = convertirARepresentacionPdf(
                contenido,
                descarga.fileName(),
                descarga.contentType());
        return new DocumentoPdf(
                new ByteArrayResource(pdf),
                nombrePdf(descarga.fileName()),
                (long) pdf.length);
    }

    public byte[] convertirARepresentacionPdf(
            byte[] contenido,
            String nombreArchivo,
            String contentType
    ) {
        if (contenido == null || contenido.length == 0) {
            throw new IllegalStateException("El archivo fuente del POE esta vacio.");
        }
        if (PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            validarPdf(contenido);
            return contenido;
        }
        if (DOCX_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            return convertirDocx(contenido, nombreArchivo);
        }
        throw new IllegalStateException("El formato del POE no admite representacion PDF.");
    }

    private void validarIntegridad(byte[] contenido, String sha256Esperado) {
        if (sha256Esperado == null || sha256Esperado.isBlank()) {
            throw new IllegalStateException("El POE no tiene un SHA-256 registrado.");
        }
        try {
            String sha256Real = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(contenido));
            if (!sha256Real.equalsIgnoreCase(sha256Esperado)) {
                throw new IllegalStateException(
                        "El archivo del POE no coincide con el SHA-256 de su version documental.");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible verificar el SHA-256 del POE.", exception);
        }
    }

    private void validarPdf(byte[] contenido) {
        try {
            PdfReader reader = new PdfReader(contenido);
            try {
                if (reader.getNumberOfPages() < 1) {
                    throw new IllegalStateException("El PDF del POE no contiene paginas.");
                }
            } finally {
                reader.close();
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("El archivo del POE no es un PDF valido.", exception);
        }
    }

    private byte[] convertirDocx(byte[] contenido, String nombreArchivo) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 42, 42, 48, 42);
            PdfWriter.getInstance(document, output);
            document.open();
            Paragraph notice = new Paragraph(
                    "Representacion PDF del archivo DOCX: " + nombreArchivo,
                    font(8, Font.ITALIC, BaseColor.DARK_GRAY));
            notice.setSpacingAfter(10);
            document.add(notice);

            try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(contenido))) {
                for (IBodyElement element : word.getBodyElements()) {
                    if (element instanceof XWPFParagraph paragraph) {
                        agregarParrafoDocx(document, paragraph);
                    } else if (element instanceof XWPFTable table) {
                        agregarTablaDocx(document, table);
                    }
                }
            }
            document.close();
            byte[] pdf = output.toByteArray();
            validarPdf(pdf);
            return pdf;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "No fue posible convertir el POE DOCX a una representacion PDF.", exception);
        }
    }

    private void agregarParrafoDocx(Document document, XWPFParagraph source) throws Exception {
        String text = source.getText();
        boolean heading = source.getStyle() != null
                && source.getStyle().toLowerCase(Locale.ROOT).contains("heading");
        if (text != null && !text.isBlank()) {
            Paragraph paragraph = new Paragraph(
                    text,
                    font(heading ? 12 : 9, heading ? Font.BOLD : Font.NORMAL,
                            heading ? PRIMARY : BaseColor.BLACK));
            paragraph.setSpacingAfter(heading ? 6 : 3);
            document.add(paragraph);
        }
        for (XWPFRun run : source.getRuns()) {
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                try {
                    Image image = Image.getInstance(picture.getPictureData().getData());
                    image.scaleToFit(500, 650);
                    image.setAlignment(Element.ALIGN_CENTER);
                    document.add(image);
                } catch (Exception ignored) {
                    document.add(new Paragraph(
                            "[Imagen del DOCX no compatible con la conversion PDF]",
                            font(7, Font.ITALIC, BaseColor.GRAY)));
                }
            }
        }
    }

    private void agregarTablaDocx(Document document, XWPFTable source) throws Exception {
        int columns = source.getRows().stream()
                .mapToInt(row -> row.getTableCells().size())
                .max().orElse(1);
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        for (XWPFTableRow row : source.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                PdfPCell target = new PdfPCell(new Phrase(
                        cell.getText(), font(8, Font.NORMAL, BaseColor.BLACK)));
                target.setPadding(4);
                table.addCell(target);
            }
            for (int missing = row.getTableCells().size(); missing < columns; missing++) {
                table.addCell(new PdfPCell(new Phrase("")));
            }
        }
        table.setSpacingAfter(6);
        document.add(table);
    }

    private String nombrePdf(String nombreArchivo) {
        String nombre = nombreArchivo == null || nombreArchivo.isBlank()
                ? "poe" : nombreArchivo.trim();
        int dot = nombre.lastIndexOf('.');
        String base = dot > 0 ? nombre.substring(0, dot) : nombre;
        return base + ".pdf";
    }

    private Font font(float size, int style, BaseColor color) {
        return new Font(Font.FontFamily.HELVETICA, size, style, color);
    }

    public record DocumentoPdf(
            ByteArrayResource resource,
            String fileName,
            Long contentLength
    ) {
    }
}
