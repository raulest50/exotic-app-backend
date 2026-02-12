package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.ErrorRecord;
import exotic.app.planta.model.commons.dto.ValidationResultDTO;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
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
import java.util.Optional;
import java.util.Set;

/**
 * Servicio para carga masiva de Terminados.
 * Soporta plantilla placeholder (template) y flujo "sin insumos" (template-sin-insumos, validar, ejecutar).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CargaMasivaTerminadoService {

    private final ProductoRepo productoRepo;
    private final TerminadoRepo terminadoRepo;
    private final CategoriaRepo categoriaRepo;

    private static final String[] SIN_INSUMOS_HEADERS = {
            "producto_id", "nombre", "observaciones", "costo", "iva_percentual", "tipo_unidades",
            "cantidad_unidad", "stock_minimo", "status", "categoria_id", "foto_url", "prefijo_lote"
    };

    public byte[] generateTemplateExcel() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Carga masiva terminados");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("Próximamente");
            sheet.autoSizeColumn(0);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            log.error("Error generating carga masiva terminados template Excel", e);
            throw new RuntimeException("Error generating carga masiva terminados template Excel", e);
        }
    }

    public byte[] generateTemplateExcelSinInsumos() {
        try (Workbook workbook = new XSSFWorkbook()) {
            List<Categoria> categorias = categoriaRepo.findAll();
            int primeraCategoriaId = categorias.isEmpty() ? 0 : categorias.get(0).getCategoriaId();

            Sheet valoresSheet = workbook.createSheet("Valores permitidos");

            Row headerValores = valoresSheet.createRow(0);
            headerValores.createCell(0).setCellValue("Columna");
            headerValores.createCell(1).setCellValue("Valores permitidos");

            int rowIdx = 1;
            valoresSheet.createRow(rowIdx++).createCell(0).setCellValue("producto_id");
            valoresSheet.getRow(rowIdx - 1).createCell(1).setCellValue("Texto único, obligatorio");
            valoresSheet.createRow(rowIdx++).createCell(0).setCellValue("nombre");
            valoresSheet.getRow(rowIdx - 1).createCell(1).setCellValue("Texto obligatorio");
            valoresSheet.createRow(rowIdx++).createCell(0).setCellValue("observaciones");
            valoresSheet.getRow(rowIdx - 1).createCell(1).setCellValue("Texto opcional");
            valoresSheet.createRow(rowIdx++).createCell(0).setCellValue("costo");
            valoresSheet.getRow(rowIdx - 1).createCell(1).setCellValue("Número >= 0");
            valoresSheet.createRow(rowIdx++).createCell(0).setCellValue("iva_percentual");
            valoresSheet.getRow(rowIdx - 1).createCell(1).setCellValue("0, 5, 19");
            valoresSheet.createRow(rowIdx++).createCell(0).setCellValue("tipo_unidades");
            valoresSheet.getRow(rowIdx - 1).createCell(1).setCellValue("L, KG, U");
            valoresSheet.createRow(rowIdx++).createCell(0).setCellValue("cantidad_unidad");
            valoresSheet.getRow(rowIdx - 1).createCell(1).setCellValue("Número >= 0");
            valoresSheet.createRow(rowIdx++).createCell(0).setCellValue("stock_minimo");
            valoresSheet.getRow(rowIdx - 1).createCell(1).setCellValue("Número >= 0");
            valoresSheet.createRow(rowIdx++).createCell(0).setCellValue("status");
            valoresSheet.getRow(rowIdx - 1).createCell(1).setCellValue("0 (activo), 1 (obsoleto)");
            Row catRow = valoresSheet.createRow(rowIdx++);
            catRow.createCell(0).setCellValue("categoria_id");
            StringBuilder catValores = new StringBuilder();
            if (categorias.isEmpty()) {
                catValores.append("Opcional (vacío o 0)");
            } else {
                for (int i = 0; i < categorias.size(); i++) {
                    if (i > 0) catValores.append("; ");
                    catValores.append(categorias.get(i).getCategoriaId())
                            .append(": ")
                            .append(categorias.get(i).getCategoriaNombre() != null ? categorias.get(i).getCategoriaNombre() : "");
                }
            }
            catRow.createCell(1).setCellValue(catValores.toString());
            valoresSheet.createRow(rowIdx++).createCell(0).setCellValue("foto_url");
            valoresSheet.getRow(rowIdx - 1).createCell(1).setCellValue("Texto opcional");
            valoresSheet.createRow(rowIdx++).createCell(0).setCellValue("prefijo_lote");
            valoresSheet.getRow(rowIdx - 1).createCell(1).setCellValue("Texto único opcional");

            rowIdx += 2;

            Row ejemplosHeader = valoresSheet.createRow(rowIdx++);
            for (int i = 0; i < SIN_INSUMOS_HEADERS.length; i++) {
                ejemplosHeader.createCell(i).setCellValue(SIN_INSUMOS_HEADERS[i]);
            }
            Object[][] ejemplos = {
                    {"TER_EJ01", "Terminado ejemplo 1", "", 150, 19, "U", 1, 0, 0, primeraCategoriaId > 0 ? primeraCategoriaId : "", "", "TRA"},
                    {"TER_EJ02", "Terminado ejemplo 2", "", 80, 5, "KG", 1, 0, 0, primeraCategoriaId > 0 ? primeraCategoriaId : "", "", ""},
                    {"TER_EJ03", "Terminado ejemplo 3 obsoleto", "", 50, 0, "L", 1, 0, 1, "", "", ""},
                    {"TER_EJ04", "Terminado con prefijo", "Observaciones ejemplo", 120, 19, "U", 1, 5, 0, "", "", "TRP"},
                    {"TER_EJ05", "Terminado sin categoría", "", 200, 19, "KG", 1, 0, 0, "", "", ""},
            };
            for (Object[] fila : ejemplos) {
                Row r = valoresSheet.createRow(rowIdx++);
                for (int c = 0; c < fila.length; c++) {
                    Object v = fila[c];
                    if (v instanceof Number) {
                        r.createCell(c).setCellValue(((Number) v).doubleValue());
                    } else {
                        r.createCell(c).setCellValue(v != null ? v.toString() : "");
                    }
                }
            }

            for (int i = 0; i < Math.max(2, SIN_INSUMOS_HEADERS.length); i++) {
                valoresSheet.autoSizeColumn(i);
            }

            Sheet datosSheet = workbook.createSheet("Datos");
            Row headerRow = datosSheet.createRow(0);
            for (int i = 0; i < SIN_INSUMOS_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(SIN_INSUMOS_HEADERS[i]);
            }
            for (int i = 0; i < SIN_INSUMOS_HEADERS.length; i++) {
                datosSheet.autoSizeColumn(i);
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            log.error("Error generating carga masiva terminados sin insumos template Excel", e);
            throw new RuntimeException("Error generating carga masiva terminados sin insumos template Excel", e);
        }
    }

    public ValidationResultDTO validateExcelSinInsumos(MultipartFile file) {
        List<ErrorRecord> errors = new ArrayList<>();
        int rowCount = 0;
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("Datos");
            if (sheet == null) {
                sheet = workbook.getSheetAt(workbook.getNumberOfSheets() > 1 ? 1 : 0);
            }
            log.debug("[CargaMasivaTerminados] validateExcelSinInsumos: hojaUsada={}, lastRowNum={}, totalHojas={}",
                    sheet != null ? sheet.getSheetName() : "null",
                    sheet != null ? sheet.getLastRowNum() : -1,
                    workbook.getNumberOfSheets());
            if (sheet == null || sheet.getLastRowNum() < 1) {
                errors.add(new ErrorRecord(0, "", "El archivo no contiene la hoja 'Datos' o no tiene filas de datos"));
                return new ValidationResultDTO(false, errors, 0);
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                errors.add(new ErrorRecord(1, "", "Fila de encabezados no encontrada"));
                return new ValidationResultDTO(false, errors, 0);
            }
            for (int c = 0; c < SIN_INSUMOS_HEADERS.length; c++) {
                String expected = SIN_INSUMOS_HEADERS[c];
                String actual = getCellValueAsString(headerRow, c);
                if (actual == null || !actual.equalsIgnoreCase(expected)) {
                    errors.add(new ErrorRecord(1, "", "Columna " + (c + 1) + " esperada '" + expected + "', encontrada '" + (actual != null ? actual : "") + "'", expected));
                }
            }
            if (!errors.isEmpty()) {
                return new ValidationResultDTO(false, errors, 0);
            }
            Set<String> seenProductoIds = new HashSet<>();
            Set<String> seenPrefijos = new HashSet<>();
            int lastRow = sheet.getLastRowNum();
            log.debug("[CargaMasivaTerminados] validateExcelSinInsumos: rangoBucle rowNum desde 1 hasta {} (lastRowNum={}), ¿bucleSeEjecutara?={}",
                    lastRow, lastRow, 1 <= lastRow);
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;
                String productoId = getCellValueAsString(row, 0);
                if (productoId == null || productoId.trim().isEmpty()) continue;
                productoId = productoId.trim();
                log.debug("[CargaMasivaTerminados] validateExcelSinInsumos: procesando fila rowNum={} (Excel fila {}), productoId={}",
                        rowNum, rowNum + 1, productoId);
                rowCount++;

                if (seenProductoIds.contains(productoId)) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "producto_id duplicado dentro del archivo", "producto_id"));
                    continue;
                }
                seenProductoIds.add(productoId);

                String nombre = getCellValueAsString(row, 1);
                if (nombre == null || nombre.trim().isEmpty()) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "nombre es obligatorio", "nombre"));
                }
                double costo = getCellValueAsDouble(row, 3);
                if (costo < 0) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "costo debe ser >= 0", "costo"));
                }
                double ivaPercentual = getCellValueAsDouble(row, 4);
                if (ivaPercentual != 0 && ivaPercentual != 5 && ivaPercentual != 19) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "iva_percentual debe ser 0, 5 o 19", "iva_percentual"));
                }
                String tipoUnidades = getCellValueAsString(row, 5);
                if (tipoUnidades == null || tipoUnidades.trim().isEmpty()) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "tipo_unidades es obligatorio", "tipo_unidades"));
                } else {
                    String tu = tipoUnidades.trim().toUpperCase();
                    if (!tu.equals("L") && !tu.equals("KG") && !tu.equals("U")) {
                        errors.add(new ErrorRecord(rowNum + 1, productoId, "tipo_unidades debe ser L, KG o U", "tipo_unidades"));
                    }
                }
                double cantidadUnidad = getCellValueAsDouble(row, 6);
                if (cantidadUnidad < 0) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "cantidad_unidad debe ser >= 0", "cantidad_unidad"));
                }
                double statusVal = getCellValueAsDouble(row, 8);
                if (statusVal != 0 && statusVal != 1) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "status debe ser 0 (activo) o 1 (obsoleto)", "status"));
                }
                double categoriaIdVal = getCellValueAsDouble(row, 9);
                if (categoriaIdVal > 0) {
                    int catId = (int) categoriaIdVal;
                    if (!categoriaRepo.existsById(catId)) {
                        errors.add(new ErrorRecord(rowNum + 1, productoId, "categoria_id no existe en la base de datos", "categoria_id"));
                    }
                }
                String prefijoLote = getCellValueAsString(row, 11);
                if (prefijoLote != null && !prefijoLote.trim().isEmpty()) {
                    prefijoLote = prefijoLote.trim();
                    if (seenPrefijos.contains(prefijoLote)) {
                        errors.add(new ErrorRecord(rowNum + 1, productoId, "prefijo_lote duplicado dentro del archivo", "prefijo_lote"));
                    } else {
                        seenPrefijos.add(prefijoLote);
                    }
                    Optional<Terminado> existing = terminadoRepo.findByPrefijoLote(prefijoLote);
                    if (existing.isPresent() && !existing.get().getProductoId().equals(productoId)) {
                        errors.add(new ErrorRecord(rowNum + 1, productoId, "prefijo_lote ya existe en la base de datos", "prefijo_lote"));
                    }
                }

                if (productoRepo.findByProductoId(productoId).isPresent()) {
                    errors.add(new ErrorRecord(rowNum + 1, productoId, "producto_id ya existe en la base de datos", "producto_id"));
                }
            }
            log.debug("[CargaMasivaTerminados] validateExcelSinInsumos: fin validacion, rowCount={}, errorsCount={}",
                    rowCount, errors.size());
            return new ValidationResultDTO(errors.isEmpty(), errors, rowCount);
        } catch (IOException e) {
            log.error("Error validando Excel de carga masiva terminados sin insumos", e);
            errors.add(new ErrorRecord(0, "", "Error leyendo archivo: " + e.getMessage()));
            return new ValidationResultDTO(false, errors, 0);
        }
    }

    @Transactional
    public ValidationResultDTO processBulkInsertSinInsumos(MultipartFile file) {
        ValidationResultDTO validation = validateExcelSinInsumos(file);
        log.debug("[CargaMasivaTerminados] processBulkInsertSinInsumos: validacion previa valid={}, rowCount={}",
                validation.isValid(), validation.getRowCount());
        if (!validation.isValid()) {
            return validation;
        }
        List<ErrorRecord> errors = new ArrayList<>();
        int successCount = 0;
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("Datos");
            if (sheet == null) {
                sheet = workbook.getSheetAt(workbook.getNumberOfSheets() > 1 ? 1 : 0);
            }
            int lastRow = sheet.getLastRowNum();
            log.debug("[CargaMasivaTerminados] processBulkInsertSinInsumos: hojaUsada={}, lastRowNum={}, rangoBucle rowNum 1..{}, ¿bucleSeEjecutara?={}",
                    sheet.getSheetName(), lastRow, lastRow, 1 <= lastRow);
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) {
                    log.debug("[CargaMasivaTerminados] processBulkInsertSinInsumos: fila {} (rowNum={}) es null, saltando", rowNum + 1, rowNum);
                    continue;
                }
                String productoId = getCellValueAsString(row, 0);
                if (productoId == null || productoId.trim().isEmpty()) {
                    log.debug("[CargaMasivaTerminados] processBulkInsertSinInsumos: fila {} (rowNum={}) producto_id vacio, saltando", rowNum + 1, rowNum);
                    continue;
                }
                productoId = productoId.trim();
                log.debug("[CargaMasivaTerminados] processBulkInsertSinInsumos: procesando fila {} (rowNum={}), productoId={}", rowNum + 1, rowNum, productoId);
                try {
                    Terminado terminado = new Terminado();
                    terminado.setProductoId(productoId);
                    terminado.setNombre(getCellValueAsString(row, 1) != null ? getCellValueAsString(row, 1).trim() : "");
                    terminado.setObservaciones(getCellValueAsString(row, 2));
                    terminado.setCosto(getCellValueAsDouble(row, 3));
                    terminado.setIvaPercentual(getCellValueAsDouble(row, 4));
                    terminado.setTipoUnidades(getCellValueAsString(row, 5) != null ? getCellValueAsString(row, 5).trim().toUpperCase() : "U");
                    terminado.setCantidadUnidad(getCellValueAsDouble(row, 6));
                    terminado.setStockMinimo(getCellValueAsDouble(row, 7));
                    terminado.setStatus((int) getCellValueAsDouble(row, 8));
                    double catIdVal = getCellValueAsDouble(row, 9);
                    if (catIdVal > 0) {
                        categoriaRepo.findById((int) catIdVal).ifPresent(terminado::setCategoria);
                    }
                    terminado.setFotoUrl(getCellValueAsString(row, 10));
                    String prefijoLote = getCellValueAsString(row, 11);
                    terminado.setPrefijoLote(prefijoLote != null && !prefijoLote.trim().isEmpty() ? prefijoLote.trim() : null);
                    terminado.setInsumos(new ArrayList<>());
                    terminado.setProcesoProduccionCompleto(null);
                    terminado.setCasePack(null);
                    terminado.setInventareable(true);
                    terminadoRepo.save(terminado);
                    successCount++;
                    log.debug("[CargaMasivaTerminados] processBulkInsertSinInsumos: fila {} productoId={} INSERTADO OK", rowNum + 1, productoId);
                } catch (Exception e) {
                    log.warn("[CargaMasivaTerminados] processBulkInsertSinInsumos: fila {} productoId={} ERROR: {}", rowNum + 1, productoId, e.getMessage());
                    errors.add(new ErrorRecord(rowNum + 1, productoId, e.getMessage()));
                }
            }
            log.debug("[CargaMasivaTerminados] processBulkInsertSinInsumos: fin, successCount={}, errorsCount={}",
                    successCount, errors.size());
            return new ValidationResultDTO(errors.isEmpty(), errors, successCount);
        } catch (IOException e) {
            log.error("Error procesando Excel de carga masiva terminados sin insumos", e);
            errors.add(new ErrorRecord(0, "", "Error leyendo archivo: " + e.getMessage()));
            return new ValidationResultDTO(false, errors, successCount);
        }
    }

    private String getCellValueAsString(Row row, int cellIndex) {
        if (row == null) return null;
        var cell = row.getCell(cellIndex);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) {
            String v = cell.getStringCellValue();
            if (v == null) {
                log.debug("[CargaMasivaTerminados] getCellValueAsString: row={}, cellIndex={}, getStringCellValue=null (usando vacio)",
                        row.getRowNum(), cellIndex);
                return "";
            }
            return v.trim();
        }
        String v = cell.toString();
        return (v == null) ? "" : v.trim();
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
                String str = cell.getStringCellValue();
                if (str == null || str.trim().isEmpty()) return 0;
                return Double.parseDouble(str.trim());
            }
        } catch (Exception e) {
            log.warn("Error parseando celda {} como número: {}", cellIndex, e.getMessage());
        }
        return 0;
    }
}
