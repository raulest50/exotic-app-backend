package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordFirma;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordRevision;
import exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordFirmaRepo;
import exotic.app.planta.repo.usuarios.FirmaVisualUsuarioVersionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renderiza exclusivamente la primera parte humana del expediente. La
 * composición de OP, dispensaciones y POE permanece en el servicio de anexos.
 */
@Component
@RequiredArgsConstructor
class BatchRecordMainPdfRenderer {

    private static final Locale LOCALE_ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", LOCALE_ES_CO);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_ES_CO);

    private static final BaseColor GOLD = new BaseColor(177, 136, 46);
    private static final BaseColor GOLD_LIGHT = new BaseColor(247, 242, 229);
    private static final BaseColor CHARCOAL = new BaseColor(43, 43, 43);
    private static final BaseColor MUTED = new BaseColor(102, 102, 102);
    private static final BaseColor BORDER = new BaseColor(205, 199, 184);
    private static final BaseColor SUCCESS = new BaseColor(47, 111, 78);
    private static final BaseColor WARNING = new BaseColor(151, 99, 16);
    private static final BaseColor DANGER = new BaseColor(166, 48, 48);

    private final BatchRecordFirmaRepo firmaRepo;
    private final FirmaVisualUsuarioVersionRepo firmaVisualRepo;
    private final ObjectMapper objectMapper;
    private final Map<String, byte[]> optimizedLogoCache = new ConcurrentHashMap<>();

    record LogoDocumental(byte[] contenido, Integer version, String sha256) {
    }

    record RenderContext(
            String etiquetaRevision,
            String hash,
            boolean borrador,
            BatchRecordRevision revision,
            String plantillaAplicada,
            LogoDocumental logo
    ) {
    }

    byte[] render(JsonNode root, RenderContext context) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 34, 34, 72, 42);
        PdfWriter writer = PdfWriter.getInstance(document, output);
        String logoCacheKey = context.logo().sha256() == null
                ? "version-" + context.logo().version() : context.logo().sha256();
        byte[] optimizedLogo = optimizedLogoCache.computeIfAbsent(
                logoCacheKey, ignored -> optimizeLogo(context.logo().contenido()));
        Image logoTemplate = Image.getInstance(optimizedLogo);
        writer.setPageEvent(new MainPageHeaderEvent(
                optimizedLogo,
                text(root.path("codigo"), "Expediente"),
                context.etiquetaRevision()));
        document.addTitle("Batch record " + text(root.path("codigo"), ""));
        document.addSubject(
                "Expediente digital de fabricación - informe humano y anexos controlados");
        document.addCreator("Novum Labs - Exotic App");
        document.open();

        addCover(document, root, context, logoTemplate);
        document.newPage();
        addBatchSummary(document, root);
        addMaterialReconciliation(document, root);
        addStages(document, root);
        addControls(document, root);
        addDeviations(document, root);
        addExceptionalTrace(document, root);
        addQualityDecisions(document, root);
        addSignatures(document, root, context.revision());
        addClosing(document, context);

        document.close();
        return output.toByteArray();
    }

    private byte[] optimizeLogo(byte[] source) {
        final int maximumDimension = 420;
        try (ByteArrayInputStream input = new ByteArrayInputStream(source);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BufferedImage original = ImageIO.read(input);
            if (original == null
                    || (original.getWidth() <= maximumDimension
                    && original.getHeight() <= maximumDimension)) {
                return source;
            }
            double scale = Math.min(
                    (double) maximumDimension / original.getWidth(),
                    (double) maximumDimension / original.getHeight());
            int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
            BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(
                        RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(original, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
            if (!ImageIO.write(resized, "png", output)) return source;
            return output.toByteArray();
        } catch (Exception ignored) {
            return source;
        }
    }

    private void addCover(
            Document document,
            JsonNode root,
            RenderContext context,
            Image logoTemplate
    ) throws Exception {
        Image logo = Image.getInstance(logoTemplate);
        logo.scaleToFit(165, 125);
        logo.setAlignment(Element.ALIGN_CENTER);
        document.add(logo);

        Paragraph title = new Paragraph(
                "EXPEDIENTE DIGITAL DE FABRICACIÓN",
                font(19, Font.BOLD, CHARCOAL));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(14);
        title.setSpacingAfter(5);
        document.add(title);

        Paragraph subtitle = new Paragraph("BATCH RECORD", font(11, Font.BOLD, GOLD));
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(15);
        document.add(subtitle);

        PdfPTable status = new PdfPTable(1);
        status.setWidthPercentage(72);
        status.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell statusCell = new PdfPCell(new Phrase(
                humanEnum(context.etiquetaRevision()),
                font(10, Font.BOLD, context.borrador() ? DANGER : CHARCOAL)));
        statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        statusCell.setBackgroundColor(context.borrador()
                ? new BaseColor(255, 238, 238) : GOLD_LIGHT);
        statusCell.setBorderColor(context.borrador() ? DANGER : GOLD);
        statusCell.setPadding(8);
        status.addCell(statusCell);
        document.add(status);

        document.add(Chunk.NEWLINE);
        PdfPTable identity = new PdfPTable(new float[]{1.2f, 2.1f, 1.2f, 2.1f});
        identity.setWidthPercentage(100);
        JsonNode order = root.path("orden");
        JsonNode product = root.path("producto");
        JsonNode lot = root.path("lote");
        pair(identity, "Código del expediente", text(root.path("codigo"), "No registra"));
        pair(identity, "Orden", orderDescription(order) + " " + text(order.path("id"), ""));
        pair(identity, "Producto", text(product.path("nombre"), "No registra"));
        pair(identity, "Código de producto", text(product.path("id"), "No registra"));
        pair(identity, "Lote", text(lot.path("numero"), "No registra"));
        pair(identity, "Estado de calidad", humanEnum(text(lot.path("estadoCalidad"), "No registra")));
        pair(identity, "Estado del expediente", humanEnum(text(root.path("estado"), "No registra")));
        pair(identity, "Revisión documental", text(root.path("revisionDocumental"), "No registra"));
        document.add(identity);

        document.add(Chunk.NEWLINE);
        addSectionTitle(document, "Control documental");
        PdfPTable control = new PdfPTable(new float[]{1.35f, 2.15f, 1.35f, 2.15f});
        control.setWidthPercentage(100);
        pair(control, "Esquema de datos", text(root.path("esquemaVersion"), "Histórico"));
        pair(control, "Plantilla aplicada", context.plantillaAplicada());
        pair(control, "Logo documental", "Versión " + context.logo().version());
        pair(control, "Registro del contenido", formatDate(text(root.path("registradoEn"), "")));
        if (context.revision() != null) {
            pair(control, "Revisión emitida", formatDate(context.revision().getCreadaEn()));
            pair(control, "Emitida por", valueOr(personName(context.revision().getCreadaPorNombre()), "No registra"));
            pair(control, "Tipo de revisión", humanEnum(context.revision().getTipo().name()));
            pair(control, "Motivo", valueOr(context.revision().getMotivo(), "No registra"));
        }
        document.add(control);

        if (context.borrador()) {
            Paragraph warning = new Paragraph(
                    "Documento de consulta. No corresponde a una revisión emitida ni firmada.",
                    font(9, Font.BOLD, DANGER));
            warning.setAlignment(Element.ALIGN_CENTER);
            warning.setSpacingBefore(14);
            document.add(warning);
        }
    }

    private void addBatchSummary(Document document, JsonNode root) throws Exception {
        addSectionTitle(document, "1. Resumen del lote");
        document.add(note(
                "Vista ejecutiva del lote. El detalle documental de la orden, las dispensaciones "
                        + "y los POE se encuentra en los anexos del mismo archivo."));

        JsonNode order = root.path("orden");
        JsonNode lot = root.path("lote");
        JsonNode quantities = root.path("cantidades");
        JsonNode manufacturing = root.path("manufactura");
        PdfPTable table = new PdfPTable(new float[]{1.35f, 2.15f, 1.35f, 2.15f});
        table.setWidthPercentage(100);
        pair(table, "Tipo de orden", orderDescription(order));
        pair(table, "Estado de la orden", humanEnum(text(order.path("estado"), "No registra")));
        pair(table, "Área operativa", text(order.path("areaOperativa"), "No registra"));
        pair(table, "Departamento", text(order.path("departamentoOperativo"), "No registra"));
        pair(table, "Versión de manufactura", text(manufacturing.path("versionNumero"), "No registra"));
        pair(table, "Estado de calidad", humanEnum(text(lot.path("estadoCalidad"), "No registra")));
        pair(table, "Cantidad planificada", quantity(quantities.path("planificada"), quantities.path("unidad")));
        pair(table, "Cantidad obtenida", quantity(quantities.path("obtenida"), quantities.path("unidad")));
        pair(table, "Rendimiento", calculateYield(
                quantities.path("planificada"), quantities.path("obtenida")));
        pair(table, "Ciclo de revisión", text(root.path("cicloRevisionActual"), "No registra"));
        pair(table, "Inicio real", formatDate(text(root.path("iniciadoEn"), text(order.path("fechaInicio"), ""))));
        pair(table, "Cierre", formatDate(text(root.path("cerradoEn"), text(order.path("fechaFinal"), ""))));
        pair(table, "Envío a Calidad", formatDate(text(root.path("enviadoRevisionEn"), "")));
        pair(table, "Vencimiento del lote", formatDate(text(lot.path("fechaVencimiento"), "")));
        document.add(table);

        PdfPTable metrics = new PdfPTable(4);
        metrics.setWidthPercentage(100);
        metrics.setSpacingBefore(9);
        metric(metrics, "Etapas", completedStages(root.path("etapas")));
        metric(metrics, "Controles", controlsSummary(root));
        metric(metrics, "Desviaciones", deviationsSummary(root));
        metric(metrics, "Documentos", relatedDocumentsSummary(root));
        document.add(metrics);

        String observations = text(root.path("observaciones"), "");
        if (!observations.isBlank()) {
            addNarrative(document, "Observaciones generales", observations);
        }
    }

    private void addMaterialReconciliation(Document document, JsonNode root) throws Exception {
        addSectionTitle(document, "2. Conciliación de materiales");
        document.add(note(
                "La diferencia corresponde a consumo neto documentado menos cantidad planificada. "
                        + "No representa por sí sola una conclusión de conformidad."));

        Map<MaterialKey, MaterialLine> lines = new LinkedHashMap<>();
        JsonNode requirements = jsonArray(root.path("requerimientosMaterialesJson"));
        for (JsonNode requirement : requirements) {
            MaterialKey key = materialKey(requirement.path("productoId"),
                    requirement.path("unidadMedida"), requirement.path("productoNombre"));
            MaterialLine line = lines.computeIfAbsent(key,
                    ignored -> new MaterialLine(
                            text(requirement.path("productoId"), "Sin código"),
                            text(requirement.path("productoNombre"), "Material sin nombre"),
                            text(requirement.path("unidadMedida"), "")));
            BigDecimal planned = decimal(requirement.path("cantidad"));
            if (planned != null) line.planned = line.planned.add(planned);
            line.plannedPresent = true;
        }

        JsonNode consumptions = root.path("consumos");
        if (consumptions.isArray()) {
            for (JsonNode consumption : consumptions) {
                MaterialKey key = materialKey(consumption.path("productoId"),
                        consumption.path("unidad"), consumption.path("productoNombre"));
                MaterialLine line = lines.computeIfAbsent(key,
                        ignored -> new MaterialLine(
                                text(consumption.path("productoId"), "Sin código"),
                                text(consumption.path("productoNombre"), "Material sin nombre"),
                                text(consumption.path("unidad"), "")));
                BigDecimal actual = decimal(consumption.path("cantidad"));
                if (actual != null) line.actual = line.actual.add(actual);
                String lot = text(consumption.path("loteOrigen"), "");
                if (!lot.isBlank()) line.lots.add(lot);
            }
        }

        if (lines.isEmpty()) {
            document.add(noRecords("No se registraron requerimientos ni consumos de materiales."));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{1.05f, 2.55f, 1.05f, 1.15f, 1.15f, 1.7f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSplitLate(false);
        for (String header : List.of(
                "Código", "Material", "Planificado", "Documentado", "Diferencia", "Lotes de origen")) {
            header(table, header);
        }
        for (MaterialLine line : lines.values()) {
            cell(table, line.code);
            cell(table, line.name + (line.plannedPresent ? "" : " (no planificado)"));
            cell(table, line.plannedPresent ? number(line.planned) + unitSuffix(line.unit) : "No planificado");
            cell(table, number(line.actual) + unitSuffix(line.unit));
            cell(table, number(line.actual.subtract(line.planned)) + unitSuffix(line.unit));
            cell(table, line.lots.isEmpty() ? "Sin lote físico registrado" : String.join(", ", line.lots));
        }
        document.add(table);
    }

    private void addStages(Document document, JsonNode root) throws Exception {
        addSectionTitle(document, "3. Ejecución por etapas");
        JsonNode stages = root.path("etapas");
        if (!stages.isArray() || stages.isEmpty()) {
            document.add(noRecords("No se registraron etapas de fabricación."));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{0.42f, 2.0f, 1.0f, 1.6f, 1.35f, 1.65f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSplitLate(false);
        for (String header : List.of("#", "Etapa / área", "Estado", "Inicio / fin", "Reportada por", "POE aplicado")) {
            header(table, header);
        }
        for (JsonNode stage : stages) {
            cell(table, text(stage.path("secuencia"), "-"));
            cell(table, text(stage.path("nombre"), "Etapa")
                    + lineBreak(text(stage.path("areaNombre"), "")));
            statusCell(table, text(stage.path("estado"), "No registra"));
            cell(table, dateRange(stage.path("iniciadaEn"), stage.path("completadaEn")));
            cell(table, person(stage.path("reportadaPor")));
            cell(table, poeLabel(stage.path("poe")));
            String observations = text(stage.path("observaciones"), "");
            if (!observations.isBlank()) {
                PdfPCell observationsCell = new PdfPCell(new Phrase(
                        "Observaciones: " + observations, font(7.4f, Font.ITALIC, MUTED)));
                observationsCell.setColspan(6);
                observationsCell.setBorderColor(BORDER);
                observationsCell.setPadding(5);
                table.addCell(observationsCell);
            }
        }
        document.add(table);
    }

    private void addControls(Document document, JsonNode root) throws Exception {
        addSectionTitle(document, "4. Controles de proceso y calidad");
        JsonNode unified = root.path("controlesUnificados").path("requisitos");
        if (unified.isArray() && !unified.isEmpty()) {
            addUnifiedControls(document, unified);
            return;
        }
        addLegacyControls(document, root.path("controles"));
    }

    private void addUnifiedControls(Document document, JsonNode controls) throws Exception {
        int index = 1;
        for (JsonNode control : controls) {
            Paragraph label = new Paragraph(
                    "Control " + index++ + " · " + text(control.path("planNombre"), "Control sin nombre"),
                    font(10, Font.BOLD, CHARCOAL));
            label.setSpacingBefore(8);
            label.setSpacingAfter(4);
            document.add(label);

            PdfPTable summary = new PdfPTable(new float[]{1.15f, 2.0f, 1.15f, 2.0f});
            summary.setWidthPercentage(100);
            pair(summary, "Estado", humanEnum(text(control.path("estado"), "No registra")));
            pair(summary, "Ámbito", humanEnum(text(control.path("ambito"), "No registra")));
            pair(summary, "Etapa", text(control.path("etapaNombre"), text(control.path("nodoNombre"), "No registra")));
            pair(summary, "Punto de aplicación", humanEnum(text(control.path("puntoAplicacion"), "No registra")));
            pair(summary, "Momento", humanEnum(text(control.path("momento"), "No registra")));
            pair(summary, "Plan", text(control.path("planCodigo"), "")
                    + " · v" + text(control.path("versionNumero"), "-"));
            pair(summary, "Responsable de ejecución", humanEnum(text(control.path("responsableEjecucion"), "No registra")));
            pair(summary, "Responsable de revisión", humanEnum(text(control.path("responsableRevision"), "No registra")));
            document.add(summary);

            JsonNode executions = control.path("ejecuciones");
            if (!executions.isArray() || executions.isEmpty()) {
                document.add(noRecords("Pendiente de ejecución."));
            } else {
                int executionIndex = 1;
                for (JsonNode execution : executions) {
                    addUnifiedExecution(document, control, execution, executionIndex++);
                }
            }
            addRevalidations(document, control.path("revalidaciones"));
        }
    }

    private void addUnifiedExecution(
            Document document,
            JsonNode control,
            JsonNode execution,
            int index
    ) throws Exception {
        Paragraph label = new Paragraph(
                "Ejecución " + index + " · " + humanEnum(text(execution.path("resultado"), "Sin resultado")),
                font(8.5f, Font.BOLD, statusColor(text(execution.path("resultado"), ""))));
        label.setSpacingBefore(5);
        label.setSpacingAfter(3);
        document.add(label);

        PdfPTable executionSummary = new PdfPTable(new float[]{1.0f, 2.0f, 1.0f, 2.0f});
        executionSummary.setWidthPercentage(100);
        pair(executionSummary, "Registrada", formatDate(text(execution.path("fechaRegistro"), "")));
        pair(executionSummary, "Registrada por", person(execution.path("usuario")));
        String observations = text(execution.path("observaciones"), "");
        if (!observations.isBlank()) {
            pair(executionSummary, "Observaciones", observations);
            pair(executionSummary, "Tipo", text(execution.path("motivoRepeticion"), "Inicial"));
        }
        document.add(executionSummary);

        PdfPTable readings = new PdfPTable(new float[]{2.0f, 2.15f, 0.9f, 2.25f});
        readings.setWidthPercentage(100);
        readings.setHeaderRows(1);
        readings.setSplitLate(false);
        for (String header : List.of("Característica", "Especificación", "Muestra", "Lecturas")) {
            header(readings, header);
        }

        JsonNode characteristics = control.path("caracteristicas");
        JsonNode samples = execution.path("muestras");
        boolean hasRows = false;
        if (characteristics.isArray()) {
            for (JsonNode characteristic : characteristics) {
                List<JsonNode> matching = matchingSamples(samples, characteristic.path("id"));
                if (matching.isEmpty()) {
                    cell(readings, text(characteristic.path("nombre"), "Característica"));
                    cell(readings, specification(characteristic));
                    cell(readings, "-");
                    cell(readings, "Sin lectura registrada");
                    hasRows = true;
                } else {
                    for (JsonNode sample : matching) {
                        cell(readings, text(characteristic.path("nombre"), "Característica"));
                        cell(readings, specification(characteristic));
                        cell(readings, text(sample.path("numeroMuestra"), "-"));
                        cell(readings, readings(sample.path("lecturas")));
                        hasRows = true;
                    }
                }
            }
        }
        if (hasRows) document.add(readings);
    }

    private void addRevalidations(Document document, JsonNode revalidations) throws Exception {
        if (!revalidations.isArray() || revalidations.isEmpty()) return;
        Paragraph title = new Paragraph("Revalidaciones", font(8.5f, Font.BOLD, CHARCOAL));
        title.setSpacingBefore(5);
        document.add(title);
        PdfPTable table = new PdfPTable(new float[]{0.7f, 1.35f, 1.4f, 3.2f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : List.of("Ciclo", "Fecha", "Confirmada por", "Justificación")) {
            header(table, header);
        }
        for (JsonNode item : revalidations) {
            cell(table, text(item.path("cicloRevisionNumero"), "-"));
            cell(table, formatDate(text(item.path("confirmadaEn"), "")));
            cell(table, person(item.path("confirmadaPor")));
            cell(table, text(item.path("justificacion"), "No registra"));
        }
        document.add(table);
    }

    private void addLegacyControls(Document document, JsonNode controls) throws Exception {
        if (!controls.isArray() || controls.isEmpty()) {
            document.add(noRecords("No se registraron controles de proceso o calidad."));
            return;
        }
        document.add(note("Información histórica presentada desde el modelo de controles legado."));
        int index = 1;
        for (JsonNode control : controls) {
            Paragraph label = new Paragraph(
                    "Control histórico " + index++ + " · "
                            + humanEnum(text(control.path("resultado"), "Sin resultado")),
                    font(10, Font.BOLD, CHARCOAL));
            label.setSpacingBefore(7);
            document.add(label);
            PdfPTable summary = new PdfPTable(new float[]{1.0f, 2.0f, 1.0f, 2.0f});
            summary.setWidthPercentage(100);
            pair(summary, "Área", text(control.path("areaNombre"), "No registra"));
            pair(summary, "Plan versión", text(control.path("plantillaVersion"), "No registra"));
            pair(summary, "Registrado", formatDate(text(control.path("fechaRegistro"), "")));
            pair(summary, "Registrado por", person(control.path("registradoPor")));
            document.add(summary);

            JsonNode samples = control.path("muestras");
            if (samples.isArray() && !samples.isEmpty()) {
                PdfPTable readings = new PdfPTable(new float[]{2.0f, 2.15f, 0.9f, 2.25f});
                readings.setWidthPercentage(100);
                readings.setHeaderRows(1);
                for (String header : List.of("Característica", "Especificación", "Muestra", "Lecturas")) {
                    header(readings, header);
                }
                for (JsonNode sample : samples) {
                    cell(readings, text(sample.path("caracteristica"), "Característica"));
                    cell(readings, specification(sample));
                    cell(readings, text(sample.path("numeroMuestra"), "-"));
                    cell(readings, readings(sample.path("lecturas")));
                }
                document.add(readings);
            }
            String observations = text(control.path("observaciones"), "");
            if (!observations.isBlank()) addNarrative(document, "Observaciones", observations);
        }
    }

    private void addDeviations(Document document, JsonNode root) throws Exception {
        addSectionTitle(document, "5. Desviaciones");
        JsonNode general = root.path("desviaciones");
        List<ControlDeviation> controlDeviations = controlDeviations(root);
        boolean hasGeneral = general.isArray() && !general.isEmpty();
        if (!hasGeneral && controlDeviations.isEmpty()) {
            document.add(noRecords("No se registraron desviaciones."));
            return;
        }

        if (hasGeneral) {
            int index = 1;
            for (JsonNode deviation : general) {
                Paragraph label = new Paragraph(
                        "Desviación " + index++ + " · "
                                + text(deviation.path("codigo"), "Sin código") + " · "
                                + humanEnum(text(deviation.path("estado"), "Sin estado")),
                        font(9.5f, Font.BOLD, statusColor(text(deviation.path("estado"), ""))));
                label.setSpacingBefore(7);
                document.add(label);
                PdfPTable table = new PdfPTable(new float[]{1.15f, 2.0f, 1.15f, 2.0f});
                table.setWidthPercentage(100);
                pair(table, "Ocurrida", formatDate(text(deviation.path("ocurridaEn"), "")));
                pair(table, "Detectada", formatDate(text(deviation.path("detectadaEn"), "")));
                pair(table, "Detectada por", person(deviation.path("detectadaPor")));
                pair(table, "Origen", humanEnum(text(deviation.path("origen"), "No registra")));
                document.add(table);
                addNarrativeIfPresent(document, "Descripción", deviation.path("descripcion"));
                addNarrativeIfPresent(document, "Acción inmediata", deviation.path("accionInmediata"));
                addNarrativeIfPresent(document, "Evaluación de impacto", deviation.path("evaluacionImpacto"));
                addNarrativeIfPresent(document, "Causa raíz", deviation.path("causaRaiz"));
                addNarrativeIfPresent(document, "Acciones correctivas y preventivas",
                        deviation.path("accionesCorrectivasPreventivas"));
                addNarrativeIfPresent(document, "Resolución", deviation.path("resolucion"));
            }
        }

        for (ControlDeviation item : controlDeviations) {
            JsonNode deviation = item.deviation();
            Paragraph label = new Paragraph(
                    "Desviación de control · " + item.controlName() + " · "
                            + humanEnum(text(deviation.path("estado"), "Sin estado")),
                    font(9.5f, Font.BOLD, statusColor(text(deviation.path("estado"), ""))));
            label.setSpacingBefore(7);
            document.add(label);
            PdfPTable table = new PdfPTable(new float[]{1.15f, 2.0f, 1.15f, 2.0f});
            table.setWidthPercentage(100);
            pair(table, "Ámbito", humanEnum(text(deviation.path("ambito"), "No registra")));
            pair(table, "Disposición", humanEnum(text(deviation.path("disposicion"), "Pendiente")));
            pair(table, "Abierta", formatDate(text(deviation.path("abiertaEn"), "")));
            pair(table, "Abierta por", person(deviation.path("abiertaPor")));
            pair(table, "Cerrada", formatDate(text(deviation.path("cerradaEn"), "")));
            pair(table, "Cerrada por", person(deviation.path("cerradaPor")));
            document.add(table);
            addNarrativeIfPresent(document, "Investigación", deviation.path("investigacion"));
            addNarrativeIfPresent(document, "Resolución", deviation.path("resolucion"));
            addNarrativeIfPresent(document, "Justificación de la disposición",
                    deviation.path("justificacionDisposicion"));
        }
    }

    private void addExceptionalTrace(Document document, JsonNode root) throws Exception {
        addSectionTitle(document, "6. Correcciones y trazabilidad excepcional");
        JsonNode corrections = root.path("correcciones");
        JsonNode cycles = root.path("ciclosRevision");
        JsonNode returnedSections = root.path("seccionesCorreccion");
        JsonNode reopenings = root.path("solicitudesReapertura");
        if (isEmptyArray(corrections) && isEmptyArray(cycles)
                && isEmptyArray(returnedSections) && isEmptyArray(reopenings)) {
            document.add(noRecords("No se registraron correcciones ni eventos excepcionales."));
            return;
        }

        if (!isEmptyArray(corrections)) {
            Paragraph subtitle = subsection("Correcciones y entradas tardías");
            document.add(subtitle);
            PdfPTable table = new PdfPTable(new float[]{1.15f, 1.45f, 2.35f, 2.35f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String header : List.of("Fecha", "Responsable", "Motivo", "Cambio registrado")) {
                header(table, header);
            }
            for (JsonNode correction : corrections) {
                cell(table, formatDate(text(correction.path("corregidaEn"), "")));
                cell(table, person(correction.path("corregidaPor")));
                cell(table, text(correction.path("motivo"), "No registra"));
                cell(table, safeChange(correction.path("valorAnterior"), correction.path("valorNuevo")));
            }
            document.add(table);
        }

        if (!isEmptyArray(cycles)) {
            document.add(subsection("Ciclos de revisión de Calidad"));
            PdfPTable table = new PdfPTable(new float[]{0.55f, 1.1f, 1.15f, 1.4f, 1.4f, 2.0f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String header : List.of("Ciclo", "Origen", "Estado", "Envío", "Cierre", "Motivo")) {
                header(table, header);
            }
            for (JsonNode cycle : cycles) {
                cell(table, text(cycle.path("numero"), "-"));
                cell(table, humanEnum(text(cycle.path("origen"), "No registra")));
                statusCell(table, text(cycle.path("estado"), "No registra"));
                cell(table, formatDate(text(cycle.path("enviadoEn"), ""))
                        + lineBreak(person(cycle.path("enviadoPor"))));
                cell(table, formatDate(text(cycle.path("cerradoEn"), ""))
                        + lineBreak(person(cycle.path("cerradoPor"))));
                cell(table, text(cycle.path("motivoEnvio"), "No registra"));
            }
            document.add(table);
        }

        if (!isEmptyArray(returnedSections)) {
            document.add(subsection("Secciones devueltas para corrección"));
            PdfPTable table = new PdfPTable(new float[]{0.65f, 1.5f, 1.0f, 1.5f, 2.5f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String header : List.of("Ciclo", "Sección", "Estado", "Atención", "Justificación")) {
                header(table, header);
            }
            for (JsonNode section : returnedSections) {
                cell(table, text(section.path("cicloRevisionNumero"), "-"));
                cell(table, humanEnum(text(section.path("seccion"), "No registra")));
                statusCell(table, text(section.path("estado"), "No registra"));
                cell(table, formatDate(text(section.path("atendidaEn"), ""))
                        + lineBreak(person(section.path("atendidaPor"))));
                cell(table, text(section.path("justificacion"), "No registra"));
            }
            document.add(table);
        }

        if (!isEmptyArray(reopenings)) {
            document.add(subsection("Solicitudes de reapertura excepcional"));
            for (JsonNode reopening : reopenings) {
                PdfPTable table = new PdfPTable(new float[]{1.15f, 2.0f, 1.15f, 2.0f});
                table.setWidthPercentage(100);
                pair(table, "Estado", humanEnum(text(reopening.path("estado"), "No registra")));
                pair(table, "Ciclo", text(reopening.path("cicloRevisionNumero"), "No registra"));
                pair(table, "Solicitada", formatDate(text(reopening.path("solicitadaEn"), "")));
                pair(table, "Solicitada por", person(reopening.path("solicitadaPor")));
                pair(table, "Aprobada", formatDate(text(reopening.path("aprobadaEn"), "")));
                pair(table, "Aprobada por", person(reopening.path("aprobadaPor")));
                document.add(table);
                addNarrativeIfPresent(document, "Motivo", reopening.path("motivo"));
                addNarrativeIfPresent(document, "Evidencia", reopening.path("evidencia"));
                addNarrativeIfPresent(document, "Alcance", safeNode(reopening.path("alcance")));
                addNarrativeIfPresent(document, "Motivo de aprobación", reopening.path("motivoAprobacion"));
            }
        }
    }

    private void addQualityDecisions(Document document, JsonNode root) throws Exception {
        addSectionTitle(document, "7. Decisiones de Calidad");
        JsonNode decisions = root.path("decisionesCalidad");
        if (!decisions.isArray() || decisions.isEmpty()) {
            document.add(noRecords("No se registraron decisiones de Calidad."));
            return;
        }
        PdfPTable table = new PdfPTable(new float[]{0.65f, 1.25f, 1.35f, 1.45f, 2.8f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : List.of("Ciclo", "Decisión", "Fecha", "Responsable", "Motivo")) {
            header(table, header);
        }
        for (JsonNode decision : decisions) {
            cell(table, text(decision.path("cicloRevision"), "-"));
            statusCell(table, text(decision.path("decision"), "No registra"));
            cell(table, formatDate(text(decision.path("decididaEn"), "")));
            cell(table, person(decision.path("decididaPor")));
            cell(table, text(decision.path("motivo"), "No registra"));
        }
        document.add(table);
    }

    private void addSignatures(
            Document document,
            JsonNode root,
            BatchRecordRevision revision
    ) throws Exception {
        addSectionTitle(document, "8. Firmas y aprobaciones");
        if (revision != null) {
            List<BatchRecordFirma> signatures =
                    firmaRepo.findByRevision_IdOrderByFirmadoEnAscIdAsc(revision.getId());
            if (!signatures.isEmpty()) {
                for (BatchRecordFirma signature : signatures) addSignature(document, signature);
                return;
            }
        }

        JsonNode signatures = root.path("firmas");
        if (!signatures.isArray() || signatures.isEmpty()) {
            document.add(noRecords(revision == null
                    ? "La vista actual no registra firmas."
                    : "Esta revisión no registra firmas aplicadas."));
            return;
        }
        for (JsonNode signature : signatures) addSignature(document, signature);
    }

    private void addSignature(Document document, BatchRecordFirma signature) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{1.05f, 2.0f, 1.05f, 2.0f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        pair(table, "Firmante", valueOr(personName(signature.getNombreFirmante()), "No registra"));
        pair(table, "Rol", valueOr(signature.getRolFirmante(), "No registra"));
        pair(table, "Alcance", humanEnum(signature.getAlcance().name()));
        pair(table, "Decisión", humanEnum(signature.getDecision().name()));
        pair(table, "Fecha", formatDate(signature.getFirmadoEn()));
        pair(table, "Manifestación", valueOr(signature.getManifestacion(), "No registra"));
        document.add(table);
        if (signature.getComentario() != null && !signature.getComentario().isBlank()) {
            addNarrative(document, "Comentario", signature.getComentario());
        }
        addVisualSignature(document, signature.getFirmaVisualVersion());
    }

    private void addSignature(Document document, JsonNode signature) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{1.05f, 2.0f, 1.05f, 2.0f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        pair(table, "Firmante", valueOr(personName(text(signature.path("nombre"), "")), "No registra"));
        pair(table, "Rol", humanEnum(text(signature.path("rol"), "No registra")));
        pair(table, "Alcance", humanEnum(text(signature.path("alcance"), "No registra")));
        pair(table, "Decisión", humanEnum(text(signature.path("decision"), "No registra")));
        pair(table, "Fecha", formatDate(text(signature.path("firmadoEn"), "")));
        pair(table, "Manifestación", text(signature.path("manifestacion"), "No registra"));
        document.add(table);
        addNarrativeIfPresent(document, "Comentario", signature.path("comentario"));
        if (signature.path("firmaVisualVersionId").canConvertToLong()) {
            addVisualSignature(document, firmaVisualRepo
                    .findById(signature.path("firmaVisualVersionId").longValue()).orElse(null));
        }
    }

    private void addVisualSignature(Document document, FirmaVisualUsuarioVersion signature)
            throws Exception {
        if (signature == null || signature.getContenido() == null
                || signature.getContenido().length == 0) return;
        try {
            Image image = Image.getInstance(signature.getContenido());
            image.scaleToFit(135, 52);
            image.setAlignment(Element.ALIGN_LEFT);
            image.setSpacingBefore(3);
            document.add(image);
            document.add(new Paragraph(
                    "Representación visual de la firma · versión " + signature.getVersion(),
                    font(6.8f, Font.ITALIC, MUTED)));
        } catch (Exception exception) {
            document.add(new Paragraph(
                    "La representación visual no pudo mostrarse; la evidencia electrónica "
                            + "permanece conservada en el expediente.",
                    font(7, Font.ITALIC, MUTED)));
        }
    }

    private void addClosing(Document document, RenderContext context) throws Exception {
        document.add(Chunk.NEWLINE);
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(GOLD_LIGHT);
        cell.setBorderColor(GOLD);
        cell.setPadding(8);
        cell.addElement(new Paragraph(
                "DOCUMENTOS RELACIONADOS",
                font(8.5f, Font.BOLD, CHARCOAL)));
        cell.addElement(new Paragraph(
                "A continuación se incorpora el índice documental y, sin modificación, "
                        + "la orden, las dispensaciones y los POE relacionados.",
                font(8, Font.NORMAL, CHARCOAL)));
        cell.addElement(new Paragraph(
                context.hash() == null
                        ? "Integridad: vista no controlada generada desde el estado actual."
                        : "Integridad: contenido canónico verificado para la revisión emitida.",
                font(7.2f, Font.ITALIC, MUTED)));
        box.addCell(cell);
        document.add(box);
    }

    private List<ControlDeviation> controlDeviations(JsonNode root) {
        List<ControlDeviation> result = new ArrayList<>();
        JsonNode controls = root.path("controlesUnificados").path("requisitos");
        if (!controls.isArray()) return result;
        for (JsonNode control : controls) {
            JsonNode deviations = control.path("desviaciones");
            if (!deviations.isArray()) continue;
            for (JsonNode deviation : deviations) {
                result.add(new ControlDeviation(
                        text(control.path("planNombre"), "Control sin nombre"), deviation));
            }
        }
        return result;
    }

    private List<JsonNode> matchingSamples(JsonNode samples, JsonNode characteristicId) {
        List<JsonNode> result = new ArrayList<>();
        if (!samples.isArray()) return result;
        String expected = characteristicId.asText("");
        for (JsonNode sample : samples) {
            if (expected.equals(sample.path("caracteristicaId").asText(""))) result.add(sample);
        }
        return result;
    }

    private String specification(JsonNode characteristic) {
        if (!characteristic.path("valorBooleanoEsperado").isMissingNode()
                && !characteristic.path("valorBooleanoEsperado").isNull()) {
            return "Esperado: " + yesNo(characteristic.path("valorBooleanoEsperado").asBoolean());
        }
        String unit = text(characteristic.path("unidadSimbolo"),
                text(characteristic.path("unidad"), ""));
        BigDecimal lower = decimal(characteristic.path("limiteInferior"));
        BigDecimal upper = decimal(characteristic.path("limiteSuperior"));
        BigDecimal target = decimal(characteristic.path("objetivo"));
        List<String> parts = new ArrayList<>();
        if (target != null) parts.add("Objetivo " + number(target) + unitSuffix(unit));
        if (lower != null || upper != null) {
            parts.add((lower == null ? "-∞" : number(lower)) + " a "
                    + (upper == null ? "+∞" : number(upper)) + unitSuffix(unit));
        }
        return parts.isEmpty() ? "Sin especificación numérica" : String.join(" · ", parts);
    }

    private String readings(JsonNode readings) {
        if (!readings.isArray() || readings.isEmpty()) return "Sin lectura registrada";
        List<String> values = new ArrayList<>();
        for (JsonNode reading : readings) {
            if (!reading.path("valorNumerico").isMissingNode()
                    && !reading.path("valorNumerico").isNull()) {
                values.add(number(decimal(reading.path("valorNumerico"))));
            } else if (!reading.path("valorBooleano").isMissingNode()
                    && !reading.path("valorBooleano").isNull()) {
                values.add(yesNo(reading.path("valorBooleano").asBoolean()));
            }
        }
        return values.isEmpty() ? "Sin lectura registrada" : String.join(" / ", values);
    }

    private String completedStages(JsonNode stages) {
        if (!stages.isArray()) return "0 de 0";
        int completed = 0;
        for (JsonNode stage : stages) {
            String state = text(stage.path("estado"), "");
            if ("COMPLETADA".equals(state) || "OMITIDA".equals(state)) completed++;
        }
        return completed + " de " + stages.size();
    }

    private String controlsSummary(JsonNode root) {
        JsonNode unified = root.path("controlesUnificados").path("requisitos");
        JsonNode controls = unified.isArray() && !unified.isEmpty()
                ? unified : root.path("controles");
        if (!controls.isArray()) return "0 registrados";
        int conform = 0;
        for (JsonNode control : controls) {
            String value = text(control.path("estado"), text(control.path("resultado"), ""));
            if ("CONFORME".equals(value) || "ACEPTADO_POR_DESVIACION".equals(value)) conform++;
        }
        return conform + " conformes de " + controls.size();
    }

    private String deviationsSummary(JsonNode root) {
        int total = root.path("desviaciones").isArray() ? root.path("desviaciones").size() : 0;
        int open = countOpen(root.path("desviaciones"));
        for (ControlDeviation item : controlDeviations(root)) {
            total++;
            if (isOpen(item.deviation().path("estado"))) open++;
        }
        return open + " abiertas de " + total;
    }

    private int countOpen(JsonNode deviations) {
        if (!deviations.isArray()) return 0;
        int result = 0;
        for (JsonNode deviation : deviations) if (isOpen(deviation.path("estado"))) result++;
        return result;
    }

    private boolean isOpen(JsonNode stateNode) {
        String state = text(stateNode, "");
        return !state.isBlank() && !"CERRADA".equals(state) && !"RESUELTA".equals(state);
    }

    private String relatedDocumentsSummary(JsonNode root) {
        int dispensations = root.path("dispensaciones").isArray()
                ? root.path("dispensaciones").size() : 0;
        Set<String> poeVersions = new LinkedHashSet<>();
        JsonNode stages = root.path("etapas");
        if (stages.isArray()) {
            for (JsonNode stage : stages) {
                JsonNode poe = stage.path("poe");
                if (poe.isObject()) {
                    String key = text(poe.path("documentoVersionId"), poeLabel(poe));
                    if (!key.isBlank()) poeVersions.add(key);
                }
            }
        }
        return "1 orden · " + dispensations + " dispensaciones · "
                + poeVersions.size() + " POE";
    }

    private String poeLabel(JsonNode poe) {
        if (!poe.isObject() || poe.isEmpty()) return "No registra";
        String name = text(poe.path("procesoProduccionNombre"), "POE");
        String version = text(poe.path("version"), "");
        return version.isBlank() ? name : name + " · v" + version;
    }

    private String dateRange(JsonNode start, JsonNode end) {
        return "Inicio: " + formatDate(text(start, ""))
                + "\nFin: " + formatDate(text(end, ""));
    }

    private String safeChange(JsonNode before, JsonNode after) {
        return "Anterior: " + safeBusinessValue(text(before, ""))
                + "\nNuevo: " + safeBusinessValue(text(after, ""));
    }

    private JsonNode safeNode(JsonNode value) {
        if (!value.isTextual()) return objectMapper.getNodeFactory().textNode(
                value.isMissingNode() || value.isNull()
                        ? "" : "Detalle conservado en la trazabilidad auditable");
        return objectMapper.getNodeFactory().textNode(safeBusinessValue(value.asText()));
    }

    private String safeBusinessValue(String value) {
        if (value == null || value.isBlank()) return "No registra";
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "Detalle conservado en la trazabilidad auditable";
        }
        return trimmed;
    }

    private MaterialKey materialKey(JsonNode idNode, JsonNode unitNode, JsonNode nameNode) {
        String id = text(idNode, "");
        String name = text(nameNode, "");
        return new MaterialKey(id.isBlank() ? name : id, text(unitNode, ""));
    }

    private JsonNode jsonArray(JsonNode value) throws Exception {
        if (value.isArray()) return value;
        if (value.isTextual() && !value.asText().isBlank()) {
            JsonNode parsed = objectMapper.readTree(value.asText());
            if (parsed != null && parsed.isArray()) return parsed;
        }
        return objectMapper.createArrayNode();
    }

    private boolean isEmptyArray(JsonNode value) {
        return !value.isArray() || value.isEmpty();
    }

    private BigDecimal decimal(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        try {
            return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String calculateYield(JsonNode plannedNode, JsonNode obtainedNode) {
        BigDecimal planned = decimal(plannedNode);
        BigDecimal obtained = decimal(obtainedNode);
        if (planned == null || obtained == null || planned.signum() <= 0) return "No disponible";
        return number(obtained.multiply(BigDecimal.valueOf(100))
                .divide(planned, 2, RoundingMode.HALF_UP)) + " %";
    }

    private String quantity(JsonNode value, JsonNode unit) {
        BigDecimal decimal = decimal(value);
        return decimal == null ? "No registra" : number(decimal) + unitSuffix(text(unit, ""));
    }

    private String number(BigDecimal value) {
        if (value == null) return "-";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(LOCALE_ES_CO);
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,##0.######", symbols);
        format.setParseBigDecimal(true);
        return format.format(value.stripTrailingZeros());
    }

    private String formatDate(Object raw) {
        if (raw == null) return "No registra";
        return formatDate(raw.toString());
    }

    private String formatDate(String raw) {
        if (raw == null || raw.isBlank()) return "No registra";
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value).format(DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // Continúa con formatos locales sin zona.
        }
        try {
            return LocalDateTime.parse(value).format(DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // Continúa con fecha simple.
        }
        try {
            return LocalDate.parse(value).format(DATE);
        } catch (DateTimeParseException ignored) {
            return value;
        }
    }

    private String person(JsonNode person) {
        if (!person.isObject() || person.isEmpty()) return "No registra";
        return valueOr(personName(text(person.path("nombre"), "")), "No registra");
    }

    private String personName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String humanEnum(String value) {
        if (value == null || value.isBlank()) return "No registra";
        String normalized = value.trim().replace('_', ' ').toLowerCase(LOCALE_ES_CO);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String orderDescription(JsonNode order) {
        return "ORDEN_FABRICACION".equals(text(order.path("tipo"), ""))
                ? "Orden de fabricación" : "Orden de producción";
    }

    private String text(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        String value = node.asText(fallback);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String yesNo(boolean value) {
        return value ? "Sí" : "No";
    }

    private String unitSuffix(String unit) {
        return unit == null || unit.isBlank() ? "" : " " + unit.trim();
    }

    private String lineBreak(String value) {
        return value == null || value.isBlank() || "No registra".equals(value)
                ? "" : "\n" + value;
    }

    private BaseColor statusColor(String value) {
        if (value == null) return CHARCOAL;
        String normalized = value.toUpperCase(LOCALE_ES_CO);
        if (normalized.contains("NO_CONFORME") || normalized.contains("RECHAZ")
                || normalized.contains("ANUL") || normalized.contains("ABIERTA")) {
            return DANGER;
        }
        if (normalized.contains("CONFORME") || normalized.contains("APROB")
                || normalized.contains("CERRAD") || normalized.contains("COMPLET")) {
            return SUCCESS;
        }
        return WARNING;
    }

    private void addSectionTitle(Document document, String value) throws Exception {
        Paragraph title = new Paragraph(value, font(12.5f, Font.BOLD, CHARCOAL));
        title.setSpacingBefore(13);
        title.setSpacingAfter(5);
        title.setKeepTogether(true);
        document.add(title);
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingAfter(5);
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setFixedHeight(2);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(GOLD);
        line.addCell(cell);
        document.add(line);
    }

    private Paragraph subsection(String value) {
        Paragraph title = new Paragraph(value, font(9.2f, Font.BOLD, CHARCOAL));
        title.setSpacingBefore(7);
        title.setSpacingAfter(3);
        return title;
    }

    private Paragraph note(String value) {
        Paragraph paragraph = new Paragraph(value, font(7.8f, Font.ITALIC, MUTED));
        paragraph.setSpacingAfter(6);
        paragraph.setLeading(10);
        return paragraph;
    }

    private Paragraph noRecords(String value) {
        Paragraph paragraph = new Paragraph(value, font(8.2f, Font.ITALIC, MUTED));
        paragraph.setSpacingAfter(5);
        return paragraph;
    }

    private void addNarrativeIfPresent(Document document, String label, JsonNode value)
            throws Exception {
        String text = this.text(value, "");
        if (!text.isBlank()) addNarrative(document, label, text);
    }

    private void addNarrative(Document document, String label, String value) throws Exception {
        Paragraph paragraph = new Paragraph();
        paragraph.setSpacingBefore(3);
        paragraph.setSpacingAfter(3);
        paragraph.setLeading(10);
        paragraph.add(new Chunk(label + ": ", font(7.8f, Font.BOLD, CHARCOAL)));
        paragraph.add(new Chunk(valueOr(value, "No registra"), font(7.8f, Font.NORMAL, CHARCOAL)));
        document.add(paragraph);
    }

    private void pair(PdfPTable table, String label, String value) {
        PdfPCell key = new PdfPCell(new Phrase(label, font(7.4f, Font.BOLD, CHARCOAL)));
        key.setBackgroundColor(GOLD_LIGHT);
        key.setBorderColor(BORDER);
        key.setPadding(5);
        table.addCell(key);
        PdfPCell content = new PdfPCell(new Phrase(valueOr(value, "No registra"),
                font(7.4f, Font.NORMAL, CHARCOAL)));
        content.setBorderColor(BORDER);
        content.setPadding(5);
        table.addCell(content);
    }

    private void metric(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(BORDER);
        cell.setPadding(7);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph title = new Paragraph(label, font(7.2f, Font.BOLD, GOLD));
        title.setAlignment(Element.ALIGN_CENTER);
        Paragraph content = new Paragraph(value, font(8.3f, Font.BOLD, CHARCOAL));
        content.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(title);
        cell.addElement(content);
        table.addCell(cell);
    }

    private void header(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font(7.2f, Font.BOLD, BaseColor.WHITE)));
        cell.setBackgroundColor(CHARCOAL);
        cell.setBorderColor(CHARCOAL);
        cell.setPadding(5);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private void cell(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(valueOr(value, "No registra"),
                font(7.1f, Font.NORMAL, CHARCOAL)));
        cell.setBorderColor(BORDER);
        cell.setPadding(4.5f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private void statusCell(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(humanEnum(value),
                font(7.1f, Font.BOLD, statusColor(value))));
        cell.setBorderColor(BORDER);
        cell.setPadding(4.5f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private Font font(float size, int style, BaseColor color) {
        return new Font(Font.FontFamily.HELVETICA, size, style, color);
    }

    private record MaterialKey(String product, String unit) {
    }

    private static final class MaterialLine {
        private final String code;
        private final String name;
        private final String unit;
        private BigDecimal planned = BigDecimal.ZERO;
        private BigDecimal actual = BigDecimal.ZERO;
        private boolean plannedPresent;
        private final Set<String> lots = new LinkedHashSet<>();

        private MaterialLine(String code, String name, String unit) {
            this.code = code;
            this.name = name;
            this.unit = unit;
        }
    }

    private record ControlDeviation(String controlName, JsonNode deviation) {
    }

    private final class MainPageHeaderEvent extends PdfPageEventHelper {
        private final byte[] logoBytes;
        private final String code;
        private final String revision;

        private MainPageHeaderEvent(byte[] logoBytes, String code, String revision) {
            this.logoBytes = logoBytes;
            this.code = code;
            this.revision = revision;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (writer.getPageNumber() == 1) return;
            Rectangle page = document.getPageSize();
            PdfContentByte canvas = writer.getDirectContent();
            try {
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(42, 34);
                logo.setAbsolutePosition(document.left(), page.getTop() - 52);
                canvas.addImage(logo);
            } catch (Exception ignored) {
                // La evidencia de marca sigue identificada en el control documental.
            }
            try {
                ColumnText.showTextAligned(
                        canvas,
                        Element.ALIGN_LEFT,
                        new Phrase("EXPEDIENTE DIGITAL DE FABRICACIÓN", font(8.2f, Font.BOLD, CHARCOAL)),
                        document.left() + 50,
                        page.getTop() - 28,
                        0);
                ColumnText.showTextAligned(
                        canvas,
                        Element.ALIGN_RIGHT,
                        new Phrase(code + " · " + humanEnum(revision), font(7, Font.NORMAL, MUTED)),
                        document.right(),
                        page.getTop() - 28,
                        0);
                canvas.setColorStroke(GOLD);
                canvas.setLineWidth(1.1f);
                canvas.moveTo(document.left(), page.getTop() - 57);
                canvas.lineTo(document.right(), page.getTop() - 57);
                canvas.stroke();
            } catch (Exception ignored) {
                // El contenido documental no se invalida si falla el adorno del encabezado.
            }
        }
    }
}
