package exotic.app.planta.service.compras.proveedor.metricas;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.bi.dto.ProveedorMaterialOrdenHistRowDTO;
import exotic.app.planta.model.bi.dto.ProveedorMaterialRecepcionRowDTO;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.compras.proveedor.metricas.EstadoLeadTimeProveedorKPI;
import exotic.app.planta.model.compras.proveedor.metricas.LeadTimeProveedorKPI;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.compras.proveedor.metricas.LeadTimeProveedorKPIRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadTimeProveedorKpiRefreshService {

    static final int DEFAULT_WINDOW_DAYS = 365;
    private static final double EPSILON = 1e-9d;
    private static final String REASON_NO_VALID_OBSERVATIONS =
            "No existen observaciones validas de lead time en la ventana evaluada.";

    private final ProveedorRepo proveedorRepo;
    private final ItemOrdenCompraRepo itemOrdenCompraRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final LeadTimeProveedorKPIRepo leadTimeProveedorKPIRepo;

    public LeadTimeProveedorKpiRefreshSummary refreshAllProveedores() {
        EffectiveWindow window = resolveDefaultWindow();
        List<Proveedor> proveedores = proveedorRepo.findAll();

        int vigentes = 0;
        int desactualizados = 0;
        int sinInformacion = 0;
        int fallidos = 0;

        for (Proveedor proveedor : proveedores) {
            try {
                LeadTimeProveedorKpiResult result = computeKpiProveedor(proveedor, window);
                EstadoLeadTimeProveedorKPI estado = persistKpiResult(proveedor, result);
                if (estado == EstadoLeadTimeProveedorKPI.VIGENTE) {
                    vigentes++;
                } else if (estado == EstadoLeadTimeProveedorKPI.DESACTUALIZADO) {
                    desactualizados++;
                } else if (estado == EstadoLeadTimeProveedorKPI.SIN_INFORMACION) {
                    sinInformacion++;
                }
            } catch (Exception ex) {
                fallidos++;
                log.warn(
                        "LeadTimeProveedorKPI: fallo al evaluar proveedor {}: {}",
                        proveedor.getId(),
                        ex.getMessage(),
                        ex
                );
            }
        }

        LeadTimeProveedorKpiRefreshSummary summary = new LeadTimeProveedorKpiRefreshSummary(
                proveedores.size(),
                vigentes,
                desactualizados,
                sinInformacion,
                fallidos
        );
        log.info(
                "LeadTimeProveedorKPI: refresh finalizado. evaluados={}, vigentes={}, desactualizados={}, sinInformacion={}, fallidos={}",
                summary.proveedoresEvaluados(),
                summary.vigentes(),
                summary.desactualizados(),
                summary.sinInformacion(),
                summary.fallidos()
        );
        return summary;
    }

    LeadTimeProveedorKpiResult computeKpiProveedor(Proveedor proveedor, EffectiveWindow window) {
        LeadTimeProveedorFacts facts = collectLeadTimeFacts(proveedor, window);
        return calculateKpi(facts, window);
    }

    LeadTimeProveedorFacts collectLeadTimeFacts(Proveedor proveedor, EffectiveWindow window) {
        List<ProveedorMaterialOrdenHistRowDTO> orderRows = itemOrdenCompraRepo.findLeadTimeOrderHistoryByProveedor(
                proveedor.getId(),
                window.startDateTime(),
                window.endDateTime()
        );
        List<ProveedorMaterialRecepcionRowDTO> receiptRows = transaccionAlmacenRepo.findLeadTimeReceiptHistoryByProveedor(
                proveedor.getId(),
                window.startDateTime(),
                window.endDateTime(),
                TransaccionAlmacen.TipoEntidadCausante.OCM,
                Movimiento.TipoMovimiento.COMPRA
        );

        Map<ObservationKey, OrderObservation> orders = new LinkedHashMap<>();
        Set<Integer> ordenesConsideradas = new HashSet<>();
        for (ProveedorMaterialOrdenHistRowDTO row : orderRows) {
            if (row.getOrdenCompraId() == null || row.getMaterialId() == null) {
                continue;
            }
            ordenesConsideradas.add(row.getOrdenCompraId());
            ObservationKey key = new ObservationKey(row.getOrdenCompraId(), row.getMaterialId());
            orders.compute(key, (ignored, existing) -> {
                if (existing == null) {
                    return new OrderObservation(
                            row.getOrdenCompraId(),
                            row.getMaterialId(),
                            row.getFechaEmision(),
                            row.getFechaEnvioProveedor(),
                            safeDouble(row.getCantidadOrdenada())
                    );
                }
                return existing.withAdditionalOrderedQuantity(safeDouble(row.getCantidadOrdenada()));
            });
        }

        Map<ObservationKey, List<ReceiptObservation>> receiptsByObservation = new HashMap<>();
        for (ProveedorMaterialRecepcionRowDTO row : receiptRows) {
            if (row.getOrdenCompraId() == null || row.getMaterialId() == null || row.getFechaMovimiento() == null) {
                continue;
            }
            ObservationKey key = new ObservationKey(row.getOrdenCompraId(), row.getMaterialId());
            receiptsByObservation.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new ReceiptObservation(row.getFechaMovimiento(), safeDouble(row.getCantidadRecibida())));
        }

        return new LeadTimeProveedorFacts(
                new ArrayList<>(orders.values()),
                receiptsByObservation,
                ordenesConsideradas.size()
        );
    }

    LeadTimeProveedorKpiResult calculateKpi(LeadTimeProveedorFacts facts, EffectiveWindow window) {
        List<Double> leadTimes = new ArrayList<>();

        for (OrderObservation order : facts.orders()) {
            if (order.cantidadOrdenada() <= 0.0d) {
                continue;
            }

            LocalDateTime startAt = order.leadTimeStartAt();
            if (startAt == null) {
                continue;
            }

            LocalDateTime completionAt = findCompleteReceiptAt(
                    order,
                    facts.receiptsByObservation().getOrDefault(order.key(), List.of())
            );
            if (completionAt == null) {
                continue;
            }

            double leadTimeDays = daysBetween(startAt, completionAt);
            if (leadTimeDays < 0.0d) {
                continue;
            }
            leadTimes.add(leadTimeDays);
        }

        LocalDateTime evaluatedAt = AppTime.now();
        if (leadTimes.isEmpty()) {
            return LeadTimeProveedorKpiResult.noCalculable(
                    window,
                    evaluatedAt,
                    facts.ordenesConsideradas(),
                    REASON_NO_VALID_OBSERVATIONS
            );
        }

        leadTimes.sort(Double::compareTo);
        return LeadTimeProveedorKpiResult.calculable(
                window,
                evaluatedAt,
                round4(median(leadTimes)),
                leadTimes.size(),
                facts.ordenesConsideradas()
        );
    }

    EstadoLeadTimeProveedorKPI persistKpiResult(Proveedor proveedor, LeadTimeProveedorKpiResult result) {
        LeadTimeProveedorKPI kpi = leadTimeProveedorKPIRepo.findByProveedor_Pk(proveedor.getPk()).orElse(null);

        if (result.calculable()) {
            if (kpi == null) {
                kpi = new LeadTimeProveedorKPI();
                kpi.setProveedor(proveedor);
            }
            kpi.setFechaCorte(result.window().fechaCorte());
            kpi.setVentanaDias(result.window().ventanaDias());
            kpi.setLeadTimeMedianoDias(result.leadTimeMedianoDias());
            kpi.setObservaciones(result.observaciones());
            kpi.setOrdenesConsideradas(result.ordenesConsideradas());
            kpi.setCalculadoEn(result.evaluatedAt());
            kpi.setEstado(EstadoLeadTimeProveedorKPI.VIGENTE);
            kpi.setMotivoEstado(null);
            kpi.setUltimaEvaluacionEn(result.evaluatedAt());
            kpi.setUltimaFechaCorteEvaluada(result.window().fechaCorte());
            leadTimeProveedorKPIRepo.save(kpi);
            return EstadoLeadTimeProveedorKPI.VIGENTE;
        }

        if (kpi == null) {
            kpi = new LeadTimeProveedorKPI();
            kpi.setProveedor(proveedor);
            kpi.setFechaCorte(result.window().fechaCorte());
            kpi.setVentanaDias(result.window().ventanaDias());
            kpi.setLeadTimeMedianoDias(null);
            kpi.setObservaciones(0);
            kpi.setOrdenesConsideradas(0);
            kpi.setCalculadoEn(null);
            kpi.setEstado(EstadoLeadTimeProveedorKPI.SIN_INFORMACION);
            kpi.setMotivoEstado(result.reason());
            kpi.setUltimaEvaluacionEn(result.evaluatedAt());
            kpi.setUltimaFechaCorteEvaluada(result.window().fechaCorte());
            leadTimeProveedorKPIRepo.save(kpi);
            return EstadoLeadTimeProveedorKPI.SIN_INFORMACION;
        }

        if (kpi.getLeadTimeMedianoDias() != null) {
            kpi.setEstado(EstadoLeadTimeProveedorKPI.DESACTUALIZADO);
            kpi.setMotivoEstado(result.reason());
            kpi.setUltimaEvaluacionEn(result.evaluatedAt());
            kpi.setUltimaFechaCorteEvaluada(result.window().fechaCorte());
            leadTimeProveedorKPIRepo.save(kpi);
            return EstadoLeadTimeProveedorKPI.DESACTUALIZADO;
        }

        kpi.setFechaCorte(result.window().fechaCorte());
        kpi.setVentanaDias(result.window().ventanaDias());
        kpi.setObservaciones(0);
        kpi.setOrdenesConsideradas(0);
        kpi.setCalculadoEn(null);
        kpi.setEstado(EstadoLeadTimeProveedorKPI.SIN_INFORMACION);
        kpi.setMotivoEstado(result.reason());
        kpi.setUltimaEvaluacionEn(result.evaluatedAt());
        kpi.setUltimaFechaCorteEvaluada(result.window().fechaCorte());
        leadTimeProveedorKPIRepo.save(kpi);
        return EstadoLeadTimeProveedorKPI.SIN_INFORMACION;
    }

    private EffectiveWindow resolveDefaultWindow() {
        LocalDate fechaCorte = AppTime.today();
        LocalDate startDate = fechaCorte.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        return new EffectiveWindow(
                fechaCorte,
                DEFAULT_WINDOW_DAYS,
                startDate.atStartOfDay(),
                fechaCorte.atTime(LocalTime.MAX)
        );
    }

    private LocalDateTime findCompleteReceiptAt(OrderObservation order, List<ReceiptObservation> receipts) {
        if (receipts.isEmpty()) {
            return null;
        }

        double cumulativeReceived = 0.0d;
        List<ReceiptObservation> sorted = new ArrayList<>(receipts);
        sorted.sort(Comparator.comparing(ReceiptObservation::receiptAt));
        for (ReceiptObservation receipt : sorted) {
            cumulativeReceived += receipt.quantity();
            if (cumulativeReceived + EPSILON >= order.cantidadOrdenada()) {
                return receipt.receiptAt();
            }
        }
        return null;
    }

    private static double daysBetween(LocalDateTime startAt, LocalDateTime endAt) {
        return Duration.between(startAt, endAt).toMinutes() / 1440.0d;
    }

    private static double median(List<Double> sorted) {
        int size = sorted.size();
        if (size == 0) {
            return 0.0d;
        }
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0d;
    }

    private static Double round4(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 10000.0d) / 10000.0d;
    }

    private static double safeDouble(Number value) {
        return value != null ? value.doubleValue() : 0.0d;
    }

    public record LeadTimeProveedorKpiRefreshSummary(
            int proveedoresEvaluados,
            int vigentes,
            int desactualizados,
            int sinInformacion,
            int fallidos
    ) {
    }

    record EffectiveWindow(
            LocalDate fechaCorte,
            int ventanaDias,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
    }

    record LeadTimeProveedorFacts(
            List<OrderObservation> orders,
            Map<ObservationKey, List<ReceiptObservation>> receiptsByObservation,
            int ordenesConsideradas
    ) {
    }

    record LeadTimeProveedorKpiResult(
            boolean calculable,
            EffectiveWindow window,
            LocalDateTime evaluatedAt,
            Double leadTimeMedianoDias,
            int observaciones,
            int ordenesConsideradas,
            String reason
    ) {
        static LeadTimeProveedorKpiResult calculable(
                EffectiveWindow window,
                LocalDateTime evaluatedAt,
                Double leadTimeMedianoDias,
                int observaciones,
                int ordenesConsideradas
        ) {
            return new LeadTimeProveedorKpiResult(
                    true,
                    window,
                    evaluatedAt,
                    leadTimeMedianoDias,
                    observaciones,
                    ordenesConsideradas,
                    null
            );
        }

        static LeadTimeProveedorKpiResult noCalculable(
                EffectiveWindow window,
                LocalDateTime evaluatedAt,
                int ordenesConsideradas,
                String reason
        ) {
            return new LeadTimeProveedorKpiResult(
                    false,
                    window,
                    evaluatedAt,
                    null,
                    0,
                    ordenesConsideradas,
                    reason
            );
        }
    }

    private record ObservationKey(Integer ordenCompraId, String materialId) {
    }

    private record OrderObservation(
            Integer ordenCompraId,
            String materialId,
            LocalDateTime fechaEmision,
            LocalDateTime fechaEnvioProveedor,
            double cantidadOrdenada
    ) {
        private ObservationKey key() {
            return new ObservationKey(ordenCompraId, materialId);
        }

        private LocalDateTime leadTimeStartAt() {
            return fechaEnvioProveedor != null ? fechaEnvioProveedor : fechaEmision;
        }

        private OrderObservation withAdditionalOrderedQuantity(double extraQuantity) {
            return new OrderObservation(
                    ordenCompraId,
                    materialId,
                    fechaEmision,
                    fechaEnvioProveedor,
                    cantidadOrdenada + extraQuantity
            );
        }
    }

    private record ReceiptObservation(LocalDateTime receiptAt, double quantity) {
    }
}
