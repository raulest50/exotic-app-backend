package exotic.app.planta.service.bi;

import exotic.app.planta.model.bi.dto.InformeGlobalProduccionDTO;
import exotic.app.planta.model.inventarios.Movimiento;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InformesGlobalesService {

    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final MasterProductionScheduleSemanalRepo masterProductionScheduleSemanalRepo;
    private final MpsSemanalDiaRepo mpsSemanalDiaRepo;
    private final CategoriaRepo categoriaRepo;

    public InformeGlobalProduccionDTO obtenerReporteProduccion(LocalDate fechaDesde, LocalDate fechaHasta) {
        DateTimeRange range = resolveDateTimeRange(fechaDesde, fechaHasta);
        int diasRango = (int) ChronoUnit.DAYS.between(fechaDesde, fechaHasta) + 1;

        List<Movimiento> movimientos = transaccionAlmacenRepo.findIngresosTerminadoPorDia(
                range.start(), range.end(), Movimiento.TipoMovimiento.BACKFLUSH);
        double producidasPeriodoAnterior = totalProducidoEnRango(
                fechaDesde.minusDays(diasRango), fechaDesde.minusDays(1));

        Map<String, ReferenciaAccumulator> referencias = new LinkedHashMap<>();
        Set<Integer> mpsIds = new LinkedHashSet<>();
        for (LocalDate fecha = fechaDesde; !fecha.isAfter(fechaHasta); fecha = fecha.plusDays(1)) {
            LocalDate fechaConsulta = fecha;
            Optional<MasterProductionScheduleSemanal> mpsOpt = masterProductionScheduleSemanalRepo
                    .findAllContainingDate(fechaConsulta)
                    .stream()
                    .findFirst();
            mpsOpt.ifPresent((mps) -> mpsIds.add(mps.getMpsId()));
            mpsOpt.flatMap((mps) -> mpsSemanalDiaRepo.findByMpsIdAndFecha(mps.getMpsId(), fechaConsulta))
                    .ifPresent((dia) -> agregarPlaneacionDia(dia, referencias));
        }

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
        }

        Map<Integer, Categoria> categorias = cargarCategorias(referencias);
        Map<String, CategoriaAccumulator> porCategoria = new HashMap<>();
        for (ReferenciaAccumulator referencia : referencias.values()) {
            int capacidadPeriodo = capacidadCategoria(categorias, referencia.categoriaId) * diasRango;
            String categoriaNombre = nombreCategoria(referencia.categoriaNombre);
            String categoriaKey = referencia.categoriaId != null
                    ? "ID:" + referencia.categoriaId
                    : "NOMBRE:" + categoriaNombre;
            porCategoria.computeIfAbsent(
                            categoriaKey,
                            (ignored) -> new CategoriaAccumulator(
                                    referencia.categoriaId,
                                    categoriaNombre,
                                    capacidadPeriodo))
                    .add(referencia);
        }

        List<InformeGlobalProduccionDTO.DetalleReferenciaDTO> detalleReferencias = referencias.values()
                .stream()
                .sorted(Comparator
                        .comparing((ReferenciaAccumulator ref) -> nombreCategoria(ref.categoriaNombre))
                        .thenComparing((ReferenciaAccumulator ref) -> defaultIfBlank(ref.productoNombre, "")))
                .map(ReferenciaAccumulator::toDetalleDto)
                .toList();

        List<InformeGlobalProduccionDTO.ConsolidadoCategoriaDTO> consolidadoCategorias = porCategoria.values()
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
        double capacidadProductivaPeriodo = porCategoria.values().stream()
                .mapToDouble((cat) -> cat.capacidadProductivaPeriodo)
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
                .filter((cat) -> cat.capacidadProductivaPeriodo > 0)
                .count();
        int categoriasSinCapacidad = (int) porCategoria.values().stream()
                .filter((cat) -> cat.capacidadProductivaPeriodo <= 0)
                .count();

        InformeGlobalProduccionDTO.ResumenDTO resumen = InformeGlobalProduccionDTO.ResumenDTO.builder()
                .unidadesPlaneadas(unidadesPlaneadas)
                .unidadesProducidas(unidadesProducidas)
                .unidadesProducidasPeriodoAnterior(producidasPeriodoAnterior)
                .capacidadProductivaPeriodo(capacidadProductivaPeriodo)
                .rendimientoPlaneacionPct(pct(unidadesProducidas, unidadesPlaneadas))
                .cumplimientoReferenciasPct(pct(referenciasPlaneadasProducidas, referenciasPlaneadas))
                .capacidadUtilizadaPct(pct(unidadesProducidas, capacidadProductivaPeriodo))
                .tendenciaProduccionPct(tendencia(unidadesProducidas, producidasPeriodoAnterior))
                .referenciasPlaneadas(referenciasPlaneadas)
                .referenciasProducidas(referenciasProducidas)
                .referenciasPlaneadasProducidas(referenciasPlaneadasProducidas)
                .referenciasNoPlaneadas(referenciasNoPlaneadas)
                .categoriasConCapacidad(categoriasConCapacidad)
                .categoriasSinCapacidad(categoriasSinCapacidad)
                .movimientosProduccion(movimientos.size())
                .build();

        return InformeGlobalProduccionDTO.builder()
                .fechaDesde(fechaDesde)
                .fechaHasta(fechaHasta)
                .modoFecha(fechaDesde.equals(fechaHasta) ? "FECHA_UNICA" : "RANGO")
                .diasRango(diasRango)
                .mpsIds(new ArrayList<>(mpsIds))
                .resumen(resumen)
                .consolidadoCategorias(consolidadoCategorias)
                .detalleReferencias(detalleReferencias)
                .notas(crearNotas(mpsIds, resumen))
                .build();
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

    private double totalProducidoEnRango(LocalDate fechaDesde, LocalDate fechaHasta) {
        DateTimeRange range = resolveDateTimeRange(fechaDesde, fechaHasta);
        return transaccionAlmacenRepo.findIngresosTerminadoPorDia(
                        range.start(), range.end(), Movimiento.TipoMovimiento.BACKFLUSH)
                .stream()
                .mapToDouble(Movimiento::getCantidad)
                .sum();
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
                categoriaNombre);
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

    private List<InformeGlobalProduccionDTO.NotaDTO> crearNotas(
            Set<Integer> mpsIds,
            InformeGlobalProduccionDTO.ResumenDTO resumen) {
        List<InformeGlobalProduccionDTO.NotaDTO> notas = new ArrayList<>();
        if (mpsIds.isEmpty()) {
            notas.add(InformeGlobalProduccionDTO.NotaDTO.builder()
                    .tipo("WARNING")
                    .mensaje("No se encontro MPS para el periodo seleccionado; los indicadores contra planeacion pueden quedar sin base.")
                    .build());
        }
        if (resumen.getCategoriasSinCapacidad() > 0) {
            notas.add(InformeGlobalProduccionDTO.NotaDTO.builder()
                    .tipo("INFO")
                    .mensaje("Hay categorias sin capacidad productiva configurada; la capacidad utilizada puede quedar incompleta.")
                    .build());
        }
        if (resumen.getReferenciasNoPlaneadas() > 0) {
            notas.add(InformeGlobalProduccionDTO.NotaDTO.builder()
                    .tipo("INFO")
                    .mensaje("Se encontraron referencias producidas que no estaban planeadas en el MPS del periodo.")
                    .build());
        }
        return notas;
    }

    private static int capacidadCategoria(Map<Integer, Categoria> categorias, Integer categoriaId) {
        if (categoriaId == null || !categorias.containsKey(categoriaId)) {
            return 0;
        }
        return categorias.get(categoriaId).getCapacidadProductivaDiaria();
    }

    private static Double pct(double numerator, double denominator) {
        if (denominator <= 0) {
            return null;
        }
        return numerator * 100d / denominator;
    }

    private static Double tendencia(double current, double previous) {
        if (previous <= 0) {
            return null;
        }
        return (current - previous) * 100d / previous;
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
            String categoriaNombre) {
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

        private InformeGlobalProduccionDTO.DetalleReferenciaDTO toDetalleDto() {
            boolean planeado = cantidadPlaneada > 0;
            boolean producido = cantidadProducida > 0;
            return InformeGlobalProduccionDTO.DetalleReferenciaDTO.builder()
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
        private final int capacidadProductivaPeriodo;
        private double cantidadPlaneada;
        private double cantidadProducida;
        private int referenciasPlaneadas;
        private int referenciasProducidas;
        private int referenciasPlaneadasProducidas;

        private CategoriaAccumulator(Integer categoriaId, String categoriaNombre, int capacidadProductivaPeriodo) {
            this.categoriaId = categoriaId;
            this.categoriaNombre = categoriaNombre;
            this.capacidadProductivaPeriodo = capacidadProductivaPeriodo;
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

        private InformeGlobalProduccionDTO.ConsolidadoCategoriaDTO toDto() {
            return InformeGlobalProduccionDTO.ConsolidadoCategoriaDTO.builder()
                    .categoriaId(categoriaId)
                    .categoriaNombre(nombreCategoria(categoriaNombre))
                    .unidadesPlaneadas(cantidadPlaneada)
                    .unidadesProducidas(cantidadProducida)
                    .capacidadProductivaPeriodo(capacidadProductivaPeriodo)
                    .rendimientoPlaneacionPct(pct(cantidadProducida, cantidadPlaneada))
                    .cumplimientoReferenciasPct(pct(referenciasPlaneadasProducidas, referenciasPlaneadas))
                    .capacidadUtilizadaPct(pct(cantidadProducida, capacidadProductivaPeriodo))
                    .referenciasPlaneadas(referenciasPlaneadas)
                    .referenciasProducidas(referenciasProducidas)
                    .referenciasPlaneadasProducidas(referenciasPlaneadasProducidas)
                    .build();
        }
    }
}
