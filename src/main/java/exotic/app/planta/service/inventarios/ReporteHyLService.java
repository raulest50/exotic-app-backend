package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.inventarios.dto.ReporteHyLRequestDTO;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.producto.TerminadoRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteHyLService {

    private static final String[] HYL_HEADERS = {
            "codigo", "nombre", "precio1", "precio2", "precio3", "precio4", "cantidad", "costo"
    };

    private final TerminadoRepo terminadoRepo;

    @Transactional(readOnly = true)
    public byte[] generarReporteXls(ReporteHyLRequestDTO request) {
        List<ReporteHyLRow> rows = consolidarReporte(request);
        Map<String, Terminado> terminadosById = cargarTerminados(rows);

        try (Workbook workbook = new HSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte HyL");
            escribirHeader(sheet);

            int rowIdx = 1;
            for (ReporteHyLRow row : rows) {
                double costo = request.isCostosEnCero()
                        ? 0d
                        : terminadosById.get(row.productoId()).getCosto().doubleValue();
                escribirFila(sheet.createRow(rowIdx++), row, costo);
            }

            for (int col = 0; col < HYL_HEADERS.length; col++) {
                sheet.autoSizeColumn(col);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Error generando reporte HyL XLS", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error generando reporte HyL: " + e.getMessage());
        }
    }

    private List<ReporteHyLRow> consolidarReporte(ReporteHyLRequestDTO request) {
        if (request == null || request.getIngresos() == null || request.getIngresos().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El reporte HyL requiere al menos un producto con producción positiva.");
        }

        Map<String, ReporteHyLRow> byProductoId = new LinkedHashMap<>();
        for (ReporteHyLRequestDTO.IngresoHyLItemDTO item : request.getIngresos()) {
            if (item == null || item.getCantidadProducida() <= 0) {
                continue;
            }
            String productoId = normalizeRequired(item.getProductoId(), "productoId");
            String productoNombre = normalizeRequired(item.getProductoNombre(), "productoNombre");
            byProductoId.compute(
                    productoId,
                    (id, current) -> current == null
                            ? new ReporteHyLRow(id, productoNombre, item.getCantidadProducida())
                            : current.addCantidad(item.getCantidadProducida()));
        }

        if (byProductoId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El reporte HyL requiere al menos un producto con producción positiva.");
        }
        return new ArrayList<>(byProductoId.values());
    }

    private Map<String, Terminado> cargarTerminados(Collection<ReporteHyLRow> rows) {
        Set<String> productoIds = rows.stream()
                .map(ReporteHyLRow::productoId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        Map<String, Terminado> terminadosById = terminadoRepo.findByProductoIdIn(productoIds)
                .stream()
                .collect(Collectors.toMap(Terminado::getProductoId, terminado -> terminado));

        List<String> missing = productoIds.stream()
                .filter(productoId -> !terminadosById.containsKey(productoId))
                .toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontró el producto terminado " + String.join(", ", missing) + " en el catálogo.");
        }
        return terminadosById;
    }

    private static void escribirHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        for (int col = 0; col < HYL_HEADERS.length; col++) {
            headerRow.createCell(col).setCellValue(HYL_HEADERS[col]);
        }
    }

    private static void escribirFila(Row excelRow, ReporteHyLRow row, double costo) {
        excelRow.createCell(0).setCellValue(row.productoId());
        excelRow.createCell(1).setCellValue(row.productoNombre());
        excelRow.createCell(2).setCellValue("");
        excelRow.createCell(3).setCellValue("");
        excelRow.createCell(4).setCellValue("");
        excelRow.createCell(5).setCellValue("");
        excelRow.createCell(6).setCellValue(row.cantidadProducida());
        excelRow.createCell(7).setCellValue(costo);
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El campo " + fieldName + " es obligatorio para el reporte HyL.");
        }
        return value.trim();
    }

    private record ReporteHyLRow(String productoId, String productoNombre, double cantidadProducida) {
        private ReporteHyLRow addCantidad(double cantidadAdicional) {
            return new ReporteHyLRow(productoId, productoNombre, cantidadProducida + cantidadAdicional);
        }
    }
}
