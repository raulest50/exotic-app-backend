package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.CargaCostosDTOs;
import exotic.app.planta.service.productos.ProductoCostoService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CargaCostosExcelParser {
    static final int MAX_CANDIDATOS = 5_000;
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final List<String> REQUIRED_HEADERS = List.of(
            "CODIGO", "DESCRIPCION", "NOMBRE PROVEEDOR", "VLR SIN IVA UNIT");

    private final ProductoCostoService productoCostoService;

    public ParsedWorkbook parse(MultipartFile file) {
        validateFile(file);
        List<CargaCostosDTOs.ErrorFila> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, ParsedRow> uniqueRows = new LinkedHashMap<>();
        int omitted = 0;
        int totalRows = 0;
        int duplicateRows = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw validation("El archivo no contiene hojas", List.of(), List.of());
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) {
                throw validation("El archivo no contiene filas de datos", List.of(), List.of());
            }

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Map<String, Integer> headers = readHeaders(sheet.getRow(0), formatter, errors);
            for (String required : REQUIRED_HEADERS) {
                if (!headers.containsKey(required)) {
                    errors.add(error(1, "", required, "Falta la columna obligatoria"));
                }
            }
            if (!errors.isEmpty()) {
                throw validation("La estructura del archivo no es valida", errors, warnings);
            }

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmpty(row, formatter)) continue;
                totalRows++;
                int excelRow = rowIndex + 1;

                Cell codeCell = row.getCell(headers.get("CODIGO"));
                Cell costCell = row.getCell(headers.get("VLR SIN IVA UNIT"));
                if (isFormula(codeCell)) {
                    errors.add(error(excelRow, "", "CODIGO", "No se permiten formulas"));
                    continue;
                }
                if (isFormula(costCell)) {
                    errors.add(error(excelRow, "", "VLR SIN IVA UNIT", "No se permiten formulas"));
                    continue;
                }

                String code = normalizeCode(formatter.formatCellValue(codeCell));
                String description = trimToLength(
                        formatter.formatCellValue(row.getCell(headers.get("DESCRIPCION"))), 500);
                String provider = normalizeText(
                        formatter.formatCellValue(row.getCell(headers.get("NOMBRE PROVEEDOR"))));

                BigDecimal cost;
                try {
                    cost = readNumericCost(costCell);
                } catch (IllegalArgumentException ex) {
                    errors.add(error(excelRow, code, "VLR SIN IVA UNIT", ex.getMessage()));
                    continue;
                }

                if ("PRODUCTO TERMINADO".equals(provider) && cost != null && cost.signum() == 0) {
                    omitted++;
                    continue;
                }
                if (code.isBlank()) {
                    errors.add(error(excelRow, "", "CODIGO", "El codigo esta vacio"));
                    continue;
                }
                if (cost == null) {
                    errors.add(error(excelRow, code, "VLR SIN IVA UNIT", "El costo es obligatorio"));
                    continue;
                }
                if (cost.signum() <= 0) {
                    errors.add(error(excelRow, code, "VLR SIN IVA UNIT", "El costo debe ser mayor que cero"));
                    continue;
                }

                BigDecimal normalizedCost;
                try {
                    normalizedCost = productoCostoService.normalizar(cost);
                } catch (IllegalArgumentException ex) {
                    errors.add(error(excelRow, code, "VLR SIN IVA UNIT", ex.getMessage()));
                    continue;
                }

                ParsedRow previous = uniqueRows.get(code);
                if (previous != null) {
                    if (previous.costo().compareTo(normalizedCost) != 0) {
                        errors.add(error(
                                excelRow,
                                code,
                                "CODIGO",
                                "El codigo esta duplicado con costos diferentes en las filas "
                                        + previous.fila() + " y " + excelRow));
                    } else {
                        duplicateRows++;
                    }
                    continue;
                }

                uniqueRows.put(code, new ParsedRow(excelRow, code, description, normalizedCost));
                if (uniqueRows.size() > MAX_CANDIDATOS) {
                    errors.add(error(excelRow, code, "CODIGO", "El archivo supera 5.000 materiales candidatos"));
                    break;
                }
            }
        } catch (CargaCostosValidationException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw validation(
                    "No fue posible leer el archivo Excel",
                    List.of(error(0, "", "file", safeMessage(ex))),
                    warnings);
        }

        if (omitted > 0) {
            warnings.add("Se omitieron " + omitted + " filas de PRODUCTO TERMINADO con costo cero.");
        }
        if (duplicateRows > 0) {
            warnings.add("Se consolidaron " + duplicateRows + " filas duplicadas con el mismo costo.");
        }
        if (uniqueRows.isEmpty() && errors.isEmpty()) {
            errors.add(error(0, "", "file", "No se encontraron materiales con costo mayor que cero"));
        }
        if (!errors.isEmpty()) {
            throw validation("El archivo contiene errores y no puede prepararse", errors, warnings);
        }
        return new ParsedWorkbook(List.copyOf(uniqueRows.values()), List.copyOf(warnings), totalRows, omitted);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo es obligatorio");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Solo se permiten archivos .xlsx");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("El archivo supera 10 MB");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] signature = input.readNBytes(4);
            if (signature.length < 4 || signature[0] != 'P' || signature[1] != 'K') {
                throw new IllegalArgumentException("El contenido no corresponde a un archivo .xlsx");
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("No fue posible leer el archivo", ex);
        }
    }

    private Map<String, Integer> readHeaders(
            Row header,
            DataFormatter formatter,
            List<CargaCostosDTOs.ErrorFila> errors
    ) {
        Map<String, Integer> indexes = new HashMap<>();
        if (header == null) return indexes;
        for (Cell cell : header) {
            String normalized = normalizeText(formatter.formatCellValue(cell));
            if (normalized.isBlank()) continue;
            Integer previous = indexes.putIfAbsent(normalized, cell.getColumnIndex());
            if (previous != null && REQUIRED_HEADERS.contains(normalized)) {
                errors.add(error(1, "", normalized, "La columna obligatoria esta repetida"));
            }
        }
        return indexes;
    }

    private BigDecimal readNumericCost(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() != CellType.NUMERIC) {
            throw new IllegalArgumentException("El costo debe ser una celda numerica, no texto");
        }
        double value = cell.getNumericCellValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("El costo no es finito");
        }
        return BigDecimal.valueOf(value);
    }

    private boolean isFormula(Cell cell) {
        return cell != null && cell.getCellType() == CellType.FORMULA;
    }

    private boolean isEmpty(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) return false;
        }
        return true;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), maxLength));
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Formato de archivo invalido"
                : ex.getMessage();
    }

    private CargaCostosDTOs.ErrorFila error(int row, String code, String field, String message) {
        return new CargaCostosDTOs.ErrorFila(row, code, field, message);
    }

    private CargaCostosValidationException validation(
            String message,
            List<CargaCostosDTOs.ErrorFila> errors,
            List<String> warnings
    ) {
        return new CargaCostosValidationException(message, errors, warnings);
    }

    public record ParsedRow(int fila, String productoId, String descripcion, BigDecimal costo) {}

    public record ParsedWorkbook(
            List<ParsedRow> filas,
            List<String> advertencias,
            int totalFilas,
            int totalOmitidas
    ) {}
}
