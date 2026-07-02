package exotic.app.planta.service.bi;

import exotic.app.planta.model.bi.dto.InformeDiarioComprasRowDTO;
import exotic.app.planta.model.bi.dto.InformeDiarioIngresoTerminadosReporteDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.EstadoMpsSemanalItem;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalDia;
import exotic.app.planta.model.produccion.MpsSemanalItem;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.MpsSemanalDiaRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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

    private static final String[] HEADERS_INFORME_COMPRAS = {
            "Fecha ingreso",
            "ID transacción",
            "ID OCM",
            "ID factura compra",
            "Proveedor NIT",
            "Proveedor nombre",
            "ID material",
            "Material nombre",
            "Tipo material",
            "Cantidad ingresada",
            "Unidad",
            "Lote (batch)",
            "Fecha vencimiento lote",
            "Almacén",
            "Usuario aprobador",
            "Observaciones"
    };

    private static final String[] HEADERS_REPORTE_TERMINADOS_CONSOLIDADO = {
            "Categoria ID",
            "Categoria",
            "Unidades planeadas",
            "Unidades producidas",
            "Capacidad productiva dia",
            "Rendimiento planeacion (%)",
            "Rendimiento operativo (%)",
            "Referencias planeadas",
            "Referencias producidas",
            "Referencias cumplidas"
    };

    private static final String[] HEADERS_REPORTE_TERMINADOS_DETALLE = {
            "Producto ID",
            "Producto",
            "Categoria ID",
            "Categoria",
            "Cantidad planeada",
            "Cantidad producida",
            "Diferencia",
            "Rendimiento planeacion (%)",
            "Planeado",
            "Producido",
            "No planeado"
    };

    private static final String[] HEADERS_REPORTE_TERMINADOS_MOVIMIENTOS = {
            "Movimiento ID",
            "Fecha movimiento",
            "Transaccion ID",
            "Orden produccion ID",
            "Producto ID",
            "Producto",
            "Categoria ID",
            "Categoria",
            "Cantidad",
            "Unidad",
            "Almacen",
            "Lote",
            "Fecha vencimiento",
            "Observaciones"
    };

    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final MasterProductionScheduleSemanalRepo masterProductionScheduleSemanalRepo;
    private final MpsSemanalDiaRepo mpsSemanalDiaRepo;
    private final CategoriaRepo categoriaRepo;

    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    /**
     * Excel con movimientos de ingreso de materiales en el día indicado (zona horaria por defecto de la JVM).
     */
    public byte[] exportarIngresoMaterialesExcel(LocalDate fecha) {
        return exportarIngresoMaterialesExcel(fecha, BiExcelExportOptions.standard());
    }

    public byte[] exportarIngresoMaterialesExcel(LocalDate fecha, BiExcelExportOptions options) {
        return exportarIngresoMaterialesExcel(fecha, fecha, options);
    }

    public byte[] exportarIngresoMaterialesExcel(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            BiExcelExportOptions options) {
        DateTimeRange range = resolveDateTimeRange(fechaDesde, fechaHasta);
        List<Movimiento> movimientos = transaccionAlmacenRepo.findIngresosMaterialPorDia(
                range.start(), range.end(), TIPOS_INGRESO_MATERIAL);
        return generarExcelMovimientosAlmacen(movimientos, "Ingreso materiales", "ingreso materiales", options);
    }

    /**
     * Excel con dispensaciones de materiales (OD / OD_RA) en el día indicado.
     */
    public byte[] exportarDispensacionMaterialesExcel(LocalDate fecha) {
        return exportarDispensacionMaterialesExcel(fecha, BiExcelExportOptions.standard());
    }

    public byte[] exportarDispensacionMaterialesExcel(LocalDate fecha, BiExcelExportOptions options) {
        return exportarDispensacionMaterialesExcel(fecha, fecha, options);
    }

    public byte[] exportarDispensacionMaterialesExcel(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            BiExcelExportOptions options) {
        DateTimeRange range = resolveDateTimeRange(fechaDesde, fechaHasta);
        List<Movimiento> movimientos = transaccionAlmacenRepo.findDispensacionesMaterialPorDia(
                range.start(), range.end(), Movimiento.TipoMovimiento.DISPENSACION);
        return generarExcelMovimientosAlmacen(movimientos, "Dispensación materiales", "dispensación materiales", options);
    }

    /**
     * Excel con ingresos de producto terminado (BACKFLUSH, típicamente cierre de OP) en el día indicado.
     */
    public byte[] exportarIngresoTerminadosExcel(LocalDate fecha) {
        return exportarIngresoTerminadosExcel(fecha, BiExcelExportOptions.standard());
    }

    public byte[] exportarIngresoTerminadosExcel(LocalDate fecha, BiExcelExportOptions options) {
        return exportarIngresoTerminadosExcel(fecha, fecha, options);
    }

    public byte[] exportarIngresoTerminadosExcel(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            BiExcelExportOptions options) {
        DateTimeRange range = resolveDateTimeRange(fechaDesde, fechaHasta);
        List<Movimiento> movimientos = transaccionAlmacenRepo.findIngresosTerminadoPorDia(
                range.start(), range.end(), Movimiento.TipoMovimiento.BACKFLUSH);
        return generarExcelMovimientosAlmacen(movimientos, "Ingreso producto terminado", "ingreso producto terminado", options);
    }

    public InformeDiarioIngresoTerminadosReporteDTO obtenerReporteIngresoTerminados(LocalDate fecha) {
        LocalDateTime start = fecha.atStartOfDay();
        LocalDateTime end = fecha.atTime(LocalTime.MAX);
        List<Movimiento> movimientos = transaccionAlmacenRepo.findIngresosTerminadoPorDia(
                start, end, Movimiento.TipoMovimiento.BACKFLUSH);
        double producidasDiaAnterior = totalProducidoEnFecha(fecha.minusDays(1));

        Optional<MasterProductionScheduleSemanal> mpsOpt = masterProductionScheduleSemanalRepo
                .findAllContainingDate(fecha)
                .stream()
                .findFirst();
        Optional<MpsSemanalDia> diaOpt = mpsOpt
                .flatMap((mps) -> mpsSemanalDiaRepo.findByMpsIdAndFecha(mps.getMpsId(), fecha));

        Map<String, ReferenciaAccumulator> referencias = new LinkedHashMap<>();
        diaOpt.ifPresent((dia) -> agregarPlaneacionDia(dia, referencias));

        List<InformeDiarioIngresoTerminadosReporteDTO.MovimientoDTO> movimientoRows = new ArrayList<>();
        for (Movimiento movimiento : movimientos) {
            DatosProductoTerminado datosProducto = datosProductoTerminado(movimiento.getProducto());
            String productoId = datosProducto.productoId() != null
                    ? datosProducto.productoId()
                    : "MOV-" + movimiento.getMovimientoId();
            ReferenciaAccumulator referencia = referencias.computeIfAbsent(
                    productoId,
                    (id) -> new ReferenciaAccumulator(
                            id,
                            defaultIfBlank(datosProducto.productoNombre(), "Producto sin nombre")));
            referencia.actualizarProducto(datosProducto);
            referencia.cantidadProducida += movimiento.getCantidad();
            movimientoRows.add(toMovimientoReporte(movimiento, datosProducto));
        }

        Map<Integer, Categoria> categorias = cargarCategorias(referencias);
        Map<String, CategoriaAccumulator> porCategoria = new HashMap<>();
        for (ReferenciaAccumulator referencia : referencias.values()) {
            int capacidad = capacidadCategoria(categorias, referencia.categoriaId);
            String categoriaNombre = nombreCategoria(referencia.categoriaNombre);
            String categoriaKey = referencia.categoriaId != null
                    ? "ID:" + referencia.categoriaId
                    : "NOMBRE:" + categoriaNombre;
            porCategoria.computeIfAbsent(
                            categoriaKey,
                            (ignored) -> new CategoriaAccumulator(
                                    referencia.categoriaId,
                                    categoriaNombre,
                                    capacidad))
                    .add(referencia);
        }

        List<InformeDiarioIngresoTerminadosReporteDTO.DetalleReferenciaDTO> detalleReferencias = referencias.values()
                .stream()
                .sorted(Comparator
                        .comparing((ReferenciaAccumulator ref) -> nombreCategoria(ref.categoriaNombre))
                        .thenComparing((ReferenciaAccumulator ref) -> defaultIfBlank(ref.productoNombre, "")))
                .map(ReferenciaAccumulator::toDetalleDto)
                .toList();

        List<InformeDiarioIngresoTerminadosReporteDTO.ConsolidadoCategoriaDTO> consolidadoCategorias =
                porCategoria.values()
                        .stream()
                        .sorted(Comparator.comparing((CategoriaAccumulator cat) -> nombreCategoria(cat.categoriaNombre)))
                        .map(CategoriaAccumulator::toDto)
                        .toList();

        double unidadesPlaneadas = referencias.values().stream()
                .mapToDouble((ref) -> ref.cantidadPlaneada)
                .sum();
        double unidadesProducidas = referencias.values().stream()
                .mapToDouble((ref) -> ref.cantidadProducida)
                .sum();
        double capacidadProductivaDia = porCategoria.values().stream()
                .mapToDouble((cat) -> cat.capacidadProductivaDia)
                .sum();
        int referenciasPlaneadas = (int) referencias.values().stream()
                .filter((ref) -> ref.cantidadPlaneada > 0)
                .count();
        int referenciasProducidas = (int) referencias.values().stream()
                .filter((ref) -> ref.cantidadProducida > 0)
                .count();
        int referenciasPlaneadasProducidas = (int) referencias.values().stream()
                .filter((ref) -> ref.cantidadPlaneada > 0 && ref.cantidadProducida > 0)
                .count();
        int referenciasNoPlaneadas = (int) referencias.values().stream()
                .filter((ref) -> ref.cantidadPlaneada <= 0 && ref.cantidadProducida > 0)
                .count();
        int categoriasConCapacidad = (int) porCategoria.values().stream()
                .filter((cat) -> cat.capacidadProductivaDia > 0)
                .count();
        int categoriasSinCapacidad = (int) porCategoria.values().stream()
                .filter((cat) -> cat.capacidadProductivaDia <= 0)
                .count();

        InformeDiarioIngresoTerminadosReporteDTO.ResumenDTO resumen =
                InformeDiarioIngresoTerminadosReporteDTO.ResumenDTO.builder()
                        .unidadesPlaneadas(unidadesPlaneadas)
                        .unidadesProducidas(unidadesProducidas)
                        .unidadesProducidasDiaAnterior(producidasDiaAnterior)
                        .capacidadProductivaDia(capacidadProductivaDia)
                        .rendimientoPlaneacionPct(pct(unidadesProducidas, unidadesPlaneadas))
                        .cumplimientoReferenciasPct(pct(referenciasPlaneadasProducidas, referenciasPlaneadas))
                        .rendimientoOperativoPct(pct(unidadesProducidas, capacidadProductivaDia))
                        .tendenciaVsDiaAnteriorPct(tendencia(unidadesProducidas, producidasDiaAnterior))
                        .referenciasPlaneadas(referenciasPlaneadas)
                        .referenciasProducidas(referenciasProducidas)
                        .referenciasPlaneadasProducidas(referenciasPlaneadasProducidas)
                        .referenciasNoPlaneadas(referenciasNoPlaneadas)
                        .categoriasConCapacidad(categoriasConCapacidad)
                        .categoriasSinCapacidad(categoriasSinCapacidad)
                        .build();

        return InformeDiarioIngresoTerminadosReporteDTO.builder()
                .fecha(fecha)
                .mpsId(mpsOpt.map(MasterProductionScheduleSemanal::getMpsId).orElse(null))
                .mpsEstado(mpsOpt.map((mps) -> mps.getEstado() != null ? mps.getEstado().name() : null).orElse(null))
                .weekStartDate(mpsOpt.map(MasterProductionScheduleSemanal::getWeekStartDate).orElse(null))
                .weekEndDate(mpsOpt.map(MasterProductionScheduleSemanal::getWeekEndDate).orElse(null))
                .resumen(resumen)
                .consolidadoCategorias(consolidadoCategorias)
                .detalleReferencias(detalleReferencias)
                .movimientos(movimientoRows)
                .build();
    }

    public byte[] exportarReporteIngresoTerminadosExcel(LocalDate fecha) {
        return exportarReporteIngresoTerminadosExcel(fecha, BiExcelExportOptions.standard());
    }

    public byte[] exportarReporteIngresoTerminadosExcel(LocalDate fecha, BiExcelExportOptions options) {
        InformeDiarioIngresoTerminadosReporteDTO reporte = obtenerReporteIngresoTerminados(fecha);
        return generarExcelReporteIngresoTerminados(reporte, options);
    }

    /**
     * Excel con ingresos a almacén originados por OCM en el día indicado.
     */
    public byte[] exportarComprasExcel(LocalDate fecha) {
        return exportarComprasExcel(fecha, BiExcelExportOptions.standard());
    }

    public byte[] exportarComprasExcel(LocalDate fecha, BiExcelExportOptions options) {
        return exportarComprasExcel(fecha, fecha, options);
    }

    public byte[] exportarComprasExcel(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            BiExcelExportOptions options) {
        DateTimeRange range = resolveDateTimeRange(fechaDesde, fechaHasta);
        List<InformeDiarioComprasRowDTO> rows = transaccionAlmacenRepo.findInformeDiarioComprasPorDia(
                range.start(),
                range.end(),
                TransaccionAlmacen.TipoEntidadCausante.OCM,
                Movimiento.TipoMovimiento.COMPRA);
        return generarExcelCompras(rows, "Compras", "compras OCM", options);
    }

    /**
     * Excel con movimientos de ajuste de almacén ({@link Movimiento.TipoMovimiento#AJUSTE_POSITIVO} /
     * {@link Movimiento.TipoMovimiento#AJUSTE_NEGATIVO}) en el rango de fechas inclusive.
     */
    public byte[] exportarAjustesAlmacenExcel(
            LocalDate fechaDesde, LocalDate fechaHasta, SentidoAjusteInforme sentido) {
        return exportarAjustesAlmacenExcel(fechaDesde, fechaHasta, sentido, BiExcelExportOptions.standard());
    }

    public byte[] exportarAjustesAlmacenExcel(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            SentidoAjusteInforme sentido,
            BiExcelExportOptions options) {
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
                    "ajustes almacén entradas",
                    options);
            case SALIDAS -> generarExcelMovimientosAlmacen(
                    transaccionAlmacenRepo.findAjustesAlmacenSalidasPorRango(
                            start, end, Movimiento.TipoMovimiento.AJUSTE_NEGATIVO),
                    "Ajustes almacén salidas",
                    "ajustes almacén salidas",
                    options);
            case MIXTA -> generarExcelMovimientosAlmacen(
                    transaccionAlmacenRepo.findAjustesAlmacenMixtaPorRango(
                            start,
                            end,
                            Movimiento.TipoMovimiento.AJUSTE_POSITIVO,
                            Movimiento.TipoMovimiento.AJUSTE_NEGATIVO),
                    "Ajustes almacén mixto",
                    "ajustes almacén mixto",
                    options);
        };
    }

    private double totalProducidoEnFecha(LocalDate fecha) {
        LocalDateTime start = fecha.atStartOfDay();
        LocalDateTime end = fecha.atTime(LocalTime.MAX);
        return transaccionAlmacenRepo.findIngresosTerminadoPorDia(
                        start, end, Movimiento.TipoMovimiento.BACKFLUSH)
                .stream()
                .mapToDouble(Movimiento::getCantidad)
                .sum();
    }

    private static DateTimeRange resolveDateTimeRange(LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaDesde == null || fechaHasta == null) {
            throw new IllegalArgumentException("fechaDesde y fechaHasta son obligatorias");
        }
        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException("fechaDesde no puede ser posterior a fechaHasta");
        }
        return new DateTimeRange(fechaDesde.atStartOfDay(), fechaHasta.atTime(LocalTime.MAX));
    }

    private void agregarPlaneacionDia(
            MpsSemanalDia dia,
            Map<String, ReferenciaAccumulator> referencias) {
        for (MpsSemanalItem item : dia.getItems()) {
            if (item.getEstado() == EstadoMpsSemanalItem.CANCELADO) {
                continue;
            }
            DatosProductoTerminado datosProducto = datosProductoTerminado(item.getTerminado());
            String productoId = datosProducto.productoId() != null
                    ? datosProducto.productoId()
                    : "MPS-" + item.getId();
            ReferenciaAccumulator referencia = referencias.computeIfAbsent(
                    productoId,
                    (id) -> new ReferenciaAccumulator(
                            id,
                            defaultIfBlank(item.getTerminadoNombre(), defaultIfBlank(datosProducto.productoNombre(), id))));
            referencia.actualizarProducto(datosProducto);
            referencia.actualizarCategoria(
                    item.getCategoriaId(),
                    defaultIfBlank(item.getCategoriaNombre(), datosProducto.categoriaNombre()));
            referencia.cantidadPlaneada += item.getCantidadTotal();
        }
    }

    private DatosProductoTerminado datosProductoTerminado(Producto producto) {
        Integer categoriaId = null;
        String categoriaNombre = null;
        if (producto instanceof Terminado terminado && terminado.getCategoria() != null) {
            categoriaId = terminado.getCategoria().getCategoriaId();
            categoriaNombre = terminado.getCategoria().getCategoriaNombre();
        }
        return new DatosProductoTerminado(
                producto != null ? producto.getProductoId() : null,
                producto != null ? producto.getNombre() : null,
                categoriaId,
                categoriaNombre,
                producto != null ? producto.getTipoUnidades() : null);
    }

    private InformeDiarioIngresoTerminadosReporteDTO.MovimientoDTO toMovimientoReporte(
            Movimiento movimiento,
            DatosProductoTerminado datosProducto) {
        TransaccionAlmacen tx = movimiento.getTransaccionAlmacen();
        return InformeDiarioIngresoTerminadosReporteDTO.MovimientoDTO.builder()
                .movimientoId(movimiento.getMovimientoId())
                .fechaMovimiento(movimiento.getFechaMovimiento())
                .transaccionId(tx != null ? tx.getTransaccionId() : null)
                .ordenProduccionId(tx != null ? tx.getIdEntidadCausante() : null)
                .productoId(datosProducto.productoId())
                .productoNombre(datosProducto.productoNombre())
                .categoriaId(datosProducto.categoriaId())
                .categoriaNombre(nombreCategoria(datosProducto.categoriaNombre()))
                .cantidad(movimiento.getCantidad())
                .unidad(datosProducto.unidad())
                .almacen(movimiento.getAlmacen() != null ? movimiento.getAlmacen().name() : null)
                .loteBatchNumber(
                        movimiento.getLote() != null ? movimiento.getLote().getBatchNumber() : null)
                .fechaVencimiento(
                        movimiento.getLote() != null ? movimiento.getLote().getExpirationDate() : null)
                .observaciones(tx != null ? tx.getObservaciones() : null)
                .build();
    }

    private Map<Integer, Categoria> cargarCategorias(Map<String, ReferenciaAccumulator> referencias) {
        Set<Integer> categoriaIds = new HashSet<>();
        for (ReferenciaAccumulator referencia : referencias.values()) {
            if (referencia.categoriaId != null) {
                categoriaIds.add(referencia.categoriaId);
            }
        }

        Map<Integer, Categoria> categorias = new HashMap<>();
        if (categoriaIds.isEmpty()) {
            return categorias;
        }
        for (Categoria categoria : categoriaRepo.findAllById(categoriaIds)) {
            categorias.put(categoria.getCategoriaId(), categoria);
        }
        return categorias;
    }

    private static int capacidadCategoria(Map<Integer, Categoria> categorias, Integer categoriaId) {
        if (categoriaId == null || !categorias.containsKey(categoriaId)) {
            return 0;
        }
        return categorias.get(categoriaId).getCapacidadProductivaDiaria();
    }

    private byte[] generarExcelReporteIngresoTerminados(
            InformeDiarioIngresoTerminadosReporteDTO reporte,
            BiExcelExportOptions options) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelStyles styles = createExcelStyles(workbook, options);

            Sheet resumenSheet = workbook.createSheet("Resumen");
            escribirResumenReporteTerminados(reporte, resumenSheet, styles);

            Sheet consolidadoSheet = workbook.createSheet("Consolidado Categoria");
            escribirHeader(consolidadoSheet, HEADERS_REPORTE_TERMINADOS_CONSOLIDADO);
            int rowIdx = 1;
            for (InformeDiarioIngresoTerminadosReporteDTO.ConsolidadoCategoriaDTO row
                    : reporte.getConsolidadoCategorias()) {
                escribirFilaConsolidadoTerminados(row, consolidadoSheet.createRow(rowIdx++), styles);
            }
            autosize(consolidadoSheet, HEADERS_REPORTE_TERMINADOS_CONSOLIDADO.length);

            Sheet detalleSheet = workbook.createSheet("Detalle Referencia");
            escribirHeader(detalleSheet, HEADERS_REPORTE_TERMINADOS_DETALLE);
            rowIdx = 1;
            for (InformeDiarioIngresoTerminadosReporteDTO.DetalleReferenciaDTO row
                    : reporte.getDetalleReferencias()) {
                escribirFilaDetalleTerminados(row, detalleSheet.createRow(rowIdx++), styles);
            }
            autosize(detalleSheet, HEADERS_REPORTE_TERMINADOS_DETALLE.length);

            Sheet movimientosSheet = workbook.createSheet("Movimientos");
            escribirHeader(movimientosSheet, HEADERS_REPORTE_TERMINADOS_MOVIMIENTOS);
            rowIdx = 1;
            for (InformeDiarioIngresoTerminadosReporteDTO.MovimientoDTO row : reporte.getMovimientos()) {
                escribirFilaMovimientoTerminados(row, movimientosSheet.createRow(rowIdx++), styles);
            }
            autosize(movimientosSheet, HEADERS_REPORTE_TERMINADOS_MOVIMIENTOS.length);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Error generando Excel reporte ingreso producto terminado", e);
            throw new RuntimeException("Error generando Excel reporte ingreso producto terminado", e);
        }
    }

    private static void escribirResumenReporteTerminados(
            InformeDiarioIngresoTerminadosReporteDTO reporte,
            Sheet sheet,
            ExcelStyles styles) {
        int rowIdx = 0;
        escribirResumenRow(sheet, rowIdx++, "Fecha", reporte.getFecha(), styles);
        escribirResumenRow(sheet, rowIdx++, "MPS ID", reporte.getMpsId(), styles);
        escribirResumenRow(sheet, rowIdx++, "Estado MPS", reporte.getMpsEstado(), styles);
        escribirResumenRow(sheet, rowIdx++, "Semana inicio", reporte.getWeekStartDate(), styles);
        escribirResumenRow(sheet, rowIdx++, "Semana fin", reporte.getWeekEndDate(), styles);

        InformeDiarioIngresoTerminadosReporteDTO.ResumenDTO resumen = reporte.getResumen();
        if (resumen != null) {
            escribirResumenRow(sheet, rowIdx++, "Unidades planeadas", resumen.getUnidadesPlaneadas(), styles);
            escribirResumenRow(sheet, rowIdx++, "Unidades producidas", resumen.getUnidadesProducidas(), styles);
            escribirResumenRow(sheet, rowIdx++, "Unidades producidas dia anterior", resumen.getUnidadesProducidasDiaAnterior(), styles);
            escribirResumenRow(sheet, rowIdx++, "Capacidad productiva dia", resumen.getCapacidadProductivaDia(), styles);
            escribirResumenRow(sheet, rowIdx++, "Rendimiento planeacion (%)", resumen.getRendimientoPlaneacionPct(), styles);
            escribirResumenRow(sheet, rowIdx++, "Cumplimiento referencias (%)", resumen.getCumplimientoReferenciasPct(), styles);
            escribirResumenRow(sheet, rowIdx++, "Rendimiento operativo (%)", resumen.getRendimientoOperativoPct(), styles);
            escribirResumenRow(sheet, rowIdx++, "Tendencia vs dia anterior (%)", resumen.getTendenciaVsDiaAnteriorPct(), styles);
            escribirResumenRow(sheet, rowIdx++, "Referencias planeadas", resumen.getReferenciasPlaneadas(), styles);
            escribirResumenRow(sheet, rowIdx++, "Referencias producidas", resumen.getReferenciasProducidas(), styles);
            escribirResumenRow(sheet, rowIdx++, "Referencias planeadas producidas", resumen.getReferenciasPlaneadasProducidas(), styles);
            escribirResumenRow(sheet, rowIdx++, "Referencias no planeadas", resumen.getReferenciasNoPlaneadas(), styles);
            escribirResumenRow(sheet, rowIdx++, "Categorias con capacidad", resumen.getCategoriasConCapacidad(), styles);
            escribirResumenRow(sheet, rowIdx, "Categorias sin capacidad", resumen.getCategoriasSinCapacidad(), styles);
        }
        autosize(sheet, 2);
    }

    private static void escribirFilaConsolidadoTerminados(
            InformeDiarioIngresoTerminadosReporteDTO.ConsolidadoCategoriaDTO dto,
            Row row,
            ExcelStyles styles) {
        int c = 0;
        writeCell(row, c++, dto.getCategoriaId(), styles);
        writeCell(row, c++, dto.getCategoriaNombre(), styles);
        writeCell(row, c++, dto.getUnidadesPlaneadas(), styles);
        writeCell(row, c++, dto.getUnidadesProducidas(), styles);
        writeCell(row, c++, dto.getCapacidadProductivaDia(), styles);
        writeCell(row, c++, dto.getRendimientoPlaneacionPct(), styles);
        writeCell(row, c++, dto.getRendimientoOperativoPct(), styles);
        writeCell(row, c++, dto.getReferenciasPlaneadas(), styles);
        writeCell(row, c++, dto.getReferenciasProducidas(), styles);
        writeCell(row, c, dto.getReferenciasPlaneadasProducidas(), styles);
    }

    private static void escribirFilaDetalleTerminados(
            InformeDiarioIngresoTerminadosReporteDTO.DetalleReferenciaDTO dto,
            Row row,
            ExcelStyles styles) {
        int c = 0;
        writeCell(row, c++, dto.getProductoId(), styles);
        writeCell(row, c++, dto.getProductoNombre(), styles);
        writeCell(row, c++, dto.getCategoriaId(), styles);
        writeCell(row, c++, dto.getCategoriaNombre(), styles);
        writeCell(row, c++, dto.getCantidadPlaneada(), styles);
        writeCell(row, c++, dto.getCantidadProducida(), styles);
        writeCell(row, c++, dto.getDiferencia(), styles);
        writeCell(row, c++, dto.getRendimientoPlaneacionPct(), styles);
        writeCell(row, c++, dto.isPlaneado(), styles);
        writeCell(row, c++, dto.isProducido(), styles);
        writeCell(row, c, dto.isNoPlaneado(), styles);
    }

    private static void escribirFilaMovimientoTerminados(
            InformeDiarioIngresoTerminadosReporteDTO.MovimientoDTO dto,
            Row row,
            ExcelStyles styles) {
        int c = 0;
        writeCell(row, c++, dto.getMovimientoId(), styles);
        writeCell(row, c++, dto.getFechaMovimiento(), styles);
        writeCell(row, c++, dto.getTransaccionId(), styles);
        writeCell(row, c++, dto.getOrdenProduccionId(), styles);
        writeCell(row, c++, dto.getProductoId(), styles);
        writeCell(row, c++, dto.getProductoNombre(), styles);
        writeCell(row, c++, dto.getCategoriaId(), styles);
        writeCell(row, c++, dto.getCategoriaNombre(), styles);
        writeCell(row, c++, dto.getCantidad(), styles);
        writeCell(row, c++, dto.getUnidad(), styles);
        writeCell(row, c++, dto.getAlmacen(), styles);
        writeCell(row, c++, dto.getLoteBatchNumber(), styles);
        writeCell(row, c++, dto.getFechaVencimiento(), styles);
        writeCell(row, c, dto.getObservaciones(), styles);
    }

    private static void escribirHeader(Sheet sheet, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private static void escribirResumenRow(Sheet sheet, int rowIdx, String label, Object value, ExcelStyles styles) {
        Row row = sheet.createRow(rowIdx);
        writeCell(row, 0, label, styles);
        writeCell(row, 1, value, styles);
    }

    private static void writeCell(Row row, int index, Object value, ExcelStyles styles) {
        Cell cell = row.createCell(index);
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        if (value instanceof Number number) {
            if (isDecimalNumber(number) && styles.hasDecimalTextFormatter()) {
                styles.writeDecimalTextCell(cell, number);
                return;
            }
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool ? "SI" : "NO");
            return;
        }
        cell.setCellValue(value.toString());
    }

    private static boolean isDecimalNumber(Number number) {
        return number instanceof Double
                || number instanceof Float
                || number instanceof java.math.BigDecimal;
    }

    private static ExcelStyles createExcelStyles(XSSFWorkbook workbook, BiExcelExportOptions options) {
        if (options == null || !options.isTextDeterministic()) {
            return ExcelStyles.none();
        }
        CellStyle decimalTextStyle = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        decimalTextStyle.setDataFormat(dataFormat.getFormat("@"));
        return new ExcelStyles(decimalTextStyle, options.decimalSeparator().createFormatter());
    }

    private record ExcelStyles(CellStyle decimalTextStyle, DecimalFormat decimalFormatter) {
        private static ExcelStyles none() {
            return new ExcelStyles(null, null);
        }

        private boolean hasDecimalTextFormatter() {
            return decimalFormatter != null;
        }

        private void writeDecimalTextCell(Cell cell, Number number) {
            cell.setCellValue(decimalFormatter.format(decimalFormatValue(number)));
            if (decimalTextStyle != null) {
                cell.setCellStyle(decimalTextStyle);
            }
        }

        private Object decimalFormatValue(Number number) {
            if (number instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            }
            double doubleValue = number.doubleValue();
            if (!Double.isFinite(doubleValue)) {
                return doubleValue;
            }
            return BigDecimal.valueOf(doubleValue);
        }
    }

    private static void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static Double pct(double numerator, double denominator) {
        if (denominator <= 0) {
            return null;
        }
        return numerator * 100d / denominator;
    }

    private static Double tendencia(double today, double previous) {
        if (previous <= 0) {
            return null;
        }
        return (today - previous) * 100d / previous;
    }

    private static String nombreCategoria(String categoriaNombre) {
        return defaultIfBlank(categoriaNombre, "Sin categoria");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private record DateTimeRange(LocalDateTime start, LocalDateTime end) {
    }

    private record DatosProductoTerminado(
            String productoId,
            String productoNombre,
            Integer categoriaId,
            String categoriaNombre,
            String unidad) {
    }

    private static class ReferenciaAccumulator {
        private final String productoId;
        private String productoNombre;
        private Integer categoriaId;
        private String categoriaNombre;
        private double cantidadPlaneada;
        private double cantidadProducida;

        private ReferenciaAccumulator(String productoId, String productoNombre) {
            this.productoId = productoId;
            this.productoNombre = productoNombre;
        }

        private void actualizarProducto(DatosProductoTerminado datosProducto) {
            if (datosProducto == null) {
                return;
            }
            if (datosProducto.productoNombre() != null && !datosProducto.productoNombre().isBlank()) {
                this.productoNombre = datosProducto.productoNombre();
            }
            actualizarCategoria(datosProducto.categoriaId(), datosProducto.categoriaNombre());
        }

        private void actualizarCategoria(Integer categoriaId, String categoriaNombre) {
            if (this.categoriaId == null && categoriaId != null) {
                this.categoriaId = categoriaId;
            }
            if ((this.categoriaNombre == null || this.categoriaNombre.isBlank())
                    && categoriaNombre != null
                    && !categoriaNombre.isBlank()) {
                this.categoriaNombre = categoriaNombre;
            }
        }

        private InformeDiarioIngresoTerminadosReporteDTO.DetalleReferenciaDTO toDetalleDto() {
            boolean planeado = cantidadPlaneada > 0;
            boolean producido = cantidadProducida > 0;
            return InformeDiarioIngresoTerminadosReporteDTO.DetalleReferenciaDTO.builder()
                    .productoId(productoId)
                    .productoNombre(defaultIfBlank(productoNombre, productoId))
                    .categoriaId(categoriaId)
                    .categoriaNombre(nombreCategoria(categoriaNombre))
                    .cantidadPlaneada(cantidadPlaneada)
                    .cantidadProducida(cantidadProducida)
                    .diferencia(cantidadProducida - cantidadPlaneada)
                    .rendimientoPlaneacionPct(pct(cantidadProducida, cantidadPlaneada))
                    .planeado(planeado)
                    .producido(producido)
                    .noPlaneado(!planeado && producido)
                    .build();
        }
    }

    private static class CategoriaAccumulator {
        private final Integer categoriaId;
        private final String categoriaNombre;
        private final int capacidadProductivaDia;
        private double cantidadPlaneada;
        private double cantidadProducida;
        private int referenciasPlaneadas;
        private int referenciasProducidas;
        private int referenciasPlaneadasProducidas;

        private CategoriaAccumulator(Integer categoriaId, String categoriaNombre, int capacidadProductivaDia) {
            this.categoriaId = categoriaId;
            this.categoriaNombre = categoriaNombre;
            this.capacidadProductivaDia = capacidadProductivaDia;
        }

        private void add(ReferenciaAccumulator referencia) {
            cantidadPlaneada += referencia.cantidadPlaneada;
            cantidadProducida += referencia.cantidadProducida;
            if (referencia.cantidadPlaneada > 0) {
                referenciasPlaneadas++;
            }
            if (referencia.cantidadProducida > 0) {
                referenciasProducidas++;
            }
            if (referencia.cantidadPlaneada > 0 && referencia.cantidadProducida > 0) {
                referenciasPlaneadasProducidas++;
            }
        }

        private InformeDiarioIngresoTerminadosReporteDTO.ConsolidadoCategoriaDTO toDto() {
            return InformeDiarioIngresoTerminadosReporteDTO.ConsolidadoCategoriaDTO.builder()
                    .categoriaId(categoriaId)
                    .categoriaNombre(nombreCategoria(categoriaNombre))
                    .unidadesPlaneadas(cantidadPlaneada)
                    .unidadesProducidas(cantidadProducida)
                    .capacidadProductivaDia(capacidadProductivaDia)
                    .rendimientoPlaneacionPct(pct(cantidadProducida, cantidadPlaneada))
                    .rendimientoOperativoPct(pct(cantidadProducida, capacidadProductivaDia))
                    .referenciasPlaneadas(referenciasPlaneadas)
                    .referenciasProducidas(referenciasProducidas)
                    .referenciasPlaneadasProducidas(referenciasPlaneadasProducidas)
                    .build();
        }
    }

    private byte[] generarExcelMovimientosAlmacen(
            List<Movimiento> movimientos,
            String nombreHoja,
            String contextoLog,
            BiExcelExportOptions options) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelStyles styles = createExcelStyles(workbook, options);
            Sheet sheet = workbook.createSheet(nombreHoja);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS_MOVIMIENTO_ALMACEN.length; i++) {
                headerRow.createCell(i).setCellValue(HEADERS_MOVIMIENTO_ALMACEN[i]);
            }

            int rowIdx = 1;
            for (Movimiento mov : movimientos) {
                escribirFilaMovimiento(mov, sheet.createRow(rowIdx++), styles);
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

    private byte[] generarExcelCompras(
            List<InformeDiarioComprasRowDTO> rows,
            String nombreHoja,
            String contextoLog,
            BiExcelExportOptions options) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelStyles styles = createExcelStyles(workbook, options);
            Sheet sheet = workbook.createSheet(nombreHoja);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS_INFORME_COMPRAS.length; i++) {
                headerRow.createCell(i).setCellValue(HEADERS_INFORME_COMPRAS[i]);
            }

            int rowIdx = 1;
            for (InformeDiarioComprasRowDTO dto : rows) {
                escribirFilaInformeCompras(dto, sheet.createRow(rowIdx++), styles);
            }

            for (int i = 0; i < HEADERS_INFORME_COMPRAS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Error generando Excel {}", contextoLog, e);
            throw new RuntimeException("Error generando Excel " + contextoLog, e);
        }
    }

    private static void escribirFilaMovimiento(Movimiento mov, Row row, ExcelStyles styles) {
        int c = 0;
        row.createCell(c++).setCellValue(
                mov.getFechaMovimiento() != null ? mov.getFechaMovimiento().toString() : "");
        row.createCell(c++).setCellValue(
                mov.getProducto() != null && mov.getProducto().getProductoId() != null
                        ? mov.getProducto().getProductoId() : "");
        row.createCell(c++).setCellValue(
                mov.getProducto() != null && mov.getProducto().getNombre() != null
                        ? mov.getProducto().getNombre() : "");
        writeCell(row, c++, mov.getCantidad(), styles);
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

    private static void escribirFilaInformeCompras(
            InformeDiarioComprasRowDTO dto,
            Row row,
            ExcelStyles styles) {
        int c = 0;
        row.createCell(c++).setCellValue(dto.getFechaIngreso() != null ? dto.getFechaIngreso().toString() : "");
        row.createCell(c++).setCellValue(dto.getTransaccionId() != null ? dto.getTransaccionId() : 0);
        row.createCell(c++).setCellValue(dto.getOrdenCompraId() != null ? dto.getOrdenCompraId() : 0);
        row.createCell(c++).setCellValue(
                dto.getFacturaCompraId() != null ? String.valueOf(dto.getFacturaCompraId()) : "");
        row.createCell(c++).setCellValue(dto.getProveedorNit() != null ? dto.getProveedorNit() : "");
        row.createCell(c++).setCellValue(dto.getProveedorNombre() != null ? dto.getProveedorNombre() : "");
        row.createCell(c++).setCellValue(dto.getMaterialId() != null ? dto.getMaterialId() : "");
        row.createCell(c++).setCellValue(dto.getMaterialNombre() != null ? dto.getMaterialNombre() : "");
        row.createCell(c++).setCellValue(dto.getTipoMaterial() != null ? dto.getTipoMaterial() : "");
        writeCell(row, c++, dto.getCantidadIngresada() != null ? dto.getCantidadIngresada() : 0d, styles);
        row.createCell(c++).setCellValue(dto.getUnidad() != null ? dto.getUnidad() : "");
        row.createCell(c++).setCellValue(dto.getBatchNumber() != null ? dto.getBatchNumber() : "");
        row.createCell(c++).setCellValue(
                dto.getFechaVencimientoLote() != null ? dto.getFechaVencimientoLote().toString() : "");
        row.createCell(c++).setCellValue(dto.getAlmacen() != null ? dto.getAlmacen().name() : "");
        row.createCell(c++).setCellValue(
                dto.getUsuarioAprobadorNombre() != null ? dto.getUsuarioAprobadorNombre() : "");
        row.createCell(c).setCellValue(dto.getObservaciones() != null ? dto.getObservaciones() : "");
    }
}
