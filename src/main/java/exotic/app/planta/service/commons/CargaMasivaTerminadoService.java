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
 * Servicio para carga masiva de Terminados.
 * Por ahora genera solo una plantilla placeholder para uso futuro.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CargaMasivaTerminadoService {

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
}
