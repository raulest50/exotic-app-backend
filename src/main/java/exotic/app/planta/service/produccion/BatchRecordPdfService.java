package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordFirma;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordRevision;
import exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordFirmaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRevisionRepo;
import exotic.app.planta.repo.usuarios.FirmaVisualUsuarioVersionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reconstruye el PDF bajo demanda; nunca persiste el archivo generado. */
@Service
@RequiredArgsConstructor
public class BatchRecordPdfService {

    private static final BaseColor PRIMARY = new BaseColor(38, 74, 97);
    private static final BaseColor LIGHT = new BaseColor(235, 241, 245);
    private final BatchRecordService batchRecordService;
    private final BatchRecordRevisionRepo revisionRepo;
    private final BatchRecordFirmaRepo firmaRepo;
    private final FirmaVisualUsuarioVersionRepo firmaVisualRepo;
    private final ObjectMapper objectMapper;

    public record PdfResult(byte[] contenido, String nombreArchivo, boolean borrador) {
    }

    @Transactional(readOnly = true)
    public PdfResult generar(Long batchRecordId, Integer revisionNumero, boolean actual) {
        if (actual && revisionNumero != null) {
            throw new IllegalArgumentException(
                    "Seleccione una revisión emitida o la vista actual, no ambas.");
        }
        String contenidoCanonico;
        String hash;
        String etiquetaRevision;
        boolean borrador;

        BatchRecordRevision revision = actual
                ? null
                : revisionNumero == null
                    ? revisionRepo.findTopByBatchRecord_IdOrderByNumeroDesc(batchRecordId).orElse(null)
                    : batchRecordService.requireRevision(batchRecordId, revisionNumero);
        if (revision == null) {
            contenidoCanonico = batchRecordService.construirBorradorCanonico(batchRecordId);
            hash = null;
            etiquetaRevision = actual
                    ? "VISTA ACTUAL NO CONTROLADA"
                    : "BORRADOR NO CONTROLADO";
            borrador = true;
        } else {
            contenidoCanonico = revision.getContenidoCanonico();
            hash = revision.getContenidoSha256();
            etiquetaRevision = "Revisión " + revision.getNumero()
                    + " · " + revision.getTipo().name();
            borrador = false;
        }

        try {
            JsonNode root = objectMapper.readTree(contenidoCanonico);
            byte[] pdf = crearPdf(root, etiquetaRevision, hash, borrador, revision);
            String codigo = texto(root.path("codigo"), "batch-record");
            String nombre = codigo.replaceAll("[^A-Za-z0-9._-]", "-")
                    + (borrador ? "-borrador" : "-rev-" + revision.getNumero())
                    + ".pdf";
            return new PdfResult(pdf, nombre, borrador);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "No fue posible reconstruir el PDF del expediente digital.", exception);
        }
    }

    private byte[] crearPdf(
            JsonNode root,
            String etiquetaRevision,
            String hash,
            boolean borrador,
            BatchRecordRevision revision
    ) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 54, 48);
        PdfWriter writer = PdfWriter.getInstance(document, output);
        writer.setPageEvent(new PageNumberEvent());
        document.addTitle("Batch record " + texto(root.path("codigo"), ""));
        document.addSubject("Expediente digital de fabricación reconstruido desde datos estructurados");
        document.open();

        Font title = font(18, Font.BOLD, PRIMARY);
        Font subtitle = font(10, Font.NORMAL, BaseColor.DARK_GRAY);
        Paragraph heading = new Paragraph("EXPEDIENTE DIGITAL DE FABRICACIÓN", title);
        heading.setAlignment(Element.ALIGN_CENTER);
        document.add(heading);
        Paragraph revisionLabel = new Paragraph(etiquetaRevision, subtitle);
        revisionLabel.setAlignment(Element.ALIGN_CENTER);
        document.add(revisionLabel);
        if (borrador) {
            Paragraph warning = new Paragraph(
                    "Documento de consulta. No corresponde a una revisión emitida ni firmada.",
                    font(9, Font.BOLD, BaseColor.RED));
            warning.setAlignment(Element.ALIGN_CENTER);
            document.add(warning);
        }
        document.add(Chunk.NEWLINE);

        addSummary(document, root, hash, revision);
        addObjectSection(document, "Orden", root.path("orden"));
        addObjectSection(document, "Lote de resultado", root.path("lote"));
        addObjectSection(document, "Producto", root.path("producto"));
        addObjectSection(document, "Versión de manufactura congelada", root.path("manufactura"));
        addArraySection(document, "Requerimientos de materiales congelados",
                jsonArray(root.path("requerimientosMaterialesJson")), false);
        addObjectSection(document, "Cantidades", root.path("cantidades"));
        addArraySection(document, "Etapas de fabricación", root.path("etapas"), false);
        addArraySection(document, "Consumos y trazabilidad de lotes", root.path("consumos"), false);
        boolean esquemaV3 = "batch-record-v3".equals(texto(root.path("esquemaVersion"), ""));
        addArraySection(document,
                esquemaV3 ? "Controles de proceso legados (transición)" : "Controles de proceso",
                root.path("controles"), false);
        if (esquemaV3) {
            addArraySection(document, "Controles unificados de Proceso y Calidad",
                    root.path("controlesUnificados").path("requisitos"), false);
        }
        addArraySection(document,
                esquemaV3 ? "Desviaciones legadas (transición)" : "Desviaciones",
                root.path("desviaciones"), false);
        addArraySection(document, "Correcciones y entradas tardías", root.path("correcciones"), false);
        addArraySection(document, "Decisiones de Calidad", root.path("decisionesCalidad"), false);
        if (esquemaV3) {
            addArraySection(document, "Ciclos de revisión de Calidad",
                    root.path("ciclosRevision"), false);
            addArraySection(document, "Secciones documentales devueltas",
                    root.path("seccionesCorreccion"), false);
            addArraySection(document, "Solicitudes de reapertura excepcional",
                    root.path("solicitudesReapertura"), false);
        }
        addArraySection(document, "Firmas electrónicas", root.path("firmas"), true);
        if (revision != null) {
            addFirmasAplicadasARevision(document, revision);
        }

        document.add(Chunk.NEWLINE);
        Paragraph integrity = new Paragraph(
                hash == null
                        ? "Integridad: el borrador se genera desde el estado actual y no posee hash documental emitido."
                        : "Integridad documental SHA-256: " + hash,
                font(8, Font.NORMAL, BaseColor.DARK_GRAY));
        document.add(integrity);
        document.add(new Paragraph(
                "El PDF se reconstruyó dinámicamente desde el expediente estructurado; el archivo PDF no se almacena.",
                font(8, Font.ITALIC, BaseColor.DARK_GRAY)));
        document.close();
        return output.toByteArray();
    }

    private JsonNode jsonArray(JsonNode value) throws IOException {
        if (value.isArray()) return value;
        if (value.isTextual() && !value.asText().isBlank()) {
            JsonNode parsed = objectMapper.readTree(value.asText());
            if (parsed != null && parsed.isArray()) return parsed;
        }
        return objectMapper.createArrayNode();
    }

    private void addSummary(
            Document document,
            JsonNode root,
            String hash,
            BatchRecordRevision revision
    )
            throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        row(table, "Código", texto(root.path("codigo"), ""));
        row(table, "Estado", texto(root.path("estado"), ""));
        row(table, "Revisión documental", texto(root.path("revisionDocumental"), ""));
        row(table, "Creado en", texto(root.path("creadoEn"), ""));
        row(table, "Iniciado en", texto(root.path("iniciadoEn"), ""));
        row(table, "Enviado a Calidad", texto(root.path("enviadoRevisionEn"), ""));
        row(table, "Cerrado en", texto(root.path("cerradoEn"), ""));
        if (revision != null) {
            row(table, "Revisión emitida en", revision.getCreadaEn().toString());
            row(table, "Revisión emitida por", revision.getCreadaPorNombre()
                    + " (" + revision.getCreadaPorUsername() + ")");
            row(table, "Cédula del emisor", revision.getCreadaPorCedula());
            row(table, "Motivo de revisión", revision.getMotivo());
        }
        if (hash != null) row(table, "Hash", hash);
        document.add(table);
    }

    private void addObjectSection(Document document, String title, JsonNode node)
            throws DocumentException {
        addSectionTitle(document, title);
        if (!node.isObject() || node.isEmpty()) {
            document.add(new Paragraph("No registra.", font(9, Font.ITALIC, BaseColor.GRAY)));
            return;
        }
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        Map<String, String> values = new LinkedHashMap<>();
        flatten(node, "", values);
        values.forEach((label, value) -> row(table, label, value));
        document.add(table);
    }

    private void addArraySection(
            Document document,
            String title,
            JsonNode array,
            boolean firmas
    ) throws Exception {
        addSectionTitle(document, title);
        if (!array.isArray() || array.isEmpty()) {
            document.add(new Paragraph("No registra.", font(9, Font.ITALIC, BaseColor.GRAY)));
            return;
        }
        int index = 1;
        for (JsonNode item : array) {
            Paragraph label = new Paragraph(
                    title + " · " + index++, font(9, Font.BOLD, PRIMARY));
            label.setSpacingBefore(4);
            document.add(label);
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            Map<String, String> values = new LinkedHashMap<>();
            flatten(item, "", values);
            values.forEach((key, value) -> row(table, key, value));
            document.add(table);
            if (firmas) addFirmaVisual(document, item.path("firmaVisualVersionId"));
        }
    }

    private void addFirmaVisual(Document document, JsonNode idNode)
            throws DocumentException {
        if (!idNode.canConvertToLong()) return;
        FirmaVisualUsuarioVersion firma = firmaVisualRepo.findById(idNode.longValue()).orElse(null);
        addFirmaVisual(document, firma);
    }

    private void addFirmasAplicadasARevision(
            Document document,
            BatchRecordRevision revision
    ) throws DocumentException {
        java.util.List<BatchRecordFirma> firmas =
                firmaRepo.findByRevision_IdOrderByFirmadoEnAscIdAsc(revision.getId());
        if (firmas.isEmpty()) return;

        addSectionTitle(document, "Firmas aplicadas a esta revisión");
        for (BatchRecordFirma firma : firmas) {
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            row(table, "Alcance", firma.getAlcance().name());
            row(table, "Decisión", firma.getDecision().name());
            row(table, "Método", firma.getMetodo().name());
            row(table, "Firmado en", firma.getFirmadoEn().toString());
            row(table, "Autenticado en", firma.getAutenticadoEn().toString());
            row(table, "Usuario", firma.getUsernameFirmante());
            row(table, "Nombre", firma.getNombreFirmante());
            row(table, "Cédula", firma.getCedulaFirmante());
            row(table, "Rol", firma.getRolFirmante());
            row(table, "Manifestación", firma.getManifestacion());
            row(table, "Comentario", firma.getComentario());
            row(table, "Hash firmado", firma.getHashContenidoFirmado());
            row(table, "Algoritmo", firma.getAlgoritmoHash());
            row(table, "IP de origen", firma.getIpOrigen());
            row(table, "Agente de usuario", firma.getUserAgent());
            document.add(table);
            addFirmaVisual(document, firma.getFirmaVisualVersion());
        }
    }

    private void addFirmaVisual(
            Document document,
            FirmaVisualUsuarioVersion firma
    ) throws DocumentException {
        if (firma == null || firma.getContenido() == null || firma.getContenido().length == 0) return;

        Image image;
        try {
            image = Image.getInstance(firma.getContenido());
        } catch (BadElementException | IOException exception) {
            document.add(new Paragraph(
                    "La representación visual de la firma no pudo renderizarse; la evidencia electrónica permanece en el expediente.",
                    font(7, Font.ITALIC, BaseColor.GRAY)));
            return;
        }

        image.scaleToFit(150, 60);
        image.setAlignment(Element.ALIGN_LEFT);
        document.add(image);
        document.add(new Paragraph(
                "Representación visual opcional · versión " + firma.getVersion(),
                font(7, Font.ITALIC, BaseColor.GRAY)));
    }

    private void addSectionTitle(Document document, String value) throws DocumentException {
        Paragraph paragraph = new Paragraph(value, font(12, Font.BOLD, PRIMARY));
        paragraph.setSpacingBefore(10);
        paragraph.setSpacingAfter(4);
        document.add(paragraph);
    }

    private void row(PdfPTable table, String label, String value) {
        PdfPCell left = new PdfPCell(new Phrase(humanize(label), font(8, Font.BOLD, PRIMARY)));
        left.setBackgroundColor(LIGHT);
        left.setPadding(5);
        PdfPCell right = new PdfPCell(new Phrase(value == null ? "" : value,
                font(8, Font.NORMAL, BaseColor.BLACK)));
        right.setPadding(5);
        table.addCell(left);
        table.addCell(right);
    }

    private void flatten(JsonNode node, String prefix, Map<String, String> target) {
        if (node == null || node.isNull()) {
            target.put(prefix, "");
            return;
        }
        if (node.isValueNode()) {
            target.put(prefix, node.asText());
            return;
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                target.put(prefix, "[]");
                return;
            }
            for (int i = 0; i < node.size(); i++) {
                flatten(node.get(i), prefix + "[" + (i + 1) + "]", target);
            }
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = prefix.isBlank() ? field.getKey() : prefix + "." + field.getKey();
            flatten(field.getValue(), key, target);
        }
    }

    private String humanize(String value) {
        if (value == null || value.isBlank()) return "Valor";
        return value.replace('_', ' ');
    }

    private String texto(JsonNode node, String fallback) {
        return node == null || node.isMissingNode() || node.isNull() ? fallback : node.asText(fallback);
    }

    private Font font(float size, int style, BaseColor color) {
        return new Font(Font.FontFamily.HELVETICA, size, style, color);
    }

    private static final class PageNumberEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            Phrase footer = new Phrase(
                    "Expediente digital · Página " + writer.getPageNumber(),
                    new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.GRAY));
            com.itextpdf.text.pdf.ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_CENTER,
                    footer,
                    (document.right() + document.left()) / 2,
                    document.bottom() - 18,
                    0);
        }
    }
}
