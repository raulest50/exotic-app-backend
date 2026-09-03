package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfFileSpecification;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compone el expediente PDF con sus documentos relacionados. Los datos de la
 * OP y las dispensaciones se leen del contenido canónico de la revisión; los
 * POE se recuperan por la versión documental exacta y se verifican por hash.
 */
@Service
@RequiredArgsConstructor
public class BatchRecordPdfAnnexService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final BaseColor PRIMARY = new BaseColor(38, 74, 97);
    private static final BaseColor LIGHT = new BaseColor(235, 241, 245);

    private final ProcesoProduccionDocumentoService procesoDocumentoService;
    private final ObjectMapper objectMapper;

    public byte[] componer(byte[] expedientePrincipal, JsonNode root) {
        try {
            List<JsonNode> dispensaciones = dispensaciones(root);
            List<PoeCargado> poes = cargarPoes(root.path("etapas"));
            List<byte[]> partes = new ArrayList<>();
            partes.add(expedientePrincipal);
            partes.add(crearIndice(root, dispensaciones, poes));
            partes.add(crearOrden(root));
            for (JsonNode dispensacion : dispensaciones) {
                partes.add(crearDispensacion(root, dispensacion));
            }
            for (PoeCargado poe : poes) {
                partes.add(crearPortadaPoe(poe));
                partes.add(PDF_CONTENT_TYPE.equalsIgnoreCase(poe.contentType())
                        ? poe.contenido()
                        : convertirDocx(poe));
            }

            byte[] combinado = combinar(partes);
            return finalizar(combinado, root, poes);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "No fue posible componer los documentos anexos del expediente.", exception);
        }
    }

    private List<JsonNode> dispensaciones(JsonNode root) {
        JsonNode documentadas = root.path("dispensaciones");
        if (documentadas.isArray() && !documentadas.isEmpty()) {
            List<JsonNode> result = new ArrayList<>();
            documentadas.forEach(result::add);
            return result;
        }

        JsonNode consumos = root.path("consumos");
        if (!consumos.isArray() || consumos.isEmpty()) {
            return List.of();
        }
        ObjectNode legado = objectMapper.createObjectNode();
        legado.put("transaccionId", "No conservado en esta revisión histórica");
        legado.put("tipo", "REGISTRO_HISTORICO_DE_CONSUMOS");
        legado.set("movimientos", consumos.deepCopy());
        return List.of(legado);
    }

    private List<PoeCargado> cargarPoes(JsonNode etapas) throws Exception {
        if (!etapas.isArray()) return List.of();

        Map<Long, PoeReferencia> referencias = new LinkedHashMap<>();
        for (JsonNode etapa : etapas) {
            JsonNode poe = etapa.path("poe");
            if (!poe.isObject()
                    || !poe.path("documentoVersionId").canConvertToLong()
                    || !poe.path("procesoProduccionId").canConvertToInt()) {
                continue;
            }
            long documentoVersionId = poe.path("documentoVersionId").longValue();
            referencias.putIfAbsent(documentoVersionId, new PoeReferencia(
                    poe.path("procesoProduccionId").intValue(),
                    documentoVersionId,
                    poe.path("version").asInt(),
                    texto(poe.path("procesoProduccionNombre"), "Proceso de producción"),
                    texto(poe.path("nombreArchivo"), "poe-" + documentoVersionId),
                    texto(poe.path("sha256"), null)));
        }

        List<PoeCargado> result = new ArrayList<>();
        for (PoeReferencia referencia : referencias.values()) {
            ProcesoProduccionDocumentoService.DescargaDocumento descarga =
                    procesoDocumentoService.getDescarga(
                            referencia.procesoId(), referencia.documentoVersionId());
            byte[] contenido;
            try (InputStream input = descarga.resource().getInputStream()) {
                contenido = input.readAllBytes();
            }
            String hashEsperado = referencia.sha256() == null
                    ? descarga.sha256() : referencia.sha256();
            String hashReal = sha256(contenido);
            if (hashEsperado == null || !hashReal.equalsIgnoreCase(hashEsperado)) {
                throw new IllegalStateException(
                        "El POE " + referencia.documentoVersionId()
                                + " no coincide con el hash congelado en el expediente.");
            }
            String contentType = descarga.contentType();
            if (!PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)
                    && !DOCX_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
                throw new IllegalStateException(
                        "El formato del POE " + referencia.documentoVersionId()
                                + " no puede incorporarse al expediente PDF.");
            }
            result.add(new PoeCargado(
                    referencia,
                    contenido,
                    contentType,
                    descarga.fileName(),
                    hashReal));
        }
        return result;
    }

    private byte[] crearIndice(
            JsonNode root,
            List<JsonNode> dispensaciones,
            List<PoeCargado> poes
    ) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 48, 42);
        PdfWriter.getInstance(document, output);
        document.open();
        titulo(document, "ÍNDICE DE DOCUMENTOS ANEXOS");
        document.add(new Paragraph(
                "Expediente: " + texto(root.path("codigo"), ""),
                font(10, Font.BOLD, PRIMARY)));
        document.add(new Paragraph(
                "Este índice forma parte del PDF único del expediente.",
                font(9, Font.NORMAL, BaseColor.DARK_GRAY)));
        document.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(new float[]{0.6f, 1.5f, 2.1f, 3.8f});
        table.setWidthPercentage(100);
        header(table, "N.º");
        header(table, "Tipo");
        header(table, "Identificador");
        header(table, "Descripción");
        int numero = 1;
        row(table, Integer.toString(numero++), "Orden",
                texto(root.path("orden").path("id"), ""),
                descripcionOrden(root.path("orden")));
        for (JsonNode dispensacion : dispensaciones) {
            row(table, Integer.toString(numero++), "Dispensación",
                    texto(dispensacion.path("transaccionId"), "Sin ID"),
                    texto(dispensacion.path("tipo"), "Dispensación de materiales"));
        }
        for (PoeCargado poe : poes) {
            row(table, Integer.toString(numero++), "POE",
                    "Documento " + poe.referencia().documentoVersionId(),
                    poe.referencia().procesoNombre() + " - versión "
                            + poe.referencia().version());
        }
        document.add(table);
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph(
                poes.isEmpty()
                        ? "El expediente no registra versiones de POE asociadas."
                        : "Los POE se anexan con la versión y el hash registrados en la revisión. "
                        + "Los archivos fuente también quedan embebidos dentro del PDF.",
                font(8, Font.ITALIC, BaseColor.DARK_GRAY)));
        document.close();
        return output.toByteArray();
    }

    private byte[] crearOrden(JsonNode root) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 32, 32, 42, 36);
        PdfWriter.getInstance(document, output);
        document.open();
        JsonNode orden = root.path("orden");
        titulo(document, "ANEXO - " + descripcionOrden(orden).toUpperCase(Locale.ROOT));

        PdfPTable summary = new PdfPTable(4);
        summary.setWidthPercentage(100);
        pair(summary, "Orden", texto(orden.path("id"), ""));
        pair(summary, "Estado", texto(orden.path("estado"), texto(root.path("estado"), "")));
        pair(summary, "Producto", texto(root.path("producto").path("nombre"), ""));
        pair(summary, "Código producto", texto(root.path("producto").path("id"), ""));
        pair(summary, "Lote", texto(root.path("lote").path("numero"), ""));
        pair(summary, "Cantidad planificada",
                texto(root.path("cantidades").path("planificada"), "") + " "
                        + texto(root.path("cantidades").path("unidad"), ""));
        pair(summary, "Fecha de creación", texto(orden.path("fechaCreacion"), ""));
        pair(summary, "Fecha de lanzamiento", texto(orden.path("fechaLanzamiento"), ""));
        pair(summary, "Inicio real", texto(orden.path("fechaInicio"), ""));
        pair(summary, "Final real", texto(orden.path("fechaFinal"), ""));
        pair(summary, "Pedido comercial", texto(orden.path("pedidoComercial"), ""));
        pair(summary, "Área operativa", texto(orden.path("areaOperativa"), ""));
        pair(summary, "Departamento", texto(orden.path("departamentoOperativo"), ""));
        pair(summary, "Responsable", textoPersona(orden.path("responsable")));
        document.add(summary);

        seccion(document, "Materiales planificados");
        JsonNode requisitos = jsonArray(root.path("requerimientosMaterialesJson"));
        if (!requisitos.isArray() || requisitos.isEmpty()) {
            document.add(sinRegistros());
        } else {
            PdfPTable table = new PdfPTable(new float[]{1.2f, 2.8f, 1.2f, 1.1f, 1.1f, 1.1f});
            table.setWidthPercentage(100);
            for (String value : List.of("Código", "Material", "Tipo", "Cantidad", "Unidad", "Modalidad")) {
                header(table, value);
            }
            for (JsonNode item : requisitos) {
                row(table,
                        texto(item.path("productoId"), ""),
                        texto(item.path("productoNombre"), ""),
                        texto(item.path("tipoProducto"), ""),
                        texto(item.path("cantidad"), ""),
                        texto(item.path("unidadMedida"), ""),
                        item.path("consumoDirecto").asBoolean(false)
                                ? "Consumo directo" : "Dispensación");
            }
            document.add(table);
        }

        seccion(document, "Etapas planificadas");
        JsonNode etapas = root.path("etapas");
        if (!etapas.isArray() || etapas.isEmpty()) {
            document.add(sinRegistros());
        } else {
            PdfPTable table = new PdfPTable(new float[]{0.6f, 2.2f, 2.2f, 1.2f, 2.5f});
            table.setWidthPercentage(100);
            for (String value : List.of("Sec.", "Etapa", "Área", "Estado", "POE")) {
                header(table, value);
            }
            for (JsonNode etapa : etapas) {
                JsonNode poe = etapa.path("poe");
                row(table,
                        texto(etapa.path("secuencia"), ""),
                        texto(etapa.path("nombre"), ""),
                        texto(etapa.path("areaNombre"), ""),
                        texto(etapa.path("estado"), ""),
                        poe.isObject()
                                ? texto(poe.path("nombreArchivo"), "POE") + " v"
                                + texto(poe.path("version"), "")
                                : "No asociado");
            }
            document.add(table);
        }

        seccion(document, "Observaciones de la orden");
        document.add(new Paragraph(
                texto(orden.path("observaciones"), "Sin observaciones."),
                font(8, Font.NORMAL, BaseColor.BLACK)));
        document.close();
        return output.toByteArray();
    }

    private byte[] crearDispensacion(JsonNode root, JsonNode dispensacion) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 32, 32, 42, 36);
        PdfWriter.getInstance(document, output);
        document.open();
        titulo(document, "ANEXO - DISPENSACIÓN DE MATERIALES");

        PdfPTable summary = new PdfPTable(2);
        summary.setWidthPercentage(100);
        keyValue(summary, "Transacción", texto(dispensacion.path("transaccionId"), ""));
        keyValue(summary, "Tipo", texto(dispensacion.path("tipo"), ""));
        keyValue(summary, "Fecha", texto(dispensacion.path("fechaTransaccion"), ""));
        keyValue(summary, "Orden", texto(root.path("orden").path("id"), ""));
        keyValue(summary, "Producto fabricado", texto(root.path("producto").path("nombre"), ""));
        keyValue(summary, "Lote de fabricación", texto(root.path("lote").path("numero"), ""));
        keyValue(summary, "Realizada por", textoPersonas(dispensacion.path("usuariosRealizadores")));
        keyValue(summary, "Aprobada por", textoPersona(dispensacion.path("usuarioAprobador")));
        document.add(summary);

        seccion(document, "Materiales dispensados");
        JsonNode movimientos = dispensacion.path("movimientos");
        if (!movimientos.isArray() || movimientos.isEmpty()) {
            document.add(sinRegistros());
        } else {
            PdfPTable table = new PdfPTable(new float[]{1.1f, 2.6f, 1.5f, 1.1f, 0.9f, 1.5f});
            table.setWidthPercentage(100);
            for (String value : List.of("Código", "Material", "Lote", "Cantidad", "Unidad", "Área")) {
                header(table, value);
            }
            for (JsonNode item : movimientos) {
                row(table,
                        texto(item.path("productoId"), ""),
                        texto(item.path("productoNombre"), ""),
                        texto(item.path("loteOrigen"), ""),
                        cantidadPositiva(item.path("cantidad")),
                        texto(item.path("unidad"), texto(item.path("unidadMedida"), "")),
                        texto(item.path("areaOperativa"), ""));
            }
            document.add(table);
        }

        seccion(document, "Observaciones");
        document.add(new Paragraph(
                texto(dispensacion.path("observaciones"), "Sin observaciones."),
                font(8, Font.NORMAL, BaseColor.BLACK)));
        document.close();
        return output.toByteArray();
    }

    private byte[] crearPortadaPoe(PoeCargado poe) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 42, 42, 64, 48);
        PdfWriter.getInstance(document, output);
        document.open();
        titulo(document, "ANEXO - PROCEDIMIENTO OPERATIVO ESTÁNDAR (POE)");
        document.add(Chunk.NEWLINE);
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        keyValue(table, "Proceso", poe.referencia().procesoNombre());
        keyValue(table, "ID del proceso", Integer.toString(poe.referencia().procesoId()));
        keyValue(table, "Versión documental", Integer.toString(poe.referencia().version()));
        keyValue(table, "ID de documento", Long.toString(poe.referencia().documentoVersionId()));
        keyValue(table, "Archivo fuente", poe.fileName());
        keyValue(table, "Formato", poe.contentType());
        keyValue(table, "SHA-256", poe.sha256());
        document.add(table);
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph(
                PDF_CONTENT_TYPE.equalsIgnoreCase(poe.contentType())
                        ? "Las páginas originales del POE se incorporan inmediatamente después de esta portada."
                        : "El contenido del DOCX se convierte a una representación PDF legible. "
                        + "El archivo fuente exacto también queda embebido en el expediente.",
                font(9, Font.NORMAL, BaseColor.DARK_GRAY)));
        document.close();
        return output.toByteArray();
    }

    private byte[] convertirDocx(PoeCargado poe) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 42, 42, 48, 42);
        PdfWriter.getInstance(document, output);
        document.open();
        Paragraph notice = new Paragraph(
                "Representación PDF del archivo DOCX: " + poe.fileName(),
                font(8, Font.ITALIC, BaseColor.DARK_GRAY));
        notice.setSpacingAfter(10);
        document.add(notice);

        try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(poe.contenido()))) {
            for (IBodyElement element : word.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    agregarParrafoDocx(document, paragraph);
                } else if (element instanceof XWPFTable table) {
                    agregarTablaDocx(document, table);
                }
            }
        }
        document.close();
        return output.toByteArray();
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
                            "[Imagen del DOCX no compatible con la conversión PDF]",
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

    private byte[] combinar(List<byte[]> partes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfCopy copy = new PdfCopy(document, output);
        document.open();
        for (byte[] parte : partes) {
            PdfReader reader = new PdfReader(parte);
            try {
                for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                    copy.addPage(copy.getImportedPage(reader, page));
                }
                copy.freeReader(reader);
            } finally {
                reader.close();
            }
        }
        document.close();
        return output.toByteArray();
    }

    private byte[] finalizar(byte[] combinado, JsonNode root, List<PoeCargado> poes)
            throws Exception {
        PdfReader reader = new PdfReader(combinado);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PdfStamper stamper = new PdfStamper(reader, output);
        try {
            int totalPages = reader.getNumberOfPages();
            for (int page = 1; page <= totalPages; page++) {
                Rectangle size = reader.getPageSizeWithRotation(page);
                Phrase footer = new Phrase(
                        texto(root.path("codigo"), "Expediente") + " - página "
                                + page + " de " + totalPages,
                        font(7, Font.NORMAL, BaseColor.GRAY));
                ColumnText.showTextAligned(
                        stamper.getOverContent(page), Element.ALIGN_RIGHT, footer,
                        size.getRight() - 18, size.getBottom() + 10, 0);
            }

            for (PoeCargado poe : poes) {
                String attachmentName = nombreAdjunto(poe);
                PdfFileSpecification specification = PdfFileSpecification.fileEmbedded(
                        stamper.getWriter(), null, attachmentName, poe.contenido());
                stamper.addFileAttachment(
                        "POE " + poe.referencia().procesoNombre() + " versión "
                                + poe.referencia().version(),
                        specification);
            }
            Map<String, String> info = reader.getInfo();
            info.put("Title", "Expediente completo " + texto(root.path("codigo"), ""));
            info.put("Subject", "Batch Record con orden, dispensaciones y POE anexos");
            stamper.setMoreInfo(info);
        } finally {
            stamper.close();
            reader.close();
        }
        return output.toByteArray();
    }

    private JsonNode jsonArray(JsonNode value) throws Exception {
        if (value.isArray()) return value;
        if (value.isTextual() && !value.asText().isBlank()) {
            JsonNode parsed = objectMapper.readTree(value.asText());
            if (parsed != null && parsed.isArray()) return parsed;
        }
        return objectMapper.createArrayNode();
    }

    private String descripcionOrden(JsonNode orden) {
        return "ORDEN_PRODUCCION".equals(texto(orden.path("tipo"), ""))
                ? "Orden de producción" : "Orden de fabricación";
    }

    private String textoPersonas(JsonNode personas) {
        if (!personas.isArray() || personas.isEmpty()) return "No registra";
        List<String> values = new ArrayList<>();
        personas.forEach(persona -> values.add(textoPersona(persona)));
        return String.join(", ", values);
    }

    private String textoPersona(JsonNode persona) {
        if (!persona.isObject() || persona.isEmpty()) return "No registra";
        String nombre = texto(persona.path("nombre"), texto(persona.path("username"), ""));
        String username = texto(persona.path("username"), "");
        return username.isBlank() || nombre.equals(username)
                ? nombre : nombre + " (" + username + ")";
    }

    private String cantidadPositiva(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return "";
        if (!value.isNumber()) return value.asText("");
        return value.decimalValue().abs().stripTrailingZeros().toPlainString();
    }

    private String nombreAdjunto(PoeCargado poe) {
        String source = poe.fileName() == null || poe.fileName().isBlank()
                ? poe.referencia().fileName() : poe.fileName();
        source = source.replace('\\', '-').replace('/', '-').replaceAll("[\\p{Cntrl}]", "_");
        return "POE-" + poe.referencia().procesoId() + "-v"
                + poe.referencia().version() + "-" + source;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }

    private void titulo(Document document, String value) throws Exception {
        Paragraph title = new Paragraph(value, font(16, Font.BOLD, PRIMARY));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(14);
        document.add(title);
    }

    private void seccion(Document document, String value) throws Exception {
        Paragraph title = new Paragraph(value, font(11, Font.BOLD, PRIMARY));
        title.setSpacingBefore(10);
        title.setSpacingAfter(4);
        document.add(title);
    }

    private Paragraph sinRegistros() {
        return new Paragraph("No registra.", font(8, Font.ITALIC, BaseColor.GRAY));
    }

    private void keyValue(PdfPTable table, String label, String value) {
        PdfPCell key = new PdfPCell(new Phrase(label, font(8, Font.BOLD, PRIMARY)));
        key.setBackgroundColor(LIGHT);
        key.setPadding(5);
        PdfPCell content = new PdfPCell(new Phrase(value == null ? "" : value,
                font(8, Font.NORMAL, BaseColor.BLACK)));
        content.setPadding(5);
        table.addCell(key);
        table.addCell(content);
    }

    private void pair(PdfPTable table, String label, String value) {
        keyValue(table, label, value);
    }

    private void header(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font(8, Font.BOLD, BaseColor.WHITE)));
        cell.setBackgroundColor(PRIMARY);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void row(PdfPTable table, String... values) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(
                    value == null ? "" : value,
                    font(8, Font.NORMAL, BaseColor.BLACK)));
            cell.setPadding(4);
            table.addCell(cell);
        }
    }

    private String texto(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        String value = node.asText(fallback);
        return value == null || value.isBlank() ? fallback : value;
    }

    private Font font(float size, int style, BaseColor color) {
        return new Font(Font.FontFamily.HELVETICA, size, style, color);
    }

    private record PoeReferencia(
            int procesoId,
            long documentoVersionId,
            int version,
            String procesoNombre,
            String fileName,
            String sha256
    ) {
    }

    private record PoeCargado(
            PoeReferencia referencia,
            byte[] contenido,
            String contentType,
            String fileName,
            String sha256
    ) {
    }
}
