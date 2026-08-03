package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.dto.AlcanceInventario;
import exotic.app.planta.model.inventarios.dto.InventarioConsolidadoPageDTO;
import exotic.app.planta.model.inventarios.dto.InventarioExcelRequestDTO;
import exotic.app.planta.model.inventarios.dto.KardexMovimientoRowDTO;
import exotic.app.planta.model.inventarios.dto.KardexMovimientosPageDTO;
import exotic.app.planta.model.inventarios.dto.KardexMovimientosRequestDTO;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.dto.ProductoStockDTO;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.service.productos.ProductoService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventarioService {

    private final ProductoService productoService;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final MovimientosService movimientosService;
    private final Clock applicationClock;

    @Transactional(readOnly = true)
    public InventarioConsolidadoPageDTO getInventarioConsolidado(
            String searchTerm,
            String tipoBusqueda,
            int page,
            int size,
            AlcanceInventario alcance,
            List<Movimiento.Almacen> almacenesPersonalizados
    ) {
        String tipoNormalizado = normalizeTipoBusqueda(tipoBusqueda);
        int paginaNormalizada = Math.max(page, 0);
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("size debe estar entre 1 y 100");
        }

        AlcanceInventario alcanceNormalizado = alcance != null
                ? alcance
                : AlcanceInventario.FISICO_TOTAL;
        List<Movimiento.Almacen> almacenes = resolveAlmacenes(
                alcanceNormalizado,
                almacenesPersonalizados
        );
        OffsetDateTime fechaHoraCorte = OffsetDateTime.now(applicationClock);

        Page<ProductoStockDTO> productos = movimientosService.searchProductsWithStockByAlmacenes(
                normalizeSearchTerm(searchTerm),
                tipoNormalizado,
                paginaNormalizada,
                size,
                almacenes,
                fechaHoraCorte.toLocalDateTime()
        );

        return new InventarioConsolidadoPageDTO(
                productos.getContent(),
                productos.getNumber(),
                productos.getSize(),
                productos.getTotalElements(),
                productos.getTotalPages(),
                alcanceNormalizado,
                almacenes,
                fechaHoraCorte
        );
    }

    @Transactional(readOnly = true)
    public byte[] generateInventoryExcel(InventarioExcelRequestDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("DTO requerido");
        }

        AlcanceInventario alcance = dto.getAlcance() != null
                ? dto.getAlcance()
                : AlcanceInventario.FISICO_TOTAL;
        List<Movimiento.Almacen> almacenes = resolveAlmacenes(alcance, dto.getAlmacenes());
        OffsetDateTime fechaHoraCorte = OffsetDateTime.now(applicationClock);
        List<ProductoStockDTO> productos = movimientosService.findProductsWithStockForExportByAlmacenes(
                normalizeSearchTerm(dto.getSearchTerm()),
                normalizeTipoBusqueda(dto.getTipoBusqueda()),
                almacenes,
                fechaHoraCorte.toLocalDateTime()
        );

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Inventario");

            int rowIdx = 0;
            Row alcanceRow = sheet.createRow(rowIdx++);
            alcanceRow.createCell(0).setCellValue("Alcance del stock");
            alcanceRow.createCell(1).setCellValue(getAlcanceLabel(alcance));

            Row almacenesRow = sheet.createRow(rowIdx++);
            almacenesRow.createCell(0).setCellValue("Almacenes incluidos");
            almacenesRow.createCell(1).setCellValue(
                    String.join(", ", almacenes.stream().map(Enum::name).toList())
            );

            Row corteRow = sheet.createRow(rowIdx++);
            corteRow.createCell(0).setCellValue("Fecha y hora de corte");
            corteRow.createCell(1).setCellValue(
                    fechaHoraCorte.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX"))
            );

            rowIdx++;
            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.createCell(0).setCellValue("ID");
            headerRow.createCell(1).setCellValue("Nombre");
            headerRow.createCell(2).setCellValue("Stock");
            headerRow.createCell(3).setCellValue("Unidades");

            for (ProductoStockDTO productoStock : productos) {
                Producto producto = productoStock.getProducto();
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(producto.getProductoId());
                row.createCell(1).setCellValue(producto.getNombre());
                row.createCell(2).setCellValue(productoStock.getStock());
                row.createCell(3).setCellValue(producto.getTipoUnidades() != null ? producto.getTipoUnidades() : "");
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

    private String normalizeSearchTerm(String searchTerm) {
        return searchTerm != null ? searchTerm.trim() : "";
    }

    private String normalizeTipoBusqueda(String tipoBusqueda) {
        String normalized = tipoBusqueda != null
                ? tipoBusqueda.trim().toUpperCase(Locale.ROOT)
                : "NOMBRE";
        if (!"NOMBRE".equals(normalized) && !"ID".equals(normalized)) {
            throw new IllegalArgumentException("tipoBusqueda debe ser NOMBRE o ID");
        }
        return normalized;
    }

    private List<Movimiento.Almacen> resolveAlmacenes(
            AlcanceInventario alcance,
            List<Movimiento.Almacen> almacenesPersonalizados
    ) {
        return switch (alcance) {
            case FISICO_TOTAL -> List.copyOf(Arrays.asList(Movimiento.Almacen.values()));
            case DISPONIBLE_OPERATIVO -> List.of(Movimiento.Almacen.GENERAL);
            case RESTRINGIDO -> List.of(
                    Movimiento.Almacen.AVERIAS,
                    Movimiento.Almacen.CALIDAD,
                    Movimiento.Almacen.DEVOLUCIONES
            );
            case PERSONALIZADO -> normalizeAlmacenesPersonalizados(almacenesPersonalizados);
        };
    }

    private List<Movimiento.Almacen> normalizeAlmacenesPersonalizados(
            List<Movimiento.Almacen> almacenesPersonalizados
    ) {
        if (almacenesPersonalizados == null
                || almacenesPersonalizados.isEmpty()
                || almacenesPersonalizados.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(
                    "PERSONALIZADO requiere al menos un almacén válido"
            );
        }

        Set<Movimiento.Almacen> seleccion = new HashSet<>(almacenesPersonalizados);
        if (seleccion.size() != almacenesPersonalizados.size()) {
            throw new IllegalArgumentException(
                    "PERSONALIZADO no admite almacenes duplicados"
            );
        }
        return Arrays.stream(Movimiento.Almacen.values())
                .filter(seleccion::contains)
                .toList();
    }

    private String getAlcanceLabel(AlcanceInventario alcance) {
        return switch (alcance) {
            case FISICO_TOTAL -> "Inventario físico total";
            case DISPONIBLE_OPERATIVO -> "Disponible operativo";
            case RESTRINGIDO -> "Stock restringido/no disponible";
            case PERSONALIZADO -> "Personalizado";
        };
    }

    public KardexMovimientosPageDTO getKardexMovimientosPage(KardexMovimientosRequestDTO dto) {
        if (dto == null) throw new IllegalArgumentException("DTO requerido");
        if (dto.getProductoId() == null || dto.getProductoId().trim().isEmpty()) {
            throw new IllegalArgumentException("productoId requerido");
        }
        if (dto.getAlmacen() == null || dto.getAlmacen().trim().isEmpty()) {
            throw new IllegalArgumentException("almacen requerido");
        }

        Movimiento.Almacen almacen;
        try {
            almacen = Movimiento.Almacen.valueOf(dto.getAlmacen().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Almacen no valido: " + dto.getAlmacen());
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

        Double saldoInicialRaw = transaccionAlmacenRepo.findTotalCantidadByProductoIdAndAlmacenAndFechaMovimientoBefore(
                dto.getProductoId(), almacen, startDateTime
        );
        double saldoInicial = saldoInicialRaw != null ? saldoInicialRaw : 0.0;

        Page<Movimiento> movPage = transaccionAlmacenRepo
                .findByProducto_ProductoIdAndAlmacenAndFechaMovimientoBetweenOrderByFechaMovimientoAscMovimientoIdAsc(
                        dto.getProductoId(), almacen, startDateTime, endDateTime, pageable
                );

        double deltaAntesDePagina = 0.0;
        if (movPage.getNumber() > 0 && !movPage.getContent().isEmpty()) {
            Movimiento cursor = movPage.getContent().get(0);
            Double delta = transaccionAlmacenRepo.sumCantidadInRangeBeforeCursorAndAlmacen(
                    dto.getProductoId(),
                    almacen,
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
        if (dto.getAlmacen() == null || dto.getAlmacen().trim().isEmpty()) {
            throw new IllegalArgumentException("almacen requerido");
        }

        Movimiento.Almacen almacen;
        try {
            almacen = Movimiento.Almacen.valueOf(dto.getAlmacen().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Almacen no valido: " + dto.getAlmacen());
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

        Double saldoInicialRaw = transaccionAlmacenRepo.findTotalCantidadByProductoIdAndAlmacenAndFechaMovimientoBefore(
                dto.getProductoId(), almacen, startDateTime
        );
        double saldoInicial = saldoInicialRaw != null ? saldoInicialRaw : 0.0;

        List<Movimiento> movimientos = transaccionAlmacenRepo
                .findByProducto_ProductoIdAndAlmacenAndFechaMovimientoBetweenOrderByFechaMovimientoAscMovimientoIdAsc(
                        dto.getProductoId(), almacen, startDateTime, endDateTime
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

            rowIdx++;

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
