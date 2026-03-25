package exotic.app.planta.service.bi;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InformesDiariosService {

    private static final EnumSet<Movimiento.TipoMovimiento> TIPOS_INGRESO_MATERIAL = EnumSet.of(
            Movimiento.TipoMovimiento.COMPRA,
            Movimiento.TipoMovimiento.AJUSTE_POSITIVO,
            Movimiento.TipoMovimiento.TRANSFERENCIA
    );

    private static final String[] HEADERS_INGRESO_MATERIALES = {
            "Fecha movimiento",
            "ID producto",
            "Nombre",
            "Cantidad",
            "Unidad",
            "Tipo movimiento",
            "Almacén",
            "ID transacción",
            "Tipo entidad causante",
            "ID entidad causante",
            "Observaciones",
            "Lote (batch)"
    };

    private final TransaccionAlmacenRepo transaccionAlmacenRepo;

    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    /**
     * Excel con movimientos de ingreso de materiales en el día indicado (zona horaria por defecto de la JVM).
     */
    public byte[] exportarIngresoMaterialesExcel(LocalDate fecha) {
        LocalDateTime start = fecha.atStartOfDay();
        LocalDateTime end = fecha.atTime(LocalTime.MAX);

        List<Movimiento> movimientos = transaccionAlmacenRepo.findIngresosMaterialPorDia(
                start, end, TIPOS_INGRESO_MATERIAL);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Ingreso materiales");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS_INGRESO_MATERIALES.length; i++) {
                headerRow.createCell(i).setCellValue(HEADERS_INGRESO_MATERIALES[i]);
            }

            int rowIdx = 1;
            for (Movimiento mov : movimientos) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                row.createCell(c++).setCellValue(
                        mov.getFechaMovimiento() != null ? mov.getFechaMovimiento().toString() : "");
                row.createCell(c++).setCellValue(
                        mov.getProducto() != null && mov.getProducto().getProductoId() != null
                                ? mov.getProducto().getProductoId() : "");
                row.createCell(c++).setCellValue(
                        mov.getProducto() != null && mov.getProducto().getNombre() != null
                                ? mov.getProducto().getNombre() : "");
                row.createCell(c++).setCellValue(mov.getCantidad());
                row.createCell(c++).setCellValue(
                        mov.getProducto() != null && mov.getProducto().getTipoUnidades() != null
                                ? mov.getProducto().getTipoUnidades() : "");
                row.createCell(c++).setCellValue(
                        mov.getTipoMovimiento() != null ? mov.getTipoMovimiento().name() : "");
                row.createCell(c++).setCellValue(
                        mov.getAlmacen() != null ? mov.getAlmacen().name() : "");

                TransaccionAlmacen tx = mov.getTransaccionAlmacen();
                if (tx != null) {
                    row.createCell(c++).setCellValue(tx.getTransaccionId());
                    row.createCell(c++).setCellValue(
                            tx.getTipoEntidadCausante() != null ? tx.getTipoEntidadCausante().name() : "");
                    row.createCell(c++).setCellValue(tx.getIdEntidadCausante());
                    row.createCell(c++).setCellValue(
                            tx.getObservaciones() != null ? tx.getObservaciones() : "");
                } else {
                    row.createCell(c++).setCellValue("");
                    row.createCell(c++).setCellValue("");
                    row.createCell(c++).setCellValue("");
                    row.createCell(c++).setCellValue("");
                }

                if (mov.getLote() != null && mov.getLote().getBatchNumber() != null) {
                    row.createCell(c).setCellValue(mov.getLote().getBatchNumber());
                } else {
                    row.createCell(c).setCellValue("");
                }
            }

            for (int i = 0; i < HEADERS_INGRESO_MATERIALES.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Error generando Excel ingreso materiales", e);
            throw new RuntimeException("Error generando Excel ingreso materiales", e);
        }
    }
}
