package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.CargaPuntosReordenDTOs;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.service.productos.PuntoReordenPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CargaMasivaPuntosReordenService {

    private static final String DATA_SHEET = "Puntos de reorden";
    private static final String INSTRUCTIONS_SHEET = "Instrucciones";
    private static final String[] HEADERS = {
            "codigo", "nombre", "punto_reorden_actual", "nuevo_punto_reorden"
    };
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final String SHEET_PROTECTION_PASSWORD = "exotic-puntos-reorden";

    private final MaterialRepo materialRepo;
    private final ProductoRepo productoRepo;

    @Transactional(readOnly = true)
    public byte[] generateTemplateExcel() {
        List<Material> materials = materialRepo.findByInventareableTrueOrderByProductoIdAsc();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet dataSheet = workbook.createSheet(DATA_SHEET);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle lockedTextStyle = createLockedTextStyle(workbook);
            CellStyle lockedNumberStyle = createLockedNumberStyle(workbook);
            CellStyle editableNumberStyle = createEditableNumberStyle(workbook);

            Row header = dataSheet.createRow(0);
            for (int column = 0; column < HEADERS.length; column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(HEADERS[column]);
                cell.setCellStyle(headerStyle);
            }

            for (int index = 0; index < materials.size(); index++) {
                Material material = materials.get(index);
                Row row = dataSheet.createRow(index + 1);

                Cell codigo = row.createCell(0);
                codigo.setCellValue(material.getProductoId());
                codigo.setCellStyle(lockedTextStyle);

                Cell nombre = row.createCell(1);
                nombre.setCellValue(material.getNombre() != null ? material.getNombre() : "");
                nombre.setCellStyle(lockedTextStyle);

                Cell currentValue = row.createCell(2);
                currentValue.setCellValue(material.getPuntoReorden());
                currentValue.setCellStyle(lockedNumberStyle);

                Cell newValue = row.createCell(3);
                newValue.setBlank();
                newValue.setCellStyle(editableNumberStyle);
            }

            dataSheet.createFreezePane(0, 1);
            dataSheet.setAutoFilter(new CellRangeAddress(0, materials.size(), 0, HEADERS.length - 1));
            dataSheet.setColumnWidth(0, 22 * 256);
            dataSheet.setColumnWidth(1, 48 * 256);
            dataSheet.setColumnWidth(2, 24 * 256);
            dataSheet.setColumnWidth(3, 24 * 256);
            addNewValueValidation(dataSheet, materials.size());
            dataSheet.protectSheet(SHEET_PROTECTION_PASSWORD);

            createInstructionsSheet(workbook, headerStyle);

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                workbook.write(output);
                return output.toByteArray();
            }
        } catch (IOException exception) {
            log.error("No se pudo generar la plantilla de puntos de reorden", exception);
            throw new IllegalStateException("No se pudo generar la plantilla de puntos de reorden", exception);
        }
    }

    @Transactional(readOnly = true)
    public CargaPuntosReordenDTOs.ValidationResponse validateExcel(MultipartFile file) {
        ParsedWorkbook parsed = parseWorkbook(file);
        Map<String, Producto> products = loadProducts(parsed.activeRows());
        return evaluate(parsed, products).response();
    }

    @Transactional
    public CargaPuntosReordenDTOs.ExecutionResponse execute(MultipartFile file) {
        ParsedWorkbook parsed = parseWorkbook(file);
        Map<String, Producto> lockedProducts = loadProductsForUpdate(parsed.activeRows());
        Evaluation evaluation = evaluate(parsed, lockedProducts);

        if (!evaluation.response().valid()) {
            throw new CargaPuntosReordenValidationException(
                    "El archivo contiene errores y no se aplicó ningún cambio.",
                    evaluation.response(),
                    evaluation.onlyConflicts());
        }

        if (evaluation.response().updateRows() == 0) {
            List<CargaPuntosReordenDTOs.ErrorFila> errors = List.of(
                    new CargaPuntosReordenDTOs.ErrorFila(
                            0,
                            "",
                            "nuevo_punto_reorden",
                            "El archivo no contiene cambios aplicables.")
            );
            CargaPuntosReordenDTOs.ValidationResponse response =
                    new CargaPuntosReordenDTOs.ValidationResponse(
                            false,
                            evaluation.response().totalRows(),
                            evaluation.response().ignoredRows(),
                            evaluation.response().unchangedRows(),
                            0,
                            0,
                            List.of(),
                            errors);
            throw new CargaPuntosReordenValidationException(
                    "El archivo no contiene cambios aplicables.",
                    response,
                    false);
        }

        List<Material> updatedMaterials = new ArrayList<>(evaluation.response().updateRows());
        for (CargaPuntosReordenDTOs.CambioPreview change : evaluation.response().changes()) {
            Producto product = lockedProducts.get(change.productoId());
            if (!(product instanceof Material material)) {
                throw new IllegalStateException(
                        "El material " + change.productoId() + " dejó de estar disponible durante la ejecución.");
            }
            material.setPuntoReorden(change.newValue());
            updatedMaterials.add(material);
        }
        materialRepo.saveAllAndFlush(updatedMaterials);

        log.info(
                "Carga masiva de puntos de reorden completada. totalFilas={}, ignoradas={}, sinCambio={}, actualizadas={}",
                evaluation.response().totalRows(),
                evaluation.response().ignoredRows(),
                evaluation.response().unchangedRows(),
                updatedMaterials.size());

        return new CargaPuntosReordenDTOs.ExecutionResponse(
                true,
                evaluation.response().totalRows(),
                evaluation.response().ignoredRows(),
                evaluation.response().unchangedRows(),
                updatedMaterials.size());
    }

    private ParsedWorkbook parseWorkbook(MultipartFile file) {
        List<CargaPuntosReordenDTOs.ErrorFila> errors = new ArrayList<>();
        if (file == null || file.isEmpty()) {
            errors.add(globalError("El archivo Excel es obligatorio."));
            return new ParsedWorkbook(0, 0, List.of(), errors);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            errors.add(globalError("Solo se permiten archivos Excel con extensión .xlsx."));
            return new ParsedWorkbook(0, 0, List.of(), errors);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            errors.add(globalError("El archivo supera el tamaño máximo permitido de 10 MB."));
            return new ParsedWorkbook(0, 0, List.of(), errors);
        }

        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(DATA_SHEET);
            if (sheet == null) {
                errors.add(globalError("No se encontró la hoja '" + DATA_SHEET + "'."));
                return new ParsedWorkbook(0, 0, List.of(), errors);
            }
            if (!validateHeaders(sheet, formatter, errors)) {
                return new ParsedWorkbook(0, 0, List.of(), errors);
            }

            int totalRows = 0;
            int ignoredRows = 0;
            List<ParsedRow> activeRows = new ArrayList<>();
            Set<String> seenActiveProductIds = new LinkedHashSet<>();

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                totalRows++;
                int excelRow = rowIndex + 1;
                Cell newValueCell = row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (isBlankCell(newValueCell, formatter)) {
                    ignoredRows++;
                    continue;
                }

                int initialErrorCount = errors.size();
                String productoId = formatter.formatCellValue(
                        row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).trim();
                if (productoId.isEmpty()) {
                    errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                            excelRow, "", "codigo", "El código es obligatorio."));
                } else if (!seenActiveProductIds.add(productoId)) {
                    errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                            excelRow, productoId, "codigo", "El código está duplicado dentro del archivo."));
                }

                validateNoExtraColumns(row, formatter, excelRow, productoId, errors);
                Double currentValue = readNumericCell(
                        row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL),
                        excelRow,
                        productoId,
                        "punto_reorden_actual",
                        errors);
                Double newValue = readNumericCell(
                        newValueCell,
                        excelRow,
                        productoId,
                        "nuevo_punto_reorden",
                        errors);

                if (currentValue != null && !PuntoReordenPolicy.isValid(currentValue)) {
                    errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                            excelRow,
                            productoId,
                            "punto_reorden_actual",
                            "El valor actual debe ser -1 o mayor o igual a 0."));
                }
                if (newValue != null && !PuntoReordenPolicy.isValid(newValue)) {
                    errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                            excelRow,
                            productoId,
                            "nuevo_punto_reorden",
                            "El nuevo valor debe ser -1 o mayor o igual a 0."));
                }

                if (errors.size() == initialErrorCount
                        && currentValue != null
                        && newValue != null) {
                    activeRows.add(new ParsedRow(excelRow, productoId, currentValue, newValue));
                }
            }

            return new ParsedWorkbook(totalRows, ignoredRows, activeRows, errors);
        } catch (Exception exception) {
            log.warn("No se pudo leer el Excel de puntos de reorden: {}", exception.getMessage());
            errors.add(globalError("El archivo no es un libro .xlsx válido o está corrupto."));
            return new ParsedWorkbook(0, 0, List.of(), errors);
        }
    }

    private Evaluation evaluate(ParsedWorkbook parsed, Map<String, Producto> products) {
        List<CargaPuntosReordenDTOs.ErrorFila> errors = new ArrayList<>(parsed.errors());
        List<CargaPuntosReordenDTOs.CambioPreview> changes = new ArrayList<>();
        int unchangedRows = 0;
        int conflictErrors = 0;

        for (ParsedRow row : parsed.activeRows()) {
            Producto product = products.get(row.productoId());
            if (product == null) {
                errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                        row.rowNumber(),
                        row.productoId(),
                        "codigo",
                        "No existe un producto con este código."));
                continue;
            }
            if (!(product instanceof Material material)) {
                errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                        row.rowNumber(),
                        row.productoId(),
                        "codigo",
                        "El código corresponde a un producto que no es material."));
                continue;
            }
            if (!material.isInventareable()) {
                errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                        row.rowNumber(),
                        row.productoId(),
                        "codigo",
                        "El material no es inventariable y no admite punto de reorden."));
                continue;
            }
            if (Double.compare(material.getPuntoReorden(), row.expectedCurrentValue()) != 0) {
                errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                        row.rowNumber(),
                        row.productoId(),
                        "punto_reorden_actual",
                        "El valor actual en el archivo es " + formatValue(row.expectedCurrentValue())
                                + ", pero en la base de datos ahora es "
                                + formatValue(material.getPuntoReorden())
                                + ". Descargue una plantilla nueva."));
                conflictErrors++;
                continue;
            }
            if (Double.compare(material.getPuntoReorden(), row.newValue()) == 0) {
                unchangedRows++;
                continue;
            }
            changes.add(new CargaPuntosReordenDTOs.CambioPreview(
                    row.rowNumber(),
                    row.productoId(),
                    material.getNombre() != null ? material.getNombre() : "",
                    material.getPuntoReorden(),
                    row.newValue()));
        }

        errors.sort(Comparator
                .comparingInt(CargaPuntosReordenDTOs.ErrorFila::rowNumber)
                .thenComparing(error -> Objects.toString(error.columnName(), "")));
        int errorRows = (int) errors.stream()
                .map(CargaPuntosReordenDTOs.ErrorFila::rowNumber)
                .filter(rowNumber -> rowNumber > 0)
                .distinct()
                .count();
        CargaPuntosReordenDTOs.ValidationResponse response =
                new CargaPuntosReordenDTOs.ValidationResponse(
                        errors.isEmpty(),
                        parsed.totalRows(),
                        parsed.ignoredRows(),
                        unchangedRows,
                        changes.size(),
                        errorRows,
                        List.copyOf(changes),
                        List.copyOf(errors));
        boolean onlyConflicts = !errors.isEmpty() && conflictErrors == errors.size();
        return new Evaluation(response, onlyConflicts);
    }

    private Map<String, Producto> loadProducts(Collection<ParsedRow> rows) {
        Set<String> ids = rows.stream()
                .map(ParsedRow::productoId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Producto> products = new LinkedHashMap<>();
        productoRepo.findAllById(ids).forEach(product -> products.put(product.getProductoId(), product));
        return products;
    }

    private Map<String, Producto> loadProductsForUpdate(Collection<ParsedRow> rows) {
        Set<String> ids = rows.stream()
                .map(ParsedRow::productoId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<String, Producto> products = new LinkedHashMap<>();
        materialRepo.findAllByProductoIdInForUpdate(ids)
                .forEach(material -> products.put(material.getProductoId(), material));

        Set<String> unresolvedIds = new LinkedHashSet<>(ids);
        unresolvedIds.removeAll(products.keySet());
        if (!unresolvedIds.isEmpty()) {
            productoRepo.findAllById(unresolvedIds)
                    .forEach(product -> products.put(product.getProductoId(), product));
        }
        return products;
    }

    private boolean validateHeaders(
            Sheet sheet,
            DataFormatter formatter,
            List<CargaPuntosReordenDTOs.ErrorFila> errors
    ) {
        Row header = sheet.getRow(0);
        if (header == null) {
            errors.add(globalError("No se encontró la fila de encabezados."));
            return false;
        }
        for (int column = 0; column < HEADERS.length; column++) {
            String actual = formatter.formatCellValue(
                    header.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).trim();
            if (!HEADERS[column].equals(actual)) {
                errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                        1,
                        "",
                        HEADERS[column],
                        "La columna " + (column + 1) + " debe llamarse '" + HEADERS[column] + "'."));
            }
        }
        for (int column = HEADERS.length; column < Math.max(header.getLastCellNum(), HEADERS.length); column++) {
            String value = formatter.formatCellValue(
                    header.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).trim();
            if (!value.isEmpty()) {
                errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                        1,
                        "",
                        "estructura",
                        "La plantilla solo admite las cuatro columnas definidas."));
                break;
            }
        }
        return errors.isEmpty();
    }

    private void validateNoExtraColumns(
            Row row,
            DataFormatter formatter,
            int excelRow,
            String productoId,
            List<CargaPuntosReordenDTOs.ErrorFila> errors
    ) {
        for (int column = HEADERS.length; column < Math.max(row.getLastCellNum(), HEADERS.length); column++) {
            String value = formatter.formatCellValue(
                    row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).trim();
            if (!value.isEmpty()) {
                errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                        excelRow,
                        productoId,
                        "estructura",
                        "La fila contiene datos en columnas no permitidas."));
                return;
            }
        }
    }

    private Double readNumericCell(
            Cell cell,
            int rowNumber,
            String productoId,
            String columnName,
            List<CargaPuntosReordenDTOs.ErrorFila> errors
    ) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                    rowNumber, productoId, columnName, "El valor es obligatorio."));
            return null;
        }
        if (cell.getCellType() == CellType.FORMULA) {
            errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                    rowNumber, productoId, columnName, "No se permiten fórmulas."));
            return null;
        }
        if (cell.getCellType() != CellType.NUMERIC || DateUtil.isCellDateFormatted(cell)) {
            errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                    rowNumber, productoId, columnName, "El valor debe ser una celda numérica."));
            return null;
        }
        double value = cell.getNumericCellValue();
        if (!Double.isFinite(value)) {
            errors.add(new CargaPuntosReordenDTOs.ErrorFila(
                    rowNumber, productoId, columnName, "El valor debe ser un número finito."));
            return null;
        }
        return value;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int column = 0; column < Math.max(row.getLastCellNum(), HEADERS.length); column++) {
            Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (!isBlankCell(cell, formatter)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlankCell(Cell cell, DataFormatter formatter) {
        return cell == null
                || cell.getCellType() == CellType.BLANK
                || (cell.getCellType() == CellType.STRING && formatter.formatCellValue(cell).trim().isEmpty());
    }

    private void addNewValueValidation(Sheet sheet, int materialCount) {
        if (materialCount <= 0) {
            return;
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createCustomConstraint(
                "OR(D2=\"\",D2=-1,D2>=0)");
        CellRangeAddressList range = new CellRangeAddressList(1, materialCount, 3, 3);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.createErrorBox(
                "Valor no permitido",
                "Use una celda vacía, -1, 0 o un número positivo.");
        sheet.addValidationData(validation);
    }

    private void createInstructionsSheet(Workbook workbook, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(INSTRUCTIONS_SHEET);
        Row title = sheet.createRow(0);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Actualización masiva de puntos de reorden");
        titleCell.setCellStyle(headerStyle);

        String[] instructions = {
                "Edite únicamente la columna nuevo_punto_reorden de la hoja 'Puntos de reorden'.",
                "Celda vacía: no modifica el material.",
                "-1: excluye el material de las alertas de punto de reorden.",
                "0: deja el material sin un umbral definido.",
                "Valor mayor que 0: activa el umbral de alerta.",
                "No use fórmulas ni agregue columnas.",
                "Si el valor actual cambió después de descargar la plantilla, descargue una nueva."
        };
        for (int index = 0; index < instructions.length; index++) {
            sheet.createRow(index + 2).createCell(0).setCellValue(instructions[index]);
        }
        sheet.setColumnWidth(0, 105 * 256);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setLocked(true);
        return style;
    }

    private CellStyle createLockedTextStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setLocked(true);
        return style;
    }

    private CellStyle createLockedNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setLocked(true);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.######"));
        return style;
    }

    private CellStyle createEditableNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setLocked(false);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.######"));
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CargaPuntosReordenDTOs.ErrorFila globalError(String message) {
        return new CargaPuntosReordenDTOs.ErrorFila(0, "", null, message);
    }

    private String formatValue(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private record ParsedRow(
            int rowNumber,
            String productoId,
            double expectedCurrentValue,
            double newValue
    ) {
    }

    private record ParsedWorkbook(
            int totalRows,
            int ignoredRows,
            List<ParsedRow> activeRows,
            List<CargaPuntosReordenDTOs.ErrorFila> errors
    ) {
    }

    private record Evaluation(
            CargaPuntosReordenDTOs.ValidationResponse response,
            boolean onlyConflicts
    ) {
    }
}
