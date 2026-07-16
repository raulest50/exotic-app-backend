package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.ReporteProduccionLote;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.dto.ReporteProduccionPendientesDTO;
import exotic.app.planta.model.produccion.dto.ReporteProduccionPendientesResumenDTO;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.ReporteProduccionLoteRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ReporteProduccionLoteService {

    private static final int ESTADO_FABRICACION_COMPLETADA = 3;
    private static final int ESTADO_TERMINADA = 2;
    private static final int ESTADO_CANCELADA = -1;

    private final ReporteProduccionLoteRepo reporteRepo;
    private final LoteRepo loteRepo;
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final ProduccionCierreLockService cierreLockService;
    private final Clock applicationClock;

    public ReporteProduccionLote registrarPendiente(
            SeguimientoOrdenArea seguimiento,
            User actor,
            BigDecimal cantidadProducida
    ) {
        BigDecimal cantidad = validarCantidad(cantidadProducida);
        OrdenProduccion orden = seguimiento.getOrdenProduccion();
        LocalDate fechaProduccion = LocalDate.now(applicationClock);
        cierreLockService.lockFecha(fechaProduccion);

        if (orden.getEstadoOrden() == ESTADO_CANCELADA || orden.getEstadoOrden() == ESTADO_TERMINADA) {
            throw new IllegalStateException("La orden no admite nuevos reportes de produccion.");
        }
        if (orden.getEstadoOrden() == ESTADO_FABRICACION_COMPLETADA) {
            throw new IllegalStateException("La orden ya tiene fabricacion completada pendiente de cierre.");
        }
        if (reporteRepo.existsByOrdenProduccion_OrdenIdAndEstadoNot(
                orden.getOrdenId(), ReporteProduccionLote.Estado.ANULADO)) {
            throw new IllegalStateException("La orden ya tiene un reporte de produccion activo.");
        }

        Lote lote = resolverLoteUnico(orden);
        LocalDateTime ahora = LocalDateTime.now(applicationClock);

        ReporteProduccionLote reporte = new ReporteProduccionLote();
        reporte.setOrdenProduccion(orden);
        reporte.setLote(lote);
        reporte.setSeguimientoOrdenArea(seguimiento);
        reporte.setCantidadReportada(cantidad);
        reporte.setFechaProduccion(fechaProduccion);
        reporte.setReportadoEn(ahora);
        reporte.setReportadoPor(actor);
        reporte.setEstado(ReporteProduccionLote.Estado.PENDIENTE);
        reporte.setEstadoOrdenAnterior(orden.getEstadoOrden());

        orden.setEstadoOrden(ESTADO_FABRICACION_COMPLETADA);
        orden.setFechaFinal(ahora);
        ordenProduccionRepo.save(orden);
        return reporteRepo.save(reporte);
    }

    public void anularPendientePorSeguimiento(SeguimientoOrdenArea seguimiento, User actor, String motivo) {
        ReporteProduccionLote reporte = reporteRepo.findFirstBySeguimientoOrdenArea_IdAndEstado(
                        seguimiento.getId(), ReporteProduccionLote.Estado.PENDIENTE)
                .orElse(null);
        if (reporte == null) {
            if (reporteRepo.existsBySeguimientoOrdenArea_IdAndEstado(
                    seguimiento.getId(), ReporteProduccionLote.Estado.CONFIRMADO)) {
                throw new CierreProduccionConflictException(
                        "El reporte final ya fue confirmado; la correccion requiere un ajuste de inventario independiente.");
            }
            return;
        }

        OrdenProduccion orden = reporte.getOrdenProduccion();
        if (orden.getEstadoOrden() != ESTADO_FABRICACION_COMPLETADA) {
            throw new CierreProduccionConflictException(
                    "La orden cambio de estado y el reporte pendiente no puede anularse.");
        }

        reporte.setEstado(ReporteProduccionLote.Estado.ANULADO);
        reporte.setAnuladoEn(LocalDateTime.now(applicationClock));
        reporte.setAnuladoPor(actor);
        reporte.setMotivoAnulacion(normalizarTexto(motivo));

        orden.setEstadoOrden(reporte.getEstadoOrdenAnterior());
        orden.setFechaFinal(null);
        ordenProduccionRepo.save(orden);
        reporteRepo.save(reporte);
    }

    @Transactional(readOnly = true)
    public ReporteProduccionPendientesResumenDTO resumirPendientes() {
        LocalDate hoy = LocalDate.now(applicationClock);
        List<ReporteProduccionPendientesResumenDTO.FechaPendienteDTO> fechas = reporteRepo
                .resumirPendientesPorFecha(ReporteProduccionLote.Estado.PENDIENTE)
                .stream()
                .map(row -> new ReporteProduccionPendientesResumenDTO.FechaPendienteDTO(
                        row.getFechaProduccion(),
                        row.getCantidadReportes(),
                        row.getTotalUnidades(),
                        row.getFechaProduccion().isBefore(hoy)
                ))
                .toList();

        long pendientesHoy = fechas.stream()
                .filter(fecha -> fecha.getFechaProduccion().equals(hoy))
                .mapToLong(ReporteProduccionPendientesResumenDTO.FechaPendienteDTO::getCantidadReportes)
                .sum();
        long pendientesVencidos = fechas.stream()
                .filter(ReporteProduccionPendientesResumenDTO.FechaPendienteDTO::isVencida)
                .mapToLong(ReporteProduccionPendientesResumenDTO.FechaPendienteDTO::getCantidadReportes)
                .sum();

        return new ReporteProduccionPendientesResumenDTO(
                hoy,
                pendientesHoy,
                pendientesVencidos,
                fechas
        );
    }

    @Transactional(readOnly = true)
    public ReporteProduccionPendientesDTO consultarPendientes(LocalDate fechaProduccion) {
        if (fechaProduccion == null) {
            throw new IllegalArgumentException("Se requiere la fecha de produccion.");
        }

        List<ReporteProduccionPendientesDTO.ItemDTO> items = reporteRepo
                .findByFechaProduccionAndEstadoOrderByReportadoEnAscIdAsc(
                        fechaProduccion, ReporteProduccionLote.Estado.PENDIENTE)
                .stream()
                .map(this::toPendienteDTO)
                .toList();
        return new ReporteProduccionPendientesDTO(fechaProduccion, items);
    }

    private ReporteProduccionPendientesDTO.ItemDTO toPendienteDTO(ReporteProduccionLote reporte) {
        OrdenProduccion orden = reporte.getOrdenProduccion();
        return new ReporteProduccionPendientesDTO.ItemDTO(
                reporte.getId(),
                reporte.getVersion(),
                orden.getOrdenId(),
                reporte.getLote().getBatchNumber(),
                orden.getProducto().getProductoId(),
                orden.getProducto().getNombre(),
                orden.getProducto().getTipoUnidades(),
                BigDecimal.valueOf(orden.getCantidadProducir()),
                reporte.getCantidadReportada(),
                reporte.getReportadoEn(),
                reporte.getReportadoPor().getNombreCompleto() != null
                        ? reporte.getReportadoPor().getNombreCompleto()
                        : reporte.getReportadoPor().getUsername()
        );
    }

    private Lote resolverLoteUnico(OrdenProduccion orden) {
        List<Lote> lotes = loteRepo.findByOrdenProduccion_OrdenId(orden.getOrdenId()).stream()
                .filter(lote -> orden.getLoteAsignado() == null
                        || orden.getLoteAsignado().equals(lote.getBatchNumber()))
                .toList();
        if (lotes.size() != 1) {
            throw new IllegalStateException(
                    "La orden debe tener exactamente un lote productivo asociado para reportar terminado.");
        }
        return lotes.get(0);
    }

    private BigDecimal validarCantidad(BigDecimal cantidad) {
        if (cantidad == null || cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad producida debe ser mayor que cero.");
        }
        BigDecimal normalizada = cantidad.stripTrailingZeros();
        if (Math.max(normalizada.scale(), 0) > 4) {
            throw new IllegalArgumentException("La cantidad producida admite maximo cuatro decimales.");
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
