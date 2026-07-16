package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.produccion.CierreProduccion;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.ReporteProduccionLote;
import exotic.app.planta.model.produccion.dto.CierreProduccionRequestDTO;
import exotic.app.planta.model.produccion.dto.CierreProduccionResponseDTO;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.produccion.CierreProduccionRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.ReporteProduccionLoteRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CierreProduccionService {

    private static final int ESTADO_FABRICACION_COMPLETADA = 3;
    private static final int ESTADO_TERMINADA = 2;

    private final CierreProduccionRepo cierreRepo;
    private final ReporteProduccionLoteRepo reporteRepo;
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final LoteRepo loteRepo;
    private final TransaccionAlmacenHeaderRepo transaccionRepo;
    private final ProduccionCierreLockService cierreLockService;
    private final Clock applicationClock;

    @Transactional(rollbackFor = Exception.class)
    public CierreProduccionResponseDTO confirmar(User actor, CierreProduccionRequestDTO request) {
        validarSolicitud(request);
        String solicitudHash = calcularHash(request);
        cierreLockService.lockFecha(request.getFechaProduccion());
        cierreLockService.lockIdempotency(request.getIdempotencyKey());

        CierreProduccion existente = cierreRepo.findByIdempotencyKey(request.getIdempotencyKey()).orElse(null);
        if (existente != null) {
            if (!existente.getSolicitudHash().equals(solicitudHash)) {
                throw new CierreProduccionConflictException(
                        "La clave de idempotencia ya fue utilizada con una solicitud diferente.");
            }
            return toResponse(existente, reporteRepo.findByCierreProduccion_IdOrderByIdAsc(existente.getId()));
        }

        Map<Long, CierreProduccionRequestDTO.ItemDTO> solicitados = indexarItems(request);
        List<ReporteProduccionLote> pendientes = reporteRepo.findPendientesByFechaForUpdate(
                request.getFechaProduccion(), ReporteProduccionLote.Estado.PENDIENTE);

        Set<Long> idsPendientes = pendientes.stream()
                .map(ReporteProduccionLote::getId)
                .collect(HashSet::new, Set::add, Set::addAll);
        if (pendientes.isEmpty() || !idsPendientes.equals(solicitados.keySet())) {
            throw new CierreProduccionConflictException(
                    "Los reportes pendientes cambiaron. Actualice el asistente antes de cerrar.");
        }

        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        CierreProduccion cierre = new CierreProduccion();
        cierre.setFechaProduccion(request.getFechaProduccion());
        cierre.setCerradoEn(ahora);
        cierre.setCerradoPor(actor);
        cierre.setIdempotencyKey(request.getIdempotencyKey());
        cierre.setSolicitudHash(solicitudHash);
        cierreRepo.save(cierre);

        for (ReporteProduccionLote reporte : pendientes) {
            CierreProduccionRequestDTO.ItemDTO solicitado = solicitados.get(reporte.getId());
            validarReporteParaCierre(reporte, solicitado, request);

            OrdenProduccion orden = ordenProduccionRepo.findByIdForUpdate(
                            reporte.getOrdenProduccion().getOrdenId())
                    .orElseThrow(() -> new CierreProduccionConflictException(
                            "Una orden de produccion ya no existe."));
            if (orden.getEstadoOrden() != ESTADO_FABRICACION_COMPLETADA) {
                throw new CierreProduccionConflictException(
                        "La OP " + orden.getOrdenId() + " ya no esta pendiente de ingreso a almacen.");
            }
            if (transaccionRepo.countByTipoEntidadCausanteAndIdEntidadCausante(
                    TransaccionAlmacen.TipoEntidadCausante.OP, orden.getOrdenId()) > 0) {
                throw new CierreProduccionConflictException(
                        "La OP " + orden.getOrdenId() + " ya tiene una transaccion de produccion asociada.");
            }

            Lote lote = reporte.getLote();
            if (lote.getOrdenProduccion() == null
                    || lote.getOrdenProduccion().getOrdenId() != orden.getOrdenId()
                    || (orden.getLoteAsignado() != null
                    && !orden.getLoteAsignado().equals(lote.getBatchNumber()))) {
                throw new CierreProduccionConflictException(
                        "El lote " + lote.getBatchNumber() + " ya no corresponde a la OP "
                                + orden.getOrdenId() + ".");
            }
            if (lote.getProductionDate() != null
                    && !lote.getProductionDate().equals(reporte.getFechaProduccion())) {
                throw new CierreProduccionConflictException(
                        "El lote " + lote.getBatchNumber() + " tiene una fecha de produccion diferente.");
            }

            BigDecimal cantidadConfirmada = normalizarCantidad(solicitado.getCantidadConfirmada());
            TransaccionAlmacen transaccion = crearTransaccion(orden, lote, cantidadConfirmada, actor);
            transaccionRepo.save(transaccion);

            lote.setProductionDate(reporte.getFechaProduccion());
            loteRepo.save(lote);

            reporte.setCantidadConfirmada(cantidadConfirmada);
            reporte.setMotivoCorreccion(cantidadConfirmada.compareTo(reporte.getCantidadReportada()) == 0
                    ? null
                    : normalizarTexto(solicitado.getMotivoCorreccion()));
            reporte.setCierreProduccion(cierre);
            reporte.setTransaccionAlmacen(transaccion);
            reporte.setEstado(ReporteProduccionLote.Estado.CONFIRMADO);

            orden.setEstadoOrden(ESTADO_TERMINADA);
            if (orden.getFechaFinal() == null) {
                orden.setFechaFinal(ahora);
            }
            ordenProduccionRepo.save(orden);
        }

        reporteRepo.saveAll(pendientes);
        reporteRepo.flush();
        return toResponse(cierre, pendientes);
    }

    private void validarSolicitud(CierreProduccionRequestDTO request) {
        if (request == null || request.getFechaProduccion() == null || request.getIdempotencyKey() == null) {
            throw new IllegalArgumentException("La fecha y la clave de idempotencia son obligatorias.");
        }
        if (request.getReportes() == null || request.getReportes().isEmpty()) {
            throw new IllegalArgumentException("El cierre debe contener reportes de produccion.");
        }
    }

    private Map<Long, CierreProduccionRequestDTO.ItemDTO> indexarItems(CierreProduccionRequestDTO request) {
        Map<Long, CierreProduccionRequestDTO.ItemDTO> items = new HashMap<>();
        for (CierreProduccionRequestDTO.ItemDTO item : request.getReportes()) {
            if (item == null || item.getReporteId() == null || item.getVersion() == null) {
                throw new IllegalArgumentException("Todos los reportes deben incluir identificador y version.");
            }
            if (items.put(item.getReporteId(), item) != null) {
                throw new IllegalArgumentException("El cierre contiene reportes duplicados.");
            }
        }
        return items;
    }

    private void validarReporteParaCierre(
            ReporteProduccionLote reporte,
            CierreProduccionRequestDTO.ItemDTO solicitado,
            CierreProduccionRequestDTO request
    ) {
        if (reporte.getVersion() != solicitado.getVersion()) {
            throw new CierreProduccionConflictException(
                    "El reporte " + reporte.getId() + " fue modificado. Actualice el asistente.");
        }
        if (!reporte.getFechaProduccion().equals(request.getFechaProduccion())) {
            throw new CierreProduccionConflictException("El cierre no puede mezclar fechas de produccion.");
        }

        BigDecimal cantidad = normalizarCantidad(solicitado.getCantidadConfirmada());
        if (cantidad.compareTo(reporte.getCantidadReportada()) != 0
                && normalizarTexto(solicitado.getMotivoCorreccion()) == null) {
            throw new IllegalArgumentException(
                    "El motivo de correccion es obligatorio cuando cambia la cantidad del lote "
                            + reporte.getLote().getBatchNumber() + ".");
        }
    }

    private TransaccionAlmacen crearTransaccion(
            OrdenProduccion orden,
            Lote lote,
            BigDecimal cantidad,
            User actor
    ) {
        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setTipoEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.OP);
        transaccion.setIdEntidadCausante(orden.getOrdenId());
        transaccion.setEstadoContable(TransaccionAlmacen.EstadoContable.PENDIENTE);
        transaccion.setUsuarioAprobador(actor);
        transaccion.setObservaciones("Ingreso de producto terminado confirmado para OP " + orden.getOrdenId());

        Movimiento movimiento = new Movimiento();
        movimiento.setCantidad(cantidad.doubleValue());
        movimiento.setProducto(orden.getProducto());
        movimiento.setLote(lote);
        movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.BACKFLUSH);
        movimiento.setAlmacen(Movimiento.Almacen.GENERAL);
        movimiento.setTransaccionAlmacen(transaccion);

        List<Movimiento> movimientos = new ArrayList<>();
        movimientos.add(movimiento);
        transaccion.setMovimientosTransaccion(movimientos);
        return transaccion;
    }

    private CierreProduccionResponseDTO toResponse(
            CierreProduccion cierre,
            List<ReporteProduccionLote> reportes
    ) {
        List<CierreProduccionResponseDTO.ItemDTO> items = reportes.stream()
                .map(reporte -> new CierreProduccionResponseDTO.ItemDTO(
                        reporte.getId(),
                        reporte.getOrdenProduccion().getOrdenId(),
                        reporte.getLote().getBatchNumber(),
                        reporte.getCantidadConfirmada(),
                        reporte.getTransaccionAlmacen().getTransaccionId()
                ))
                .toList();
        BigDecimal total = reportes.stream()
                .map(ReporteProduccionLote::getCantidadConfirmada)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CierreProduccionResponseDTO(
                cierre.getId(),
                cierre.getFechaProduccion(),
                cierre.getCerradoEn(),
                reportes.size(),
                total,
                items
        );
    }

    private String calcularHash(CierreProduccionRequestDTO request) {
        List<CierreProduccionRequestDTO.ItemDTO> items = new ArrayList<>(request.getReportes());
        items.sort(Comparator.comparing(CierreProduccionRequestDTO.ItemDTO::getReporteId));
        StringBuilder canonical = new StringBuilder(request.getFechaProduccion().toString());
        for (CierreProduccionRequestDTO.ItemDTO item : items) {
            canonical.append('|')
                    .append(item.getReporteId()).append(':')
                    .append(item.getVersion()).append(':')
                    .append(normalizarCantidad(item.getCantidadConfirmada()).toPlainString()).append(':')
                    .append(normalizarTexto(item.getMotivoCorreccion()) == null
                            ? ""
                            : normalizarTexto(item.getMotivoCorreccion()));
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no esta disponible.", exception);
        }
    }

    private BigDecimal normalizarCantidad(BigDecimal cantidad) {
        if (cantidad == null || cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Las cantidades confirmadas deben ser mayores que cero.");
        }
        BigDecimal normalizada = cantidad.stripTrailingZeros();
        if (Math.max(normalizada.scale(), 0) > 4) {
            throw new IllegalArgumentException("Las cantidades admiten maximo cuatro decimales.");
        }
        return normalizada;
    }

    private String normalizarTexto(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
