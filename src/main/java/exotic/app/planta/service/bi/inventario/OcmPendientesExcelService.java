package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class OcmPendientesExcelService {
    private static final String[] EXCEL_HEADERS = {
            "OCM",
            "FECHA DE EMISIÓN",
            "PROVEEDOR",
            "ÍTEM",
            "CÓDIGO",
            "MATERIAL",
            "UNIDAD",
            "CANTIDAD ORDENADA",
            "RECIBIDO APLICADO",
            "CANTIDAD PENDIENTE",
            "PRECIO UNITARIO SIN IVA",
            "VALOR PENDIENTE SIN IVA"
    };
    private static final int EXCEL_HEADER_ROW_INDEX = 3;

    private final PendientesInventarioAssembler pendingAssembler;
    private final Clock applicationClock;

    public ExcelExport exportExcel() {
        LocalDateTime cutoff = LocalDateTime.now(applicationClock);
        List<InformeInventarioDTO.OcmDTO> orders =
                pendingAssembler.getAllPendingPurchaseOrders();
        return new ExcelExport(generateExcel(orders, cutoff), cutoff);
    }

    private byte[] generateExcel(
            List<InformeInventarioDTO.OcmDTO> orders,
            LocalDateTime cutoff
    ) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("OCM pendientes");
            ExcelStyles styles = createExcelStyles(workbook);

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Materiales pendientes de ingreso por OCM");
            titleCell.setCellStyle(styles.title());
            sheet.addMergedRegion(new CellRangeAddress(
                    0, 0, 0, EXCEL_HEADERS.length - 1));

            Row noticeRow = sheet.createRow(1);
            Cell noticeCell = noticeRow.createCell(0);
            noticeCell.setCellValue(
                    "Informe actualizado al momento de la descarga.");
            noticeCell.setCellStyle(styles.notice());
            sheet.addMergedRegion(new CellRangeAddress(
                    1, 1, 0, EXCEL_HEADERS.length - 1));

            Row cutoffRow = sheet.createRow(2);
            Cell cutoffLabelCell = cutoffRow.createCell(0);
            cutoffLabelCell.setCellValue(
                    "Fecha y hora de corte (hora Colombia)");
            cutoffLabelCell.setCellStyle(styles.metadataLabel());
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 2));
            Cell cutoffValueCell = cutoffRow.createCell(3);
            cutoffValueCell.setCellValue(cutoff);
            cutoffValueCell.setCellStyle(styles.dateTime());

            Row headerRow = sheet.createRow(EXCEL_HEADER_ROW_INDEX);
            headerRow.setHeightInPoints(32);
            for (int index = 0; index < EXCEL_HEADERS.length; index++) {
                Cell cell = headerRow.createCell(index);
                cell.setCellValue(EXCEL_HEADERS[index]);
                cell.setCellStyle(styles.header());
            }

            int rowIndex = EXCEL_HEADER_ROW_INDEX + 1;
            for (InformeInventarioDTO.OcmDTO order : orders) {
                for (InformeInventarioDTO.LineaOcmDTO line : order.lineas()) {
                    Row row = sheet.createRow(rowIndex++);
                    writeLineRow(row, order, line, styles);
                }
            }

            int lastRow = Math.max(EXCEL_HEADER_ROW_INDEX, rowIndex - 1);
            sheet.setAutoFilter(new CellRangeAddress(
                    EXCEL_HEADER_ROW_INDEX,
                    lastRow,
                    0,
                    EXCEL_HEADERS.length - 1));
            sheet.createFreezePane(0, EXCEL_HEADER_ROW_INDEX + 1);
            setExcelColumnWidths(sheet);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            log.error("Error generando Excel de OCM pendientes", ex);
            throw new RuntimeException(
                    "Error generando Excel de OCM pendientes", ex);
        }
    }

    private void writeLineRow(
            Row row,
            InformeInventarioDTO.OcmDTO order,
            InformeInventarioDTO.LineaOcmDTO line,
            ExcelStyles styles
    ) {
        int column = 0;
        row.createCell(column++).setCellValue(order.ocmId());

        Cell issueDateCell = row.createCell(column++);
        issueDateCell.setCellValue(order.fechaEmision());
        issueDateCell.setCellStyle(styles.dateTime());

        row.createCell(column++).setCellValue(order.proveedor());
        row.createCell(column++).setCellValue(line.itemId());
        row.createCell(column++).setCellValue(line.productoId());
        row.createCell(column++).setCellValue(line.productoNombre());
        row.createCell(column++).setCellValue(line.unidadMedida());
        writeNumberCell(
                row.createCell(column++),
                line.ordenado(),
                styles.quantity());
        writeNumberCell(
                row.createCell(column++),
                line.recibidoAplicado(),
                styles.quantity());
        writeNumberCell(
                row.createCell(column++),
                line.pendiente(),
                styles.quantity());
        writeNumberCell(
                row.createCell(column++),
                line.precioUnitarioSinIva(),
                styles.currency());
        writeNumberCell(
                row.createCell(column),
                line.valorPendienteSinIva(),
                styles.currency());
    }

    private void writeNumberCell(
            Cell cell,
            double value,
            CellStyle style
    ) {
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private ExcelStyles createExcelStyles(XSSFWorkbook workbook) {
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

    private void setExcelColumnWidths(Sheet sheet) {
        int[] widths = {
                12, 20, 34, 12, 18, 42, 12, 20, 20, 22, 24, 25
        };
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, widths[index] * 256);
        }
    }

    public record ExcelExport(
            byte[] content,
            LocalDateTime cutoff
    ) {
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
