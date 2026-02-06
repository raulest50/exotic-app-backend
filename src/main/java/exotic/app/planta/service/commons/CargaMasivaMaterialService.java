package exotic.app.planta.service.commons;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Servicio para carga masiva de Materiales (ROH).
 * Genera plantilla Excel vacía con columnas mínimas para registrar Material en BD.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CargaMasivaMaterialService {

    public byte[] generateTemplateExcel() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Carga masiva materiales");

            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("producto_id");
            headerRow.createCell(1).setCellValue("nombre");
            headerRow.createCell(2).setCellValue("observaciones");
            headerRow.createCell(3).setCellValue("costo");
            headerRow.createCell(4).setCellValue("iva_percentual");
            headerRow.createCell(5).setCellValue("tipo_unidades");
            headerRow.createCell(6).setCellValue("cantidad_unidad");
            headerRow.createCell(7).setCellValue("stock_minimo");
            headerRow.createCell(8).setCellValue("inventareable");
            headerRow.createCell(9).setCellValue("ficha_tecnica_url");
            headerRow.createCell(10).setCellValue("tipo_material");
            headerRow.createCell(11).setCellValue("punto_reorden");

            for (int i = 0; i < 12; i++) {
                sheet.autoSizeColumn(i);
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
}
