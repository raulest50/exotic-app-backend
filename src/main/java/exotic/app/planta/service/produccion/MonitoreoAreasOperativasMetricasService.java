package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonitoreoAreasOperativasMetricasService {

    private final SeguimientoOrdenAreaService seguimientoOrdenAreaService;
    private final SeguimientoOrdenAreaRepo seguimientoOrdenAreaRepo;
    private final SeguimientoOrdenAreaEventoRepo seguimientoOrdenAreaEventoRepo;
    private final AreaProduccionRepo areaProduccionRepo;

    public AreaOperativaMetricasDTO getMetricasArea(
            int areaId,
            String modo,
            LocalDate fecha,
            LocalDate fechaDesde,
            LocalDate fechaHasta
    ) {
        MetricMode metricMode = MetricMode.fromRaw(modo);

        return switch (metricMode) {
            case ACTUAL -> buildMetricasActuales(areaId, fecha);
            case HISTORICO -> buildMetricasHistoricas(areaId, null, null, metricMode);
            case RANGO -> buildMetricasRango(areaId, fechaDesde, fechaHasta);
        };
    }

    private AreaOperativaMetricasDTO buildMetricasActuales(int areaId, LocalDate fecha) {
        SeguimientoOrdenAreaService.AreaOperativaTableroDTO tablero =
                seguimientoOrdenAreaService.getTableroAreaPorFecha(areaId, fecha);

        AreaOperativaMetricasDTO dto = new AreaOperativaMetricasDTO();
        dto.setAreaId(areaId);
        dto.setModo(MetricMode.ACTUAL.getApiValue());
        dto.setFecha(tablero.getFechaConsulta());
        dto.setPromedioMinutosEspera(tablero.getPromedioMinutosEspera());
        dto.setPromedioMinutosEnProceso(tablero.getPromedioMinutosEnProceso());
        dto.setMuestrasEspera(tablero.getEspera().size());
        dto.setMuestrasEnProceso(tablero.getEnProceso().size());
        return dto;
    }

    private AreaOperativaMetricasDTO buildMetricasHistoricas(
            int areaId,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            MetricMode mode
    ) {
        areaProduccionRepo.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Area operativa no encontrada: " + areaId));

        List<SeguimientoOrdenArea> seguimientos = seguimientoOrdenAreaRepo.findTableroByAreaId(areaId);
        Map<Long, List<SeguimientoOrdenAreaEvento>> eventosPorSeguimiento = loadEventosPorSeguimiento(seguimientos);

        LocalDateTime inicioRango = fechaDesde != null ? fechaDesde.atStartOfDay() : null;
        LocalDateTime finRango = fechaHasta != null ? LocalDateTime.of(fechaHasta, LocalTime.MAX) : null;

        List<Long> muestrasEspera = collectClosedStateDurations(
                eventosPorSeguimiento.values(),
                EstadoSeguimientoOrdenArea.ESPERA.getCode(),
                inicioRango,
                finRango
        );
        List<Long> muestrasEnProceso = collectClosedStateDurations(
                eventosPorSeguimiento.values(),
                EstadoSeguimientoOrdenArea.EN_PROCESO.getCode(),
                inicioRango,
                finRango
        );

        AreaOperativaMetricasDTO dto = new AreaOperativaMetricasDTO();
        dto.setAreaId(areaId);
        dto.setModo(mode.getApiValue());
        dto.setFechaDesde(fechaDesde);
        dto.setFechaHasta(fechaHasta);
        dto.setPromedioMinutosEspera(averageMinutes(muestrasEspera));
        dto.setPromedioMinutosEnProceso(averageMinutes(muestrasEnProceso));
        dto.setMuestrasEspera(muestrasEspera.size());
        dto.setMuestrasEnProceso(muestrasEnProceso.size());
        return dto;
    }

    private AreaOperativaMetricasDTO buildMetricasRango(int areaId, LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaDesde == null || fechaHasta == null) {
            throw new IllegalArgumentException("fechaDesde y fechaHasta son obligatorias para modo rango.");
        }
        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException("fechaDesde no puede ser posterior a fechaHasta.");
        }

        return buildMetricasHistoricas(areaId, fechaDesde, fechaHasta, MetricMode.RANGO);
    }

    private Map<Long, List<SeguimientoOrdenAreaEvento>> loadEventosPorSeguimiento(List<SeguimientoOrdenArea> seguimientos) {
        List<Long> seguimientoIds = seguimientos.stream()
                .map(SeguimientoOrdenArea::getId)
                .toList();

        if (seguimientoIds.isEmpty()) {
            return Map.of();
        }

        return seguimientoOrdenAreaEventoRepo.findBySeguimientoOrdenArea_IdInOrderByFechaEventoAscIdAsc(seguimientoIds)
                .stream()
                .collect(Collectors.groupingBy(
                        evento -> evento.getSeguimientoOrdenArea().getId(),
                        HashMap::new,
                        Collectors.toList()
                ));
    }

    private List<Long> collectClosedStateDurations(
            Collection<List<SeguimientoOrdenAreaEvento>> groupedEvents,
            int targetStateCode,
            LocalDateTime rangoInicio,
            LocalDateTime rangoFin
    ) {
        List<Long> samples = new ArrayList<>();

        for (List<SeguimientoOrdenAreaEvento> events : groupedEvents) {
            for (ClosedStateInterval interval : resolveClosedIntervals(events, targetStateCode)) {
                if (!belongsToRange(interval.getClosedAt(), rangoInicio, rangoFin)) {
                    continue;
                }
                samples.add(interval.getDurationMinutes());
            }
        }

        return samples;
    }

    private List<ClosedStateInterval> resolveClosedIntervals(List<SeguimientoOrdenAreaEvento> events, int targetStateCode) {
        List<ClosedStateInterval> intervals = new ArrayList<>();

        for (int i = 0; i < events.size(); i++) {
            SeguimientoOrdenAreaEvento entry = events.get(i);
            if (entry.getEstadoDestino() != targetStateCode) {
                continue;
            }

            for (int j = i + 1; j < events.size(); j++) {
                SeguimientoOrdenAreaEvento exit = events.get(j);
                if (!Objects.equals(exit.getEstadoOrigen(), targetStateCode)) {
                    continue;
                }

                long minutes = Math.max(Duration.between(entry.getFechaEvento(), exit.getFechaEvento()).toMinutes(), 0);
                intervals.add(new ClosedStateInterval(minutes, exit.getFechaEvento()));
                break;
            }
        }

        return intervals;
    }

    private boolean belongsToRange(LocalDateTime closedAt, LocalDateTime rangoInicio, LocalDateTime rangoFin) {
        if (closedAt == null) {
            return false;
        }
        if (rangoInicio != null && closedAt.isBefore(rangoInicio)) {
            return false;
        }
        if (rangoFin != null && closedAt.isAfter(rangoFin)) {
            return false;
        }
        return true;
    }

    private Double averageMinutes(List<Long> samples) {
        var average = samples.stream()
                .mapToLong(Long::longValue)
                .average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    private enum MetricMode {
        ACTUAL("actual"),
        HISTORICO("historico"),
        RANGO("rango");

        private final String apiValue;

        MetricMode(String apiValue) {
            this.apiValue = apiValue;
        }

        public String getApiValue() {
            return apiValue;
        }

        public static MetricMode fromRaw(String raw) {
            if (raw == null || raw.isBlank()) {
                return ACTUAL;
            }

            return switch (raw.trim().toLowerCase()) {
                case "actual" -> ACTUAL;
                case "historico" -> HISTORICO;
                case "rango" -> RANGO;
                default -> throw new IllegalArgumentException("Modo de métricas no válido: " + raw);
            };
        }
    }

    @Data
    public static class AreaOperativaMetricasDTO {
        private int areaId;
        private String modo;
        private LocalDate fecha;
        private LocalDate fechaDesde;
        private LocalDate fechaHasta;
        private Double promedioMinutosEspera;
        private Double promedioMinutosEnProceso;
        private int muestrasEspera;
        private int muestrasEnProceso;
    }

    @Data
    private static class ClosedStateInterval {
        private final long durationMinutes;
        private final LocalDateTime closedAt;
    }
}
