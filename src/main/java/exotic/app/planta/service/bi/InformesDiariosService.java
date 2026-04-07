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

    /** Columnas comunes para informes de movimientos de almacén (materiales o terminados). */
    private static final String[] HEADERS_MOVIMIENTO_ALMACEN = {
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
        return generarExcelMovimientosAlmacen(movimientos, "Ingreso materiales", "ingreso materiales");
    }

    /**
     * Excel con dispensaciones de materiales (OD / OD_RA) en el día indicado.
     */
    public byte[] exportarDispensacionMaterialesExcel(LocalDate fecha) {
        LocalDateTime start = fecha.atStartOfDay();
        LocalDateTime end = fecha.atTime(LocalTime.MAX);
        List<Movimiento> movimientos = transaccionAlmacenRepo.findDispensacionesMaterialPorDia(
                start, end, Movimiento.TipoMovimiento.DISPENSACION);
        return generarExcelMovimientosAlmacen(movimientos, "Dispensación materiales", "dispensación materiales");
    }

    /**
     * Excel con ingresos de producto terminado (BACKFLUSH, típicamente cierre de OP) en el día indicado.
     */
    public byte[] exportarIngresoTerminadosExcel(LocalDate fecha) {
        LocalDateTime start = fecha.atStartOfDay();
        LocalDateTime end = fecha.atTime(LocalTime.MAX);
        List<Movimiento> movimientos = transaccionAlmacenRepo.findIngresosTerminadoPorDia(
                start, end, Movimiento.TipoMovimiento.BACKFLUSH);
        return generarExcelMovimientosAlmacen(movimientos, "Ingreso producto terminado", "ingreso producto terminado");
    }

    /**
     * Excel con movimientos de ajuste de almacén ({@link Movimiento.TipoMovimiento#AJUSTE_POSITIVO} /
     * {@link Movimiento.TipoMovimiento#AJUSTE_NEGATIVO}) en el rango de fechas inclusive.
     */
    public byte[] exportarAjustesAlmacenExcel(
            LocalDate fechaDesde, LocalDate fechaHasta, SentidoAjusteInforme sentido) {
        if (fechaDesde == null || fechaHasta == null) {
            throw new IllegalArgumentException("fechaDesde y fechaHasta son obligatorias");
        }
        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException("fechaDesde no puede ser posterior a fechaHasta");
        }
        if (sentido == null) {
            throw new IllegalArgumentException("sentido es obligatorio");
        }

        LocalDateTime start = fechaDesde.atStartOfDay();
        LocalDateTime end = fechaHasta.atTime(LocalTime.MAX);

        return switch (sentido) {
            case ENTRADAS -> generarExcelMovimientosAlmacen(
                    transaccionAlmacenRepo.findAjustesAlmacenEntradasPorRango(
                            start, end, Movimiento.TipoMovimiento.AJUSTE_POSITIVO),
                    "Ajustes almacén entradas",
                    "ajustes almacén entradas");
            case SALIDAS -> generarExcelMovimientosAlmacen(
                    transaccionAlmacenRepo.findAjustesAlmacenSalidasPorRango(
                            start, end, Movimiento.TipoMovimiento.AJUSTE_NEGATIVO),
                    "Ajustes almacén salidas",
                    "ajustes almacén salidas");
            case MIXTA -> generarExcelMovimientosAlmacen(
                    transaccionAlmacenRepo.findAjustesAlmacenMixtaPorRango(
                            start,
                            end,
                            Movimiento.TipoMovimiento.AJUSTE_POSITIVO,
                            Movimiento.TipoMovimiento.AJUSTE_NEGATIVO),
                    "Ajustes almacén mixto",
                    "ajustes almacén mixto");
        };
    }

    private byte[] generarExcelMovimientosAlmacen(
            List<Movimiento> movimientos, String nombreHoja, String contextoLog) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(nombreHoja);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS_MOVIMIENTO_ALMACEN.length; i++) {
                headerRow.createCell(i).setCellValue(HEADERS_MOVIMIENTO_ALMACEN[i]);
            }

            int rowIdx = 1;
            for (Movimiento mov : movimientos) {
                escribirFilaMovimiento(mov, sheet.createRow(rowIdx++));
            }

            for (int i = 0; i < HEADERS_MOVIMIENTO_ALMACEN.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Error generando Excel {}", contextoLog, e);
            throw new RuntimeException("Error generando Excel " + contextoLog, e);
        }
    }

    private static void escribirFilaMovimiento(Movimiento mov, Row row) {
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
}
