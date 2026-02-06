package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.ErrorRecord;
import exotic.app.planta.model.commons.dto.ValidationResultDTO;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Servicio para carga masiva de Materiales (ROH).
 * Genera plantilla Excel y valida/ejecuta carga masiva en tabla productos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CargaMasivaMaterialService {

    private final ProductoRepo productoRepo;
    private final MaterialRepo materialRepo;

    private static final String[] TEMPLATE_HEADERS = {
            "producto_id", "nombre", "observaciones", "costo", "iva_percentual", "tipo_unidades",
            "cantidad_unidad", "stock_minimo", "ficha_tecnica_url", "tipo_material", "punto_reorden"
    };

    public byte[] generateTemplateExcel() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Carga masiva materiales");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(TEMPLATE_HEADERS[i]);
            }
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            Sheet ejemplosSheet = workbook.createSheet("Ejemplos");
            Row ejemplosHeader = ejemplosSheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                ejemplosHeader.createCell(i).setCellValue(TEMPLATE_HEADERS[i]);
            }
            Row ejemplo1 = ejemplosSheet.createRow(1);
            ejemplo1.createCell(0).setCellValue("EJEMPLO_MP01");
            ejemplo1.createCell(1).setCellValue("Materia prima ejemplo");
            ejemplo1.createCell(2).setCellValue("");
            ejemplo1.createCell(3).setCellValue(100);
            ejemplo1.createCell(4).setCellValue(19);
            ejemplo1.createCell(5).setCellValue("KG");
            ejemplo1.createCell(6).setCellValue(1);
            ejemplo1.createCell(7).setCellValue(0);
            ejemplo1.createCell(8).setCellValue("");
            ejemplo1.createCell(9).setCellValue(1);
            ejemplo1.createCell(10).setCellValue(-1);
            Row ejemplo2 = ejemplosSheet.createRow(2);
            ejemplo2.createCell(0).setCellValue("EJEMPLO_EMP02");
            ejemplo2.createCell(1).setCellValue("Material de empaque ejemplo");
            ejemplo2.createCell(2).setCellValue("");
            ejemplo2.createCell(3).setCellValue(50);
            ejemplo2.createCell(4).setCellValue(5);
            ejemplo2.createCell(5).setCellValue("U");
            ejemplo2.createCell(6).setCellValue(1);
            ejemplo2.createCell(7).setCellValue(0);
            ejemplo2.createCell(8).setCellValue("");
            ejemplo2.createCell(9).setCellValue(2);
            ejemplo2.createCell(10).setCellValue(-1);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                ejemplosSheet.autoSizeColumn(i);
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            log.error("Error generating carga masiva materiales template Excel", e);
            throw new RuntimeException("Error generating carga masiva materiales template Excel", e);
        }
    }

    public ValidationResultDTO validateExcel(MultipartFile file) {
        List<ErrorRecord> errors = new ArrayList<>();
        int rowCount = 0;
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("Carga masiva materiales");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }
            if (sheet == null || sheet.getLastRowNum() < 1) {
                errors.add(new ErrorRecord(0, "", "El archivo no contiene la hoja 'Carga masiva materiales' o no tiene filas de datos"));
                return new ValidationResultDTO(false, errors, 0);
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                errors.add(new ErrorRecord(1, "", "Fila de encabezados no encontrada"));
                return new ValidationResultDTO(false, errors, 0);
            }
            for (int c = 0; c < TEMPLATE_HEADERS.length; c++) {
                String expected = TEMPLATE_HEADERS[c];
                String actual = getCellValueAsString(headerRow, c);
                if (actual == null || !actual.equalsIgnoreCase(expected)) {
                    errors.add(new ErrorRecord(1, "", "Columna " + (c + 1) + " esperada '" + expected + "', encontrada '" + (actual != null ? actual : "") + "'"));
                }
            }
            if (!errors.isEmpty()) {
                return new ValidationResultDTO(false, errors, 0);
            }
            Set<String> seenProductoIds = new HashSet<>();
            for (int rowNum = 2; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;
                String productoId = getCellValueAsString(row, 0);
                if (productoId == null || productoId.trim().isEmpty()) {
                    continue;
                }
                productoId = productoId.trim();
                rowCount++;

                if (seenProductoIds.contains(productoId)) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "producto_id duplicado dentro del archivo"));
                    continue;
                }
                seenProductoIds.add(productoId);

                String nombre = getCellValueAsString(row, 1);
                if (nombre == null || nombre.trim().isEmpty()) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "nombre es obligatorio"));
                }
                double costo = getCellValueAsDouble(row, 3);
                if (costo < 0) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "costo debe ser >= 0"));
                }
                double ivaPercentual = getCellValueAsDouble(row, 4);
                if (ivaPercentual != 0 && ivaPercentual != 5 && ivaPercentual != 19) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "iva_percentual debe ser 0, 5 o 19"));
                }
                String tipoUnidades = getCellValueAsString(row, 5);
                if (tipoUnidades == null || tipoUnidades.trim().isEmpty()) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "tipo_unidades es obligatorio"));
                } else {
                    String tu = tipoUnidades.trim().toUpperCase();
                    if (!tu.equals("L") && !tu.equals("KG") && !tu.equals("U")) {
                        errors.add(new ErrorRecord(rowNum + 1, productoId, "tipo_unidades debe ser L, KG o U"));
                    }
                }
                double cantidadUnidad = getCellValueAsDouble(row, 6);
                if (cantidadUnidad < 0) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "cantidad_unidad debe ser >= 0"));
                }
                double tipoMaterialVal = getCellValueAsDouble(row, 9);
                if (tipoMaterialVal != 1 && tipoMaterialVal != 2) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "tipo_material debe ser 1 (Materia Prima) o 2 (Material de Empaque)"));
                }
                double stockMinimo = getCellValueAsDouble(row, 7);
                if (stockMinimo < 0) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "stock_minimo debe ser >= 0"));
                }

                if (productoRepo.findByProductoId(productoId).isPresent()) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "producto_id ya existe en la base de datos"));
                }
            }
            return new ValidationResultDTO(errors.isEmpty(), errors, rowCount);
        } catch (IOException e) {
            log.error("Error validando Excel de carga masiva materiales", e);
            errors.add(new ErrorRecord(0, "", "Error leyendo archivo: " + e.getMessage()));
            return new ValidationResultDTO(false, errors, 0);
        }
    }

    @Transactional
    public ValidationResultDTO processBulkInsert(MultipartFile file) {
        ValidationResultDTO validation = validateExcel(file);
        if (!validation.isValid()) {
            return validation;
        }
        List<ErrorRecord> errors = new ArrayList<>();
        int successCount = 0;
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("Carga masiva materiales");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }
            for (int rowNum = 2; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;
                String productoId = getCellValueAsString(row, 0);
                if (productoId == null || productoId.trim().isEmpty()) continue;
                productoId = productoId.trim();

                try {
                    Material material = new Material();
                    material.setProductoId(productoId);
                    material.setNombre(getCellValueAsString(row, 1) != null ? getCellValueAsString(row, 1).trim() : "");
                    material.setObservaciones(getCellValueAsString(row, 2));
                    material.setCosto(getCellValueAsDouble(row, 3));
                    material.setIvaPercentual(getCellValueAsDouble(row, 4));
                    material.setTipoUnidades(getCellValueAsString(row, 5) != null ? getCellValueAsString(row, 5).trim().toUpperCase() : "U");
                    material.setCantidadUnidad(getCellValueAsDouble(row, 6));
                    material.setStockMinimo(getCellValueAsDouble(row, 7));
                    material.setFichaTecnicaUrl(getCellValueAsString(row, 8));
                    material.setTipoMaterial((int) getCellValueAsDouble(row, 9));
                    double puntoReorden = getCellValueAsDouble(row, 10);
                    material.setPuntoReorden(puntoReorden == 0 && getCellValueAsString(row, 10) == null ? -1 : puntoReorden);
                    material.setInventareable(true);

                    materialRepo.save(material);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Error guardando fila {} producto_id {}: {}", rowNum + 1, productoId, e.getMessage());
                    errors.add(new ErrorRecord(rowNum + 1, productoId, e.getMessage()));
                }
            }
            return new ValidationResultDTO(errors.isEmpty(), errors, successCount);
        } catch (IOException e) {
            log.error("Error procesando Excel de carga masiva materiales", e);
            errors.add(new ErrorRecord(0, "", "Error leyendo archivo: " + e.getMessage()));
            return new ValidationResultDTO(false, errors, successCount);
        }
    }

    private String getCellValueAsString(Row row, int cellIndex) {
        if (row == null) return null;
        var cell = row.getCell(cellIndex);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }
        return cell.toString().trim();
    }

    private double getCellValueAsDouble(Row row, int cellIndex) {
        if (row == null) return 0;
        var cell = row.getCell(cellIndex);
        if (cell == null) return 0;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return cell.getNumericCellValue();
            }
            if (cell.getCellType() == CellType.STRING) {
                String str = cell.getStringCellValue().trim();
                if (str.isEmpty()) return 0;
                return Double.parseDouble(str);
            }
        } catch (Exception e) {
            log.warn("Error parseando celda {} como número: {}", cellIndex, e.getMessage());
        }
        return 0;
    }
}
