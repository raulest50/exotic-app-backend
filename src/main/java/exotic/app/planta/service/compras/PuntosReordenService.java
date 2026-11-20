package exotic.app.planta.service.compras;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.config.TimeZoneConfig;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.compras.dto.PuntosReordenDTOs.*;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo.ReordenProveedorFrecuenciaProjection;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Calcula propuestas sin escribir materiales ni alterar el informe de dispensacion. */
@Service
@RequiredArgsConstructor
public class PuntosReordenService {
    private static final int MIN_RANGO_DIAS = 45;
    private static final int MAX_RANGO_DIAS = 3660;
    private static final int MAX_MATERIALES = 10000;
    private static final ZoneId BOGOTA = ZoneId.of(TimeZoneConfig.APP_TIME_ZONE_ID);
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final Comparator<ReordenProveedorFrecuenciaProjection> FRECUENCIA =
            Comparator.comparingLong((ReordenProveedorFrecuenciaProjection row) -> row.getCompras()).reversed()
                    .thenComparing(ReordenProveedorFrecuenciaProjection::getUltimaCompra,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ReordenProveedorFrecuenciaProjection::getProveedorId);

    private final MaterialRepo materialRepo;
    private final ProveedorRepo proveedorRepo;
    private final ItemOrdenCompraRepo itemOrdenCompraRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final ObjectMapper objectMapper;
    private final Clock applicationClock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CalculoResponse calcular(Parametros request) {
        Parametros parametros = normalizar(request);
        List<Material> materiales = materialRepo.findByInventareableTrueOrderByProductoIdAsc();
        if (materiales.size() > MAX_MATERIALES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El calculo admite hasta 10000 materiales por consulta.");
        }
        Set<String> materialIds = materiales.stream().map(Material::getProductoId).collect(Collectors.toSet());
        if (!materialIds.containsAll(parametros.proveedoresPorMaterial().keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una reasignacion corresponde a un material no inventariable o inexistente.");
        }
        Map<String, Proveedor> manuales = cargarProveedoresManuales(parametros);
        Map<String, List<ReordenProveedorFrecuenciaProjection>> frecuencias = itemOrdenCompraRepo
                .findFrecuenciasProveedorParaReorden().stream()
                .collect(Collectors.groupingBy(ReordenProveedorFrecuenciaProjection::getProductoId));
        Map<String, BigDecimal> consumos = new HashMap<>();
        SortedSet<LocalDate> diasConDispensacion = new TreeSet<>();
        Set<LocalDate> excluidos = new HashSet<>(parametros.fechasExcluidas());
        for (var movimiento : transaccionAlmacenRepo.findConsumoDiarioParaReorden(
                parametros.fechaDesde().atStartOfDay(), parametros.fechaHasta().plusDays(1).atStartOfDay(),
                Movimiento.TipoMovimiento.DISPENSACION)) {
            Double cantidad = movimiento.getCantidad();
            if (cantidad == null || !Double.isFinite(cantidad) || cantidad <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El historial de dispensacion contiene una cantidad no valida.");
            }
            // El denominador comun se obtiene del informe completo, nunca de los filtros de pantalla.
            diasConDispensacion.add(movimiento.getDia());
            if (!excluidos.contains(movimiento.getDia())) {
                consumos.merge(movimiento.getProductoId(), BigDecimal.valueOf(cantidad), BigDecimal::add);
            }
        }
        int diasEfectivos = (int) diasConDispensacion.stream().filter(dia -> !excluidos.contains(dia)).count();
        List<Fila> filas = materiales.stream()
                .map(material -> crearFila(material, parametros, manuales,
                        frecuencias.getOrDefault(material.getProductoId(), List.of()),
                        consumos.getOrDefault(material.getProductoId(), BigDecimal.ZERO), diasEfectivos))
                .toList();
        int diasCalendario = Math.toIntExact(ChronoUnit.DAYS.between(
                parametros.fechaDesde(), parametros.fechaHasta()) + 1);
        String hash = huella(new DatosCalculo(parametros, diasEfectivos, List.copyOf(diasConDispensacion), filas));
        return new CalculoResponse(hash, parametros, OffsetDateTime.now(applicationClock.withZone(BOGOTA)),
                diasCalendario, excluidos.size(), diasEfectivos, parametros.minimoDiasEfectivos(),
                List.copyOf(diasConDispensacion), filas);
    }

    private Fila crearFila(Material material, Parametros parametros, Map<String, Proveedor> manuales,
                          List<ReordenProveedorFrecuenciaProjection> frecuencias, BigDecimal total, int dias) {
        boolean manual = parametros.proveedoresPorMaterial().containsKey(material.getProductoId());
        String elegido = parametros.proveedoresPorMaterial().get(material.getProductoId());
        ReordenProveedorFrecuenciaProjection sugerencia = frecuencias.stream()
                .filter(fila -> !manual || Objects.equals(elegido, fila.getProveedorId()))
                .min(FRECUENCIA).orElse(null);
        Proveedor proveedor = manual ? manuales.get(elegido) : null;
        String proveedorId = manual ? proveedor.getId() : sugerencia == null ? null : sugerencia.getProveedorId();
        String proveedorNombre = manual ? proveedor.getNombre()
                : sugerencia == null ? null : sugerencia.getProveedorNombre();
        Integer leadTime = manual ? proveedor.getLeadTimeNegociado()
                : sugerencia == null ? null : sugerencia.getLeadTimeNegociado();
        BigDecimal diario = dias == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(dias), PRECISION);
        BigDecimal base = leadTime == null || leadTime < 0 ? BigDecimal.ZERO
                : diario.multiply(BigDecimal.valueOf(leadTime), PRECISION);
        BigDecimal seguridad = base.multiply(parametros.porcentajeSeguridad(), PRECISION).movePointLeft(2);
        String estado = dias == 0 ? "SIN_DIAS" : total.signum() == 0 ? "SIN_CONSUMO"
                : proveedorId == null ? "SIN_PROVEEDOR" : leadTime == null || leadTime < 0
                ? "SIN_LEAD_TIME" : "LISTO";
        // Se redondea solo el resultado final y siempre hacia arriba para no perder cobertura.
        BigDecimal nuevo = "LISTO".equals(estado) ? base.add(seguridad).setScale(
                "U".equalsIgnoreCase(material.getTipoUnidades()) ? 0 : 6, RoundingMode.CEILING) : null;
        if (nuevo != null && !Double.isFinite(nuevo.doubleValue())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El punto de reorden calculado excede la capacidad del campo del material.");
        }
        return new Fila(material.getProductoId(), Objects.toString(material.getNombre(), ""),
                Objects.toString(material.getTipoUnidades(), ""), proveedorId, proveedorNombre, leadTime,
                sugerencia == null ? 0L : sugerencia.getCompras(), manual, total,
                diario.setScale(12, RoundingMode.HALF_UP), seguridad.setScale(12, RoundingMode.HALF_UP),
                material.getPuntoReorden(), nuevo, estado,
                "LISTO".equals(estado) && nuevo.signum() > 0 && material.getPuntoReorden() >= 0
                        && BigDecimal.valueOf(material.getPuntoReorden()).compareTo(nuevo) != 0);
    }

    private Map<String, Proveedor> cargarProveedoresManuales(Parametros parametros) {
        Set<String> ids = new HashSet<>(parametros.proveedoresPorMaterial().values());
        if (ids.isEmpty()) return Map.of();
        Map<String, Proveedor> proveedores = proveedorRepo.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Proveedor::getId, Function.identity()));
        if (!proveedores.keySet().containsAll(ids)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno de los proveedores seleccionados ya no existe. Seleccione otro proveedor.");
        }
        return proveedores;
    }

    private Parametros normalizar(Parametros request) {
        if (request == null || request.fechaDesde() == null || request.fechaHasta() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione las fechas inicial y final.");
        }
        long dias = ChronoUnit.DAYS.between(request.fechaDesde(), request.fechaHasta()) + 1;
        if (dias < MIN_RANGO_DIAS || dias > MAX_RANGO_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango debe contener entre 45 y 3660 dias calendario, incluidos ambos extremos.");
        }
        if (request.fechaHasta().isAfter(LocalDate.now(applicationClock.withZone(BOGOTA)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se permiten fechas futuras.");
        }
        BigDecimal porcentaje = request.porcentajeSeguridad();
        if (porcentaje == null || porcentaje.signum() < 0 || porcentaje.compareTo(BigDecimal.valueOf(100)) > 0
                || porcentaje.scale() > 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El stock de seguridad debe ser un porcentaje entre 0 y 100, con hasta seis decimales.");
        }
        Integer minimo = request.minimoDiasEfectivos();
        if (minimo == null || minimo < 1 || minimo > MAX_RANGO_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El minimo de dias efectivos debe estar entre 1 y 3660.");
        }
        List<LocalDate> fechas = request.fechasExcluidas() == null ? List.of() : request.fechasExcluidas();
        if (fechas.size() > MAX_RANGO_DIAS || fechas.stream().anyMatch(dia -> dia == null
                || dia.isBefore(request.fechaDesde()) || dia.isAfter(request.fechaHasta()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los dias excluidos deben pertenecer al rango seleccionado.");
        }
        Map<String, String> manuales = request.proveedoresPorMaterial() == null ? Map.of() : request.proveedoresPorMaterial();
        if (manuales.size() > MAX_MATERIALES || manuales.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las reasignaciones deben indicar material y proveedor existentes.");
        }
        return new Parametros(request.fechaDesde(), request.fechaHasta(), porcentaje.stripTrailingZeros(), minimo,
                fechas.stream().distinct().sorted().toList(), Collections.unmodifiableMap(new TreeMap<>(manuales)));
    }

    private String huella(DatosCalculo datos) {
        try {
            byte[] serializado = objectMapper.writeValueAsBytes(datos);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serializado));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No fue posible identificar la propuesta de reorden.", exception);
        }
    }

    private record DatosCalculo(Parametros parametros, int diasEfectivos, List<LocalDate> dias, List<Fila> filas) {}
}
