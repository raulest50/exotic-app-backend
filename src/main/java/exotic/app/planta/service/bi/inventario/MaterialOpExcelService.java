package exotic.app.planta.service.bi.inventario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MaterialOpExcelService {
    private static final int HEADER_ROW_INDEX = 3;
    private static final String[] DETAIL_HEADERS = {
            "OP",
            "LOTE",
            "ESTADO",
            "FECHA PRIMER MOVIMIENTO",
            "ORIGEN",
            "CÓDIGO",
            "MATERIAL",
            "UNIDAD",
            "CANTIDAD",
            "COSTO UNITARIO VIGENTE",
            "COSTO DISPONIBLE",
            "VALOR MATERIAL ESTIMADO"
    };

    private final PendientesInventarioAssembler pendingAssembler;
    private final Clock applicationClock;

    public ExcelExport exportDispensedMaterial() {
        return export(
                pendingAssembler.getAllDispensedMaterialSnapshot(),
                ExportKind.DISPENSADO);
    }

    public ExcelExport exportWipMaterial() {
        return export(
                pendingAssembler.getAllWipMaterialSnapshot(),
                ExportKind.WIP);
    }

    private ExcelExport export(
            PendientesInventarioAssembler.MaterialOpSnapshot snapshot,
            ExportKind kind
    ) {
        LocalDateTime cutoff = LocalDateTime.now(applicationClock);
        return new ExcelExport(generateWorkbook(snapshot, kind, cutoff), cutoff);
    }

    private byte[] generateWorkbook(
            PendientesInventarioAssembler.MaterialOpSnapshot snapshot,
            ExportKind kind,
            LocalDateTime cutoff
    ) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelStyles styles = createStyles(workbook);
            writeSummarySheet(
                    workbook.createSheet("Resumen por OP"),
                    snapshot.orders(),
                    kind,
                    cutoff,
                    styles);
            writeDetailSheet(
                    workbook.createSheet("Detalle materiales"),
                    snapshot.lines(),
                    kind,
                    cutoff,
                    styles);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            log.error("Error generando Excel de materiales asociados a OP", ex);
            throw new RuntimeException(
                    "Error generando Excel de materiales asociados a OP", ex);
        }
    }

    private void writeSummarySheet(
            Sheet sheet,
            List<PendientesInventarioAssembler.MaterialOpOrderSnapshot> orders,
            ExportKind kind,
            LocalDateTime cutoff,
            ExcelStyles styles
    ) {
        String[] headers = summaryHeaders(kind);
        writeMetadata(
                sheet,
                kind.title(),
                kind.notice(),
                cutoff,
                headers.length,
                styles);
        writeHeader(sheet, headers, styles);

        int rowIndex = HEADER_ROW_INDEX + 1;
        for (PendientesInventarioAssembler.MaterialOpOrderSnapshot order : orders) {
            Row row = sheet.createRow(rowIndex++);
            int column = 0;
            row.createCell(column++).setCellValue(order.opId());
            row.createCell(column++).setCellValue(valueOrDash(order.lote()));
            row.createCell(column++).setCellValue(productionOrderState(order.estado()));
            writeDateTimeCell(
                    row.createCell(column++),
                    order.fechaReferencia(),
                    styles);
            row.createCell(column++).setCellValue(order.referencias());
            row.createCell(column++).setCellValue(
                    formatQuantities(order.cantidadesPorUnidad()));
            writeNumberCell(
                    row.createCell(column),
                    order.valorEstimado(),
                    styles.currency());
        }

        finalizeSheet(
                sheet,
                rowIndex,
                headers.length,
                new int[] {12, 18, 34, 23, 15, 42, 26});
    }

    private String[] summaryHeaders(ExportKind kind) {
        return new String[] {
                "OP",
                "LOTE",
                "ESTADO",
                kind == ExportKind.WIP ? "INICIO WIP" : "FECHA DE REFERENCIA",
                "REFERENCIAS",
                "CANTIDADES POR UNIDAD",
                "VALOR MATERIAL ESTIMADO"
        };
    }

    private void writeDetailSheet(
            Sheet sheet,
            List<PendientesInventarioAssembler.MaterialOpLineSnapshot> lines,
            ExportKind kind,
            LocalDateTime cutoff,
            ExcelStyles styles
    ) {
        writeMetadata(
                sheet,
                kind.title() + " · detalle por material y origen",
                kind.notice(),
                cutoff,
                DETAIL_HEADERS.length,
                styles);
        writeHeader(sheet, DETAIL_HEADERS, styles);

        int rowIndex = HEADER_ROW_INDEX + 1;
        for (PendientesInventarioAssembler.MaterialOpLineSnapshot line : lines) {
            Row row = sheet.createRow(rowIndex++);
            int column = 0;
            row.createCell(column++).setCellValue(line.opId());
            row.createCell(column++).setCellValue(valueOrDash(line.lote()));
            row.createCell(column++).setCellValue(productionOrderState(line.estado()));
            writeDateTimeCell(
                    row.createCell(column++),
                    line.fechaPrimerMovimiento(),
                    styles);
            row.createCell(column++).setCellValue(line.origen().label());
            row.createCell(column++).setCellValue(line.productoId());
            row.createCell(column++).setCellValue(line.productoNombre());
            row.createCell(column++).setCellValue(line.unidadMedida());
            writeNumberCell(
                    row.createCell(column++),
                    line.cantidad(),
                    styles.quantity());
            writeNumberCell(
                    row.createCell(column++),
                    line.costoUnitario(),
                    styles.currency());
            row.createCell(column++).setCellValue(
                    line.costoDisponible() ? "Sí" : "No");
            writeNumberCell(
                    row.createCell(column),
                    line.valorEstimado(),
                    styles.currency());
        }

        finalizeSheet(
                sheet,
                rowIndex,
                DETAIL_HEADERS.length,
                new int[] {
                        12, 18, 34, 23, 24, 18, 42, 12, 18, 24, 19, 26
                });
    }

    private void writeMetadata(
            Sheet sheet,
            String title,
            String notice,
            LocalDateTime cutoff,
            int columns,
            ExcelStyles styles
    ) {
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(styles.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columns - 1));

        Row noticeRow = sheet.createRow(1);
        Cell noticeCell = noticeRow.createCell(0);
        noticeCell.setCellValue(notice);
        noticeCell.setCellStyle(styles.notice());
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, columns - 1));

        Row cutoffRow = sheet.createRow(2);
        Cell cutoffLabel = cutoffRow.createCell(0);
        cutoffLabel.setCellValue("Fecha y hora de corte (hora Colombia)");
        cutoffLabel.setCellStyle(styles.metadataLabel());
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 2));
        Cell cutoffValue = cutoffRow.createCell(3);
        cutoffValue.setCellValue(cutoff);
        cutoffValue.setCellStyle(styles.dateTime());
    }

    private void writeHeader(
            Sheet sheet,
            String[] headers,
            ExcelStyles styles
    ) {
        Row header = sheet.createRow(HEADER_ROW_INDEX);
        header.setHeightInPoints(32);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(styles.header());
        }
    }

    private void finalizeSheet(
            Sheet sheet,
            int nextRowIndex,
            int columns,
            int[] widths
    ) {
        int lastRow = Math.max(HEADER_ROW_INDEX, nextRowIndex - 1);
        sheet.setAutoFilter(new CellRangeAddress(
                HEADER_ROW_INDEX,
                lastRow,
                0,
                columns - 1));
        sheet.createFreezePane(0, HEADER_ROW_INDEX + 1);
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, widths[index] * 256);
        }
    }

    private void writeDateTimeCell(
            Cell cell,
            LocalDateTime value,
            ExcelStyles styles
    ) {
        if (value == null) {
            cell.setCellValue("—");
            return;
        }
        cell.setCellValue(value);
        cell.setCellStyle(styles.dateTime());
    }

    private void writeNumberCell(
            Cell cell,
            double value,
            CellStyle style
    ) {
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String formatQuantities(
            List<exotic.app.planta.model.bi.dto.InformeInventarioDTO.CantidadUnidadDTO>
                    quantities
    ) {
        if (quantities == null || quantities.isEmpty()) {
            return "Sin cantidades";
        }
        return quantities.stream()
                .map(quantity -> formatNumber(quantity.cantidad())
                        + " " + quantity.unidadMedida())
                .reduce((left, right) -> left + " · " + right)
                .orElse("Sin cantidades");
    }

    private String formatNumber(double value) {
        return BigDecimal.valueOf(value)
                .stripTrailingZeros()
                .toPlainString();
    }

    private String productionOrderState(int status) {
        if (status == 3) {
            return "Fabricación completada · pendiente de ingreso";
        }
        if (status == 0) {
            return "Abierta";
        }
        if (status >= 11) {
            return "Abierta con dispensaciones";
        }
        return "Estado " + status;
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private ExcelStyles createStyles(XSSFWorkbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);

        Font noticeFont = workbook.createFont();
        noticeFont.setItalic(true);
        noticeFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        CellStyle notice = workbook.createCellStyle();
        notice.setFont(noticeFont);

        Font metadataFont = workbook.createFont();
        metadataFont.setBold(true);
        CellStyle metadataLabel = workbook.createCellStyle();
        metadataLabel.setFont(metadataFont);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        CellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setWrapText(true);
        header.setBorderTop(BorderStyle.THIN);
        header.setBorderRight(BorderStyle.THIN);
        header.setBorderBottom(BorderStyle.THIN);
        header.setBorderLeft(BorderStyle.THIN);

        CellStyle quantity = workbook.createCellStyle();
        quantity.setDataFormat(
                workbook.createDataFormat().getFormat("#,##0.00##"));
        CellStyle currency = workbook.createCellStyle();
        currency.setDataFormat(
                workbook.createDataFormat().getFormat("#,##0.00"));
        CellStyle dateTime = workbook.createCellStyle();
        dateTime.setDataFormat(
                workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));

        return new ExcelStyles(
                title,
                notice,
                metadataLabel,
                header,
                quantity,
                currency,
                dateTime);
    }

    public record ExcelExport(
            byte[] content,
            LocalDateTime cutoff
    ) {
    }

    private enum ExportKind {
        DISPENSADO(
                "Material dispensado a OP abiertas",
                "Informe actualizado al momento de la descarga. "
                        + "Incluye dispensaciones normales y reposiciones por "
                        + "avería desde GENERAL."),
        WIP(
                "WIP material estimado",
                "Informe actualizado al momento de la descarga. "
                        + "Incluye material dispensado, reposiciones y consumos "
                        + "directos; no descuenta averías. Es un costo material "
                        + "bruto estimado, no WIP contable.");

        private final String title;
        private final String notice;

        ExportKind(String title, String notice) {
            this.title = title;
            this.notice = notice;
        }

        String title() {
            return title;
        }

        String notice() {
            return notice;
        }
    }

    private record ExcelStyles(
            CellStyle title,
            CellStyle notice,
            CellStyle metadataLabel,
            CellStyle header,
            CellStyle quantity,
            CellStyle currency,
            CellStyle dateTime
    ) {
    }
}
