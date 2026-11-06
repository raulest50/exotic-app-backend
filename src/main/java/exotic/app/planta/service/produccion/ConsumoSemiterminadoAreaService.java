package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.produccion.EstadoDispensacionMateriales;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.dto.ConsumoSemiterminadoAreaDTOs.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.service.inventarios.SalidaAlmacenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsumoSemiterminadoAreaService {
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final SeguimientoOrdenAreaRepo seguimientoRepo;
    private final MaterialRequirementSnapshotService snapshotService;
    private final TransaccionAlmacenRepo movimientoRepo;
    private final TransaccionAlmacenHeaderRepo transaccionRepo;
    private final ProductoRepo productoRepo;
    private final LoteRepo loteRepo;
    private final SalidaAlmacenService salidaAlmacenService;
    private final BatchRecordProjectionQueueService projectionQueueService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public Contexto preparar(int ordenId, Long seguimientoId, User actor) {
        ContextoEntidades contexto = cargar(ordenId, seguimientoId, actor, false);
        return toContexto(contexto);
    }

    public List<LoteDisponible> lotes(int ordenId, Long seguimientoId, String productoId, User actor) {
        ContextoEntidades contexto = cargar(ordenId, seguimientoId, actor, false);
        List<MaterialRequirementSnapshotService.RequirementView> requisitos = requisitos(contexto);
        if (requisitos.stream().noneMatch(req -> req.productoId().equals(productoId))) {
            throw new IllegalArgumentException("El producto no es una entrada congelada de esta etapa.");
        }
        return movimientoRepo.findLotesWithStockByProductoIdAndAlmacenOrderByExpirationDate(
                        productoId, Movimiento.Almacen.GENERAL).stream()
                .filter(row -> row[0] instanceof Lote lote && salidaAlmacenService.esLoteConsumible(lote)
                        && (lote.getProducto() == null || productoId.equals(lote.getProducto().getProductoId())))
                .map(row -> {
                    Lote lote = (Lote) row[0];
                    return new LoteDisponible(lote.getId(), lote.getBatchNumber(), lote.getExpirationDate(),
                            lote.getEstadoCalidad() == null ? null : lote.getEstadoCalidad().name(),
                            decimal(BigDecimal.valueOf(((Number) row[1]).doubleValue())));
                }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public RegistroResponse registrar(int ordenId, Long seguimientoId, RegistroRequest request, User actor) {
        // Padre -> seguimiento -> lotes: serializa saldo por OP y cierre de la etapa.
        ContextoEntidades contexto = cargar(ordenId, seguimientoId, actor, true);
        List<ItemNormalizado> items = normalizar(request);
        String hash = hashSolicitud(ordenId, seguimientoId, contexto.seguimiento().getAreaOperativa().getAreaId(),
                items, normalizarTexto(request.getObservaciones()));
        TransaccionAlmacen existente = transaccionRepo.findByIdempotencyKey(request.getIdempotencyKey()).orElse(null);
        if (existente != null) {
            if (existente.getTipoEntidadCausante() != TransaccionAlmacen.TipoEntidadCausante.OD
                    || existente.getIdEntidadCausante() != ordenId || !hash.equals(existente.getRequestHash())) {
                throw new IllegalStateException("La clave de idempotencia ya fue utilizada con otra solicitud.");
            }
            return new RegistroResponse(existente.getTransaccionId(), true, toContexto(contexto));
        }
        List<MaterialRequirementSnapshotService.RequirementView> requisitos = requisitos(contexto);
        Map<String, BigDecimal> entregados = cantidadesRegistradas(contexto);
        String bloqueo = motivoBloqueo(contexto, requisitos, entregados);
        if (bloqueo != null) throw new IllegalStateException(bloqueo);
        Map<String, BigDecimal> demanda = new LinkedHashMap<>();
        items.forEach(item -> demanda.merge(item.productoId(), item.cantidad(), BigDecimal::add));
        Map<String, MaterialRequirementSnapshotService.RequirementView> porProducto = requisitos.stream()
                .collect(Collectors.toMap(MaterialRequirementSnapshotService.RequirementView::productoId, req -> req));
        for (Map.Entry<String, BigDecimal> entry : demanda.entrySet()) {
            var requisito = porProducto.get(entry.getKey());
            if (requisito == null) throw new IllegalArgumentException("Semiterminado no asignado a esta etapa: " + entry.getKey());
            BigDecimal pendiente = cantidadOperativa(requisito.cantidad())
                    .subtract(entregados.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            if (entry.getValue().compareTo(pendiente) > 0) {
                throw new IllegalStateException("La cantidad supera el saldo pendiente de " + entry.getKey() + ".");
            }
        }
        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setTipoEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.OD);
        transaccion.setIdEntidadCausante(ordenId);
        transaccion.setIdempotencyKey(request.getIdempotencyKey());
        transaccion.setRequestHash(hash);
        transaccion.setObservaciones(normalizarTexto(request.getObservaciones()));
        transaccion.setUsuarioAprobador(actor);
        transaccion.setUsuariosResponsables(List.of(actor));
        List<Movimiento> movimientos = new ArrayList<>();
        for (ItemNormalizado item : items) {
            Producto producto = (Producto) Hibernate.unproxy(productoRepo.findById(item.productoId())
                    .orElseThrow(() -> new IllegalArgumentException("Semiterminado inexistente: " + item.productoId())));
            if (!(producto instanceof SemiTerminado)) {
                throw new IllegalArgumentException("La entrada congelada no corresponde a un semiterminado.");
            }
            Movimiento movimiento = new Movimiento();
            movimiento.setTransaccionAlmacen(transaccion);
            movimiento.setProducto(producto);
            movimiento.setLote(loteRepo.getReferenceById(item.loteId()));
            movimiento.setCantidad(item.cantidad().negate().doubleValue());
            movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.DISPENSACION);
            movimiento.setAfectaInventario(true);
            movimiento.setAlmacen(Movimiento.Almacen.GENERAL);
            movimiento.setAreaOperativa(contexto.seguimiento().getAreaOperativa());
            movimientos.add(movimiento);
        }
        salidaAlmacenService.validarYBloquearLotes(movimientos);
        transaccion.setMovimientosTransaccion(movimientos);
        transaccionRepo.saveAndFlush(transaccion);
        OrdenProduccion orden = contexto.orden();
        if (orden.getEstadoOrden() == 0) orden.setEstadoOrden(11);
        else if (orden.getEstadoOrden() >= 11) orden.setEstadoOrden(orden.getEstadoOrden() + 1);
        orden.setEstadoDispensacionMateriales(EstadoDispensacionMateriales.PARCIAL);
        ordenProduccionRepo.save(orden);
        projectionQueueService.solicitarActualizacion(orden);
        return new RegistroResponse(transaccion.getTransaccionId(), false, toContexto(contexto));
    }

    private ContextoEntidades cargar(int ordenId, Long seguimientoId, User actor, boolean bloquear) {
        if (actor == null || actor.getId() == null) throw new AccessDeniedException("Se requiere usuario autenticado.");
        OrdenProduccion orden = (bloquear ? ordenProduccionRepo.findByIdForUpdate(ordenId)
                : ordenProduccionRepo.findById(ordenId)).orElseThrow(() -> new NoSuchElementException("OP inexistente."));
        if (bloquear) entityManager.refresh(orden, LockModeType.PESSIMISTIC_WRITE);
        SeguimientoOrdenArea seguimiento = (bloquear ? seguimientoRepo.findByIdForUpdate(seguimientoId)
                : seguimientoRepo.findById(seguimientoId)).orElseThrow(() -> new NoSuchElementException("Seguimiento inexistente."));
        if (bloquear) entityManager.refresh(seguimiento, LockModeType.PESSIMISTIC_WRITE);
        if (seguimiento.getOrdenProduccion().getOrdenId() != ordenId) {
            throw new NoSuchElementException("El seguimiento no pertenece a la OP indicada.");
        }
        var area = seguimiento.getAreaOperativa();
        if (area == null || area.getAreaId() == -1 || area.getResponsableArea() == null
                || !actor.getId().equals(area.getResponsableArea().getId())) {
            throw new AccessDeniedException("Solo el responsable del area de esta etapa puede consultar o registrar sus consumos.");
        }
        if (orden.getRequerimientosMaterialesJson() != null && (orden.getRutaProcesoCatVersion() == null
                || seguimiento.getRutaProcesoNode() == null
                || !orden.getRutaProcesoCatVersion().getId().equals(
                        seguimiento.getRutaProcesoNode().getRutaProcesoCatVersion().getId()))) {
            throw new IllegalStateException("La etapa no corresponde a la ruta congelada de la OP.");
        }
        return new ContextoEntidades(orden, seguimiento);
    }

    private List<MaterialRequirementSnapshotService.RequirementView> requisitos(ContextoEntidades contexto) {
        if (contexto.orden().getRequerimientosMaterialesJson() == null) return List.of();
        var requisitos = snapshotService.requerimientosEtapa(contexto.orden(), contexto.seguimiento().getRutaProcesoNode().getId());
        if (requisitos.stream().anyMatch(req -> !req.esSemiterminadoFisico()
                || !Objects.equals(req.areaOperativaId(), contexto.seguimiento().getAreaOperativa().getAreaId()))) {
            throw new IllegalStateException("El snapshot operativo contiene una asignacion incompatible con la etapa.");
        }
        return requisitos;
    }

    private Map<String, BigDecimal> cantidadesRegistradas(ContextoEntidades contexto) {
        Map<String, BigDecimal> cantidades = new HashMap<>();
        movimientoRepo.findDispensadoPorArea(contexto.orden().getOrdenId(), contexto.seguimiento().getAreaOperativa().getAreaId(),
                TransaccionAlmacen.TipoEntidadCausante.OD, Movimiento.TipoMovimiento.DISPENSACION)
                .forEach(row -> cantidades.put((String) row[0], cantidadOperativa(BigDecimal.valueOf(((Number) row[1]).doubleValue()))));
        return cantidades;
    }

    private Contexto toContexto(ContextoEntidades contexto) {
        var requisitos = requisitos(contexto);
        var cantidades = cantidadesRegistradas(contexto);
        List<Requerimiento> items = requisitos.stream().map(req -> {
            BigDecimal requerida = cantidadOperativa(req.cantidad());
            BigDecimal registrada = cantidades.getOrDefault(req.productoId(), BigDecimal.ZERO);
            return new Requerimiento(req.productoId(), req.productoNombre(), req.unidadMedida(), decimal(requerida),
                    decimal(registrada), decimal(requerida.subtract(registrada).max(BigDecimal.ZERO)));
        }).toList();
        int areaId = contexto.seguimiento().getAreaOperativa().getAreaId();
        List<Registro> registros = transaccionRepo.findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OD, contexto.orden().getOrdenId()).stream()
                .filter(transaccion -> transaccion.getIdempotencyKey() != null)
                .sorted(Comparator.comparingInt(TransaccionAlmacen::getTransaccionId).reversed())
                .filter(transaccion -> transaccion.getMovimientosTransaccion().stream().anyMatch(movimiento ->
                        movimiento.getAreaOperativa() != null && movimiento.getAreaOperativa().getAreaId() == areaId))
                .map(transaccion -> new Registro(transaccion.getTransaccionId(), transaccion.getFechaTransaccion(),
                        transaccion.getUsuarioAprobador() == null ? null : transaccion.getUsuarioAprobador().getUsername(),
                        transaccion.getObservaciones(), transaccion.getMovimientosTransaccion().stream()
                        .filter(m -> m.getAreaOperativa() != null && m.getAreaOperativa().getAreaId() == areaId)
                        .map(m -> new ItemRegistrado(m.getProducto().getProductoId(), m.getLote().getId(),
                                m.getLote().getBatchNumber(), decimal(BigDecimal.valueOf(-m.getCantidad())))).toList())).toList();
        String motivo = motivoBloqueo(contexto, requisitos, cantidades);
        return new Contexto(contexto.orden().getOrdenId(), contexto.seguimiento().getId(),
                contexto.orden().getRutaProcesoCatVersion() == null ? null : contexto.orden().getRutaProcesoCatVersion().getId(),
                contexto.seguimiento().getRutaProcesoNode() == null ? null : contexto.seguimiento().getRutaProcesoNode().getId(), areaId,
                contexto.seguimiento().getAreaOperativa().getNombre(), contexto.orden().getRequerimientosMaterialesJson() == null,
                motivo == null, motivo, items, registros);
    }

    private String motivoBloqueo(ContextoEntidades contexto,
                                  List<MaterialRequirementSnapshotService.RequirementView> requisitos,
                                  Map<String, BigDecimal> registrados) {
        if (contexto.orden().getRequerimientosMaterialesJson() == null) return "OP historica: conserva la dispensacion original de almacen.";
        if (contexto.orden().getEstadoOrden() == -1 || contexto.orden().getEstadoOrden() == 2
                || contexto.orden().getEstadoOrden() == 3) return "La OP esta cancelada, cerrada o ya termino su fabricacion.";
        int estado = contexto.seguimiento().getEstado();
        if (estado != SeguimientoOrdenArea.ESTADO_ESPERA && estado != SeguimientoOrdenArea.ESTADO_EN_PROCESO) {
            return "Solo se registran entradas mientras la etapa esta en espera o en proceso.";
        }
        if (requisitos.isEmpty()) return "Esta etapa no tiene semiterminados asignados en la OP.";
        if (requisitos.stream().noneMatch(req -> cantidadOperativa(req.cantidad())
                .compareTo(registrados.getOrDefault(req.productoId(), BigDecimal.ZERO)) > 0)) {
            return "Los semiterminados de la etapa ya fueron registrados completamente.";
        }
        return null;
    }

    private List<ItemNormalizado> normalizar(RegistroRequest request) {
        if (request == null || request.getIdempotencyKey() == null || request.getLotes() == null
                || request.getLotes().isEmpty() || request.getLotes().size() > 200
                || (request.getObservaciones() != null && request.getObservaciones().length() > 255)) {
            throw new IllegalArgumentException("Se requieren UUID, entre 1 y 200 lotes y observaciones de hasta 255 caracteres.");
        }
        List<ItemNormalizado> items = new ArrayList<>();
        for (LoteRequest item : request.getLotes()) {
            if (item == null || item.getProductoId() == null || item.getProductoId().isBlank()
                    || item.getProductoId().length() > 255 || item.getLoteId() == null || item.getLoteId() <= 0
                    || item.getCantidad() == null || !item.getCantidad().matches("\\d{1,14}(\\.\\d{1,4})?")) {
                throw new IllegalArgumentException("Cada entrada requiere producto, lote y cantidad decimal positiva de hasta cuatro decimales.");
            }
            BigDecimal cantidad = new BigDecimal(item.getCantidad()).setScale(4, RoundingMode.UNNECESSARY);
            if (cantidad.signum() <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
            items.add(new ItemNormalizado(item.getProductoId().trim(), item.getLoteId(), cantidad));
        }
        items.sort(Comparator.comparing(ItemNormalizado::productoId).thenComparing(ItemNormalizado::loteId)
                .thenComparing(ItemNormalizado::cantidad));
        return items;
    }

    private String hashSolicitud(int ordenId, Long seguimientoId, int areaId, List<ItemNormalizado> items, String observaciones) {
        try {
            String canonical = objectMapper.writeValueAsString(Arrays.asList(ordenId, seguimientoId, areaId, items, observaciones));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No se pudo identificar la solicitud de consumo.", exception);
        }
    }

    private String normalizarTexto(String texto) { return texto == null || texto.isBlank() ? null : texto.trim(); }
    private BigDecimal cantidadOperativa(BigDecimal cantidad) { return cantidad.setScale(4, RoundingMode.HALF_UP); }
    private String decimal(BigDecimal cantidad) { return cantidadOperativa(cantidad).toPlainString(); }
    private record ContextoEntidades(OrdenProduccion orden, SeguimientoOrdenArea seguimiento) {}
    private record ItemNormalizado(String productoId, Long loteId, BigDecimal cantidad) {}
}
