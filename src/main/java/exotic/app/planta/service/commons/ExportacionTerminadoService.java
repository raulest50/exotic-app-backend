package exotic.app.planta.service.commons;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
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
 * Servicio para exportar datos de Terminados a Excel en formato "sin insumos".
 * La estructura del Excel es compatible con la carga masiva terminados sin insumos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportacionTerminadoService {

    private final TerminadoRepo terminadoRepo;
    private final CategoriaRepo categoriaRepo;

    private static final String[] SIN_INSUMOS_HEADERS = {
            "producto_id", "nombre", "observaciones", "costo", "iva_percentual", "tipo_unidades",
            "cantidad_unidad", "stock_minimo", "status", "categoria_id", "foto_url", "prefijo_lote"
    };

    public byte[] exportarTerminadosExcel() {
        List<Terminado> terminados = terminadoRepo.findAll();
        List<Categoria> categorias = categoriaRepo.findAll();

        try (Workbook workbook = new XSSFWorkbook()) {
            buildValoresPermitidosSheet(workbook, categorias);
            buildDatosSheet(workbook, terminados);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            log.error("Error generating exportacion terminados Excel", e);
            throw new RuntimeException("Error generating exportacion terminados Excel", e);
        }
    }

    private void buildValoresPermitidosSheet(Workbook workbook, List<Categoria> categorias) {
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

        for (int i = 0; i < Math.max(2, SIN_INSUMOS_HEADERS.length); i++) {
            valoresSheet.autoSizeColumn(i);
        }
    }

    private void buildDatosSheet(Workbook workbook, List<Terminado> terminados) {
        Sheet datosSheet = workbook.createSheet("Datos");
        Row headerRow = datosSheet.createRow(0);
        for (int i = 0; i < SIN_INSUMOS_HEADERS.length; i++) {
            headerRow.createCell(i).setCellValue(SIN_INSUMOS_HEADERS[i]);
        }

        int rowIndex = 1;
        for (Terminado t : terminados) {
            Row row = datosSheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(t.getProductoId() != null ? t.getProductoId() : "");
            row.createCell(1).setCellValue(t.getNombre() != null ? t.getNombre() : "");
            row.createCell(2).setCellValue(t.getObservaciones() != null ? t.getObservaciones() : "");
            row.createCell(3).setCellValue(t.getCosto());
            row.createCell(4).setCellValue(t.getIvaPercentual());
            row.createCell(5).setCellValue(t.getTipoUnidades() != null ? t.getTipoUnidades() : "U");
            row.createCell(6).setCellValue(t.getCantidadUnidad());
            row.createCell(7).setCellValue(t.getStockMinimo());
            row.createCell(8).setCellValue(t.getStatus());
            if (t.getCategoria() != null) {
                row.createCell(9).setCellValue(t.getCategoria().getCategoriaId());
            } else {
                row.createCell(9).setCellValue("");
            }
            row.createCell(10).setCellValue(t.getFotoUrl() != null ? t.getFotoUrl() : "");
            row.createCell(11).setCellValue(t.getPrefijoLote() != null ? t.getPrefijoLote() : "");
        }

        for (int i = 0; i < SIN_INSUMOS_HEADERS.length; i++) {
            datosSheet.autoSizeColumn(i);
        }
    }
}
