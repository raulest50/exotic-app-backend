package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.inventarios.dto.InventarioExcelRequestDTO;
import exotic.app.planta.model.inventarios.dto.KardexMovimientoRowDTO;
import exotic.app.planta.model.inventarios.dto.KardexMovimientosPageDTO;
import exotic.app.planta.model.inventarios.dto.KardexMovimientosRequestDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.dto.search.ProductoSearchCriteria;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.service.productos.ProductoService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventarioService {

    private final ProductoService productoService;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;

    public byte[] generateInventoryExcel(InventarioExcelRequestDTO dto) {
        Page<Producto> productos = productoService.consultaProductos(
                dto.getSearchTerm(),
                dto.getCategories(),
                0,
                Integer.MAX_VALUE
        );

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Inventario");

            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("ID");
            headerRow.createCell(1).setCellValue("Nombre");
            headerRow.createCell(2).setCellValue("Categoría");
            headerRow.createCell(3).setCellValue("Stock");

            int rowIdx = 1;
            for (Producto producto : productos.getContent()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(producto.getProductoId());
                row.createCell(1).setCellValue(producto.getNombre());
                row.createCell(2).setCellValue(getCategoria(producto));
                Double stock = transaccionAlmacenRepo.findTotalCantidadByProductoId(producto.getProductoId());
                row.createCell(3).setCellValue(stock != null ? stock : 0);
            }

            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error generating inventory Excel", e);
        }
    }

    private String getCategoria(Producto producto) {
        if (producto instanceof Material material) {
            if (material.getTipoMaterial() == 1) {
                return ProductoSearchCriteria.CATEGORIA_MATERIA_PRIMA;
            } else if (material.getTipoMaterial() == 2) {
                return ProductoSearchCriteria.CATEGORIA_MATERIAL_EMPAQUE;
            }
        } else if (producto instanceof SemiTerminado) {
            return ProductoSearchCriteria.CATEGORIA_SEMITERMINADO;
        } else if (producto instanceof Terminado) {
            return ProductoSearchCriteria.CATEGORIA_TERMINADO;
        }
        return "";
    }

    public KardexMovimientosPageDTO getKardexMovimientosPage(KardexMovimientosRequestDTO dto) {
        if (dto == null) throw new IllegalArgumentException("DTO requerido");
        if (dto.getProductoId() == null || dto.getProductoId().trim().isEmpty()) {
            throw new IllegalArgumentException("productoId requerido");
        }

        LocalDate startDate = dto.getStartDate();
        LocalDate endDate = dto.getEndDate();
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate y endDate son requeridas");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate no puede ser menor que startDate");
        }

        int page = dto.getPage() != null ? dto.getPage() : 0;
        int size = dto.getSize() != null ? dto.getSize() : 10;
        if (page < 0) page = 0;
        if (size <= 0) size = 10;

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        Pageable pageable = PageRequest.of(page, size);

        Producto producto = productoService.findProductoById(dto.getProductoId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + dto.getProductoId()));

        Double saldoInicialRaw = transaccionAlmacenRepo.findTotalCantidadByProductoIdAndFechaMovimientoBefore(
                dto.getProductoId(), startDateTime
        );
        double saldoInicial = saldoInicialRaw != null ? saldoInicialRaw : 0.0;

        Page<Movimiento> movPage = transaccionAlmacenRepo
                .findByProducto_ProductoIdAndFechaMovimientoBetweenOrderByFechaMovimientoAscMovimientoIdAsc(
                        dto.getProductoId(), startDateTime, endDateTime, pageable
                );

        double deltaAntesDePagina = 0.0;
        if (movPage.getNumber() > 0 && !movPage.getContent().isEmpty()) {
            Movimiento cursor = movPage.getContent().get(0);
            Double delta = transaccionAlmacenRepo.sumCantidadInRangeBeforeCursor(
                    dto.getProductoId(),
                    startDateTime,
                    endDateTime,
                    cursor.getFechaMovimiento(),
                    cursor.getMovimientoId()
            );
            deltaAntesDePagina = delta != null ? delta : 0.0;
        }

        double saldo = saldoInicial + deltaAntesDePagina;
        List<KardexMovimientoRowDTO> rows = new ArrayList<>();
        for (Movimiento mov : movPage.getContent()) {
            double cantidad = mov.getCantidad();
            double entrada = Math.max(0, cantidad);
            double salida = Math.max(0, -cantidad);
            saldo += cantidad;

            KardexMovimientoRowDTO row = new KardexMovimientoRowDTO();
            row.setMovimientoId(mov.getMovimientoId());
            row.setProductoId(mov.getProducto() != null ? mov.getProducto().getProductoId() : dto.getProductoId());
            row.setProductoNombre(mov.getProducto() != null ? mov.getProducto().getNombre() : "");
            row.setTipoUnidades(mov.getProducto() != null ? mov.getProducto().getTipoUnidades() : "");
            row.setCantidad(cantidad);
            row.setEntrada(entrada);
            row.setSalida(salida);

            if (mov.getLote() != null) {
                row.setBatchNumber(mov.getLote().getBatchNumber());
                row.setProductionDate(mov.getLote().getProductionDate());
                row.setExpirationDate(mov.getLote().getExpirationDate());
            }

            row.setTipoMovimiento(mov.getTipoMovimiento() != null ? mov.getTipoMovimiento().name() : "");
            row.setAlmacen(mov.getAlmacen() != null ? mov.getAlmacen().name() : "");
            row.setFechaMovimiento(mov.getFechaMovimiento());
            row.setSaldo(saldo);
            rows.add(row);
        }

        return new KardexMovimientosPageDTO(
                producto.getProductoId(),
                producto.getNombre(),
                producto.getTipoUnidades(),
                saldoInicial,
                rows,
                movPage.getNumber(),
                movPage.getSize(),
                movPage.getTotalElements(),
                movPage.getTotalPages()
        );
    }

    public byte[] exportKardexExcel(KardexMovimientosRequestDTO dto) {
        if (dto == null) throw new IllegalArgumentException("DTO requerido");
        if (dto.getProductoId() == null || dto.getProductoId().trim().isEmpty()) {
            throw new IllegalArgumentException("productoId requerido");
        }

        LocalDate startDate = dto.getStartDate();
        LocalDate endDate = dto.getEndDate();
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate y endDate son requeridas");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate no puede ser menor que startDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        Producto producto = productoService.findProductoById(dto.getProductoId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + dto.getProductoId()));

        Double saldoInicialRaw = transaccionAlmacenRepo.findTotalCantidadByProductoIdAndFechaMovimientoBefore(
                dto.getProductoId(), startDateTime
        );
        double saldoInicial = saldoInicialRaw != null ? saldoInicialRaw : 0.0;

        List<Movimiento> movimientos = transaccionAlmacenRepo
                .findByProducto_ProductoIdAndFechaMovimientoBetweenOrderByFechaMovimientoAscMovimientoIdAsc(
                        dto.getProductoId(), startDateTime, endDateTime
                );

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Kardex");

            int rowIdx = 0;
            Row meta1 = sheet.createRow(rowIdx++);
            meta1.createCell(0).setCellValue("Producto");
            meta1.createCell(1).setCellValue(producto.getProductoId() + " - " + producto.getNombre());

            Row meta2 = sheet.createRow(rowIdx++);
            meta2.createCell(0).setCellValue("Rango");
            meta2.createCell(1).setCellValue(startDate + " a " + endDate);

            Row meta3 = sheet.createRow(rowIdx++);
            meta3.createCell(0).setCellValue("Saldo inicial");
            meta3.createCell(1).setCellValue(saldoInicial);

            rowIdx++; // blank

            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.createCell(0).setCellValue("Fecha");
            headerRow.createCell(1).setCellValue("TipoMovimiento");
            headerRow.createCell(2).setCellValue("Almacen");
            headerRow.createCell(3).setCellValue("Lote");
            headerRow.createCell(4).setCellValue("Entrada");
            headerRow.createCell(5).setCellValue("Salida");
            headerRow.createCell(6).setCellValue("Saldo");

            double saldo = saldoInicial;
            for (Movimiento mov : movimientos) {
                double cantidad = mov.getCantidad();
                double entrada = Math.max(0, cantidad);
                double salida = Math.max(0, -cantidad);
                saldo += cantidad;

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(mov.getFechaMovimiento() != null ? mov.getFechaMovimiento().toString() : "");
                row.createCell(1).setCellValue(mov.getTipoMovimiento() != null ? mov.getTipoMovimiento().name() : "");
                row.createCell(2).setCellValue(mov.getAlmacen() != null ? mov.getAlmacen().name() : "");
                row.createCell(3).setCellValue(mov.getLote() != null && mov.getLote().getBatchNumber() != null ? mov.getLote().getBatchNumber() : "");
                row.createCell(4).setCellValue(entrada);
                row.createCell(5).setCellValue(salida);
                row.createCell(6).setCellValue(saldo);
            }

            for (int i = 0; i <= 6; i++) {
                sheet.autoSizeColumn(i);
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error generating kardex Excel", e);
        }
    }
}
