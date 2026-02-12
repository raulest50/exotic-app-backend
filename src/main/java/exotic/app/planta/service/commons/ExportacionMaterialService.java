package exotic.app.planta.service.commons;

import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.producto.MaterialRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Servicio para exportar datos de Materiales (ROH) a Excel.
 * La estructura del Excel es compatible con la plantilla de carga masiva de materiales.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportacionMaterialService {

    private final MaterialRepo materialRepo;

    private static final String[] TEMPLATE_HEADERS = {
            "producto_id", "nombre", "observaciones", "costo", "iva_percentual", "tipo_unidades",
            "cantidad_unidad", "stock_minimo", "ficha_tecnica_url", "tipo_material", "punto_reorden"
    };

    public byte[] exportarMaterialesExcel() {
        List<Material> materiales = materialRepo.findAll();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Carga masiva materiales");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(TEMPLATE_HEADERS[i]);
            }

            int rowIndex = 1;
            for (Material m : materiales) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(m.getProductoId() != null ? m.getProductoId() : "");
                row.createCell(1).setCellValue(m.getNombre() != null ? m.getNombre() : "");
                row.createCell(2).setCellValue(m.getObservaciones() != null ? m.getObservaciones() : "");
                row.createCell(3).setCellValue(m.getCosto());
                row.createCell(4).setCellValue(m.getIvaPercentual());
                row.createCell(5).setCellValue(m.getTipoUnidades() != null ? m.getTipoUnidades() : "U");
                row.createCell(6).setCellValue(m.getCantidadUnidad());
                row.createCell(7).setCellValue(m.getStockMinimo());
                row.createCell(8).setCellValue(m.getFichaTecnicaUrl() != null ? m.getFichaTecnicaUrl() : "");
                row.createCell(9).setCellValue(m.getTipoMaterial());
                row.createCell(10).setCellValue(m.getPuntoReorden());
            }

            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            log.error("Error generating exportacion materiales Excel", e);
            throw new RuntimeException("Error generating exportacion materiales Excel", e);
        }
    }
}
