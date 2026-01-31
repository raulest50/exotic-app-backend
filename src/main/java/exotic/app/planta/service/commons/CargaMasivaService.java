package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.BulkUpdateResponseDTO;
import exotic.app.planta.model.commons.dto.ErrorRecord;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.AjusteInventarioDTO;
import exotic.app.planta.model.inventarios.dto.AjusteItemDTO;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.service.inventarios.MovimientosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class CargaMasivaService {

    private final MaterialRepo materialRepo;
    private final ProductoRepo productoRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final MovimientosService movimientosService;

    public byte[] generateTemplateExcel() {
        List<Material> materiales = materialRepo.findByInventareableTrue();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Carga masiva");

            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("productoid");
            headerRow.createCell(1).setCellValue("nombre");
            headerRow.createCell(2).setCellValue("costo");
            headerRow.createCell(3).setCellValue("cantidad_consolidada");
            headerRow.createCell(4).setCellValue("nuevo_valor_absoluto");
            headerRow.createCell(5).setCellValue("nuevo_costo");

            int rowIdx = 1;
            for (Material material : materiales) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(material.getProductoId() != null ? material.getProductoId() : "");
                row.createCell(1).setCellValue(material.getNombre() != null ? material.getNombre() : "");
                row.createCell(2).setCellValue(material.getCosto());
                Double cantidadConsolidada = transaccionAlmacenRepo.findTotalCantidadByProductoId(material.getProductoId());
                row.createCell(3).setCellValue(cantidadConsolidada != null ? cantidadConsolidada : 0);
                // nuevo_valor_absoluto vacío para completar
                row.createCell(4).setCellValue("");
                // nuevo_costo precargado con costo actual
                row.createCell(5).setCellValue(material.getCosto());
            }

            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error generating carga masiva template Excel", e);
        }
    }

    @Transactional
    public BulkUpdateResponseDTO processBulkUpdate(MultipartFile file, String username) {
        List<ErrorRecord> errors = new ArrayList<>();
        int successCount = 0;
        List<AjusteItemDTO> ajusteItems = new ArrayList<>();
        Set<String> updatedProductIds = new HashSet<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("Carga masiva");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            if (sheet == null || sheet.getLastRowNum() < 1) {
                throw new RuntimeException("El archivo Excel no contiene datos válidos");
            }

            for (int rowNum = 2; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                try {
                    String productoid = getCellValueAsString(row, 0);
                    if (productoid == null || productoid.trim().isEmpty()) {
                        continue;
                    }

                    productoid = productoid.trim();
                    Optional<Producto> productoOpt = productoRepo.findByProductoId(productoid);
                    if (productoOpt.isEmpty()) {
                        errors.add(new ErrorRecord(rowNum + 1, productoid, "Producto no encontrado"));
                        continue;
                    }

                    Producto producto = productoOpt.get();
                    if (!(producto instanceof Material)) {
                        errors.add(new ErrorRecord(rowNum + 1, productoid, "El producto no es un Material"));
                        continue;
                    }

                    Material material = (Material) producto;
                    if (!material.isInventareable()) {
                        errors.add(new ErrorRecord(rowNum + 1, productoid, "El material no es inventariable"));
                        continue;
                    }

                    double nuevoValorAbsoluto = getCellValueAsDouble(row, 4);
                    double nuevoCosto = getCellValueAsDouble(row, 5);
                    double costoActual = material.getCosto();

                    if (nuevoValorAbsoluto == -1.0 || nuevoValorAbsoluto == -7.0) {
                        continue;
                    }

                    Double actualConsolidado = transaccionAlmacenRepo.findTotalCantidadByProductoId(productoid);
                    double actual = actualConsolidado != null ? actualConsolidado : 0.0;
                    double delta = nuevoValorAbsoluto - actual;

                    boolean hasChanges = false;

                    if (delta != 0) {
                        AjusteItemDTO ajusteItem = new AjusteItemDTO();
                        ajusteItem.setProductoId(productoid);
                        ajusteItem.setCantidad(delta);
                        ajusteItem.setAlmacen(Movimiento.Almacen.GENERAL);
                        ajusteItem.setMotivo("COMPRA");
                        ajusteItems.add(ajusteItem);
                        hasChanges = true;
                    }

                    if (nuevoCosto > 0 && nuevoCosto != costoActual) {
                        material.setCosto(nuevoCosto);
                        materialRepo.save(material);
                        updateCostoCascade(material, updatedProductIds);
                        hasChanges = true;
                    }

                    if (hasChanges) {
                        successCount++;
                    }
                } catch (Exception e) {
                    String productoid = getCellValueAsString(row, 0);
                    errors.add(new ErrorRecord(rowNum + 1, productoid != null ? productoid : "N/A", 
                        "Error procesando fila: " + e.getMessage()));
                    log.error("Error procesando fila {}: {}", rowNum + 1, e.getMessage(), e);
                }
            }

            if (!ajusteItems.isEmpty()) {
                AjusteInventarioDTO ajusteDTO = new AjusteInventarioDTO();
                ajusteDTO.setUsername(username);
                ajusteDTO.setObservaciones("Carga masiva de inventario");
                ajusteDTO.setItems(ajusteItems);
                movimientosService.createAjusteInventario(ajusteDTO, TransaccionAlmacen.TipoEntidadCausante.CM);
            }

            byte[] reportFile = generateReportExcel(sheet, errors, successCount);
            BulkUpdateResponseDTO response = new BulkUpdateResponseDTO();
            response.setSuccessCount(successCount);
            response.setFailureCount(errors.size());
            response.setErrors(errors);
            response.setReportFile(reportFile);
            response.setReportFileName("reporte_carga_masiva.xlsx");
            return response;

        } catch (IOException e) {
            log.error("Error procesando archivo Excel: {}", e.getMessage(), e);
            throw new RuntimeException("Error procesando archivo Excel: " + e.getMessage(), e);
        }
    }

    private String getCellValueAsString(Row row, int cellIndex) {
        if (row == null) return null;
        var cell = row.getCell(cellIndex);
        if (cell == null) return null;
        return cell.toString().trim();
    }

    private double getCellValueAsDouble(Row row, int cellIndex) {
        if (row == null) return 0;
        var cell = row.getCell(cellIndex);
        if (cell == null) return 0;
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return cell.getNumericCellValue();
            } else if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                String str = cell.getStringCellValue().trim();
                if (str.isEmpty()) return 0;
                return Double.parseDouble(str);
            }
        } catch (Exception e) {
            log.warn("Error parseando celda {} como número: {}", cellIndex, e.getMessage());
        }
        return 0;
    }

    private void updateCostoCascade(Material material, Set<String> updatedProductIds) {
        if (updatedProductIds.contains(material.getProductoId())) {
            return;
        }
        updatedProductIds.add(material.getProductoId());
        movimientosService.updateCostoCascade(material, updatedProductIds);
    }

    private byte[] generateReportExcel(Sheet originalSheet, List<ErrorRecord> errors, int successCount) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet reportSheet = workbook.createSheet("Reporte");

            Row headerRow = reportSheet.createRow(0);
            headerRow.createCell(0).setCellValue("Resumen");
            headerRow.createCell(1).setCellValue("Valor");
            Row summaryRow1 = reportSheet.createRow(1);
            summaryRow1.createCell(0).setCellValue("Materiales actualizados exitosamente");
            summaryRow1.createCell(1).setCellValue(successCount);
            Row summaryRow2 = reportSheet.createRow(2);
            summaryRow2.createCell(0).setCellValue("Errores encontrados");
            summaryRow2.createCell(1).setCellValue(errors.size());

            if (!errors.isEmpty()) {
                Row errorHeaderRow = reportSheet.createRow(4);
                errorHeaderRow.createCell(0).setCellValue("Fila");
                errorHeaderRow.createCell(1).setCellValue("Producto ID");
                errorHeaderRow.createCell(2).setCellValue("Mensaje de error");

                int rowIdx = 5;
                for (ErrorRecord error : errors) {
                    Row errorRow = reportSheet.createRow(rowIdx++);
                    errorRow.createCell(0).setCellValue(error.getRowNumber());
                    errorRow.createCell(1).setCellValue(error.getProductoId() != null ? error.getProductoId() : "");
                    errorRow.createCell(2).setCellValue(error.getMessage());
                }
            }

            for (int i = 0; i < 3; i++) {
                reportSheet.autoSizeColumn(i);
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            log.error("Error generando reporte Excel: {}", e.getMessage(), e);
            return new byte[0];
        }
    }
}
