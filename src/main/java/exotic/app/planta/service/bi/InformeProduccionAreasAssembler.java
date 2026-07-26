package exotic.app.planta.service.bi;

import exotic.app.planta.model.bi.dto.InformeGlobalProduccionDTO;
import exotic.app.planta.model.organizacion.AreaOperativaCategoriaUnidadMedida;
import exotic.app.planta.model.organizacion.UnidadMedidaAreaOperativa;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.ReporteProduccionLote;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.repo.producto.procesos.AreaOperativaCategoriaUnidadMedidaRepo;
import exotic.app.planta.repo.produccion.ReporteProduccionLoteRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class InformeProduccionAreasAssembler {

    private static final int ALMACEN_GENERAL_AREA_ID = -1;
    private static final String SOURCE_REPORTED = "REPORTADA";
    private static final String SOURCE_STANDARD = "ESTANDAR";
    private static final String SOURCE_BATCHES = "LOTES";

    private final SeguimientoOrdenAreaRepo seguimientoRepo;
    private final SeguimientoOrdenAreaEventoRepo eventoRepo;
    private final ReporteProduccionLoteRepo reporteRepo;
    private final AreaOperativaCategoriaUnidadMedidaRepo unidadCategoriaRepo;

    @Transactional(
            readOnly = true,
            propagation = Propagation.REQUIRES_NEW,
            timeout = 5
    )
    public InformeGlobalProduccionDTO.AnaliticaAreasDTO construir(
            LocalDate fechaDesde,
            LocalDate fechaHasta
    ) {
        int diasRango = Math.toIntExact(ChronoUnit.DAYS.between(fechaDesde, fechaHasta) + 1);
        LocalDate fechaHastaAnterior = fechaDesde.minusDays(1);
        LocalDate fechaDesdeAnterior = fechaDesde.minusDays(diasRango);
        LocalDateTime inicioHorizonte = fechaDesdeAnterior.atStartOfDay();
        LocalDateTime corte = LocalDateTime.of(fechaHasta, LocalTime.MAX);

        List<SeguimientoOrdenArea> seguimientos = seguimientoRepo.findAnaliticaAreasByHorizon(
                ALMACEN_GENERAL_AREA_ID,
                inicioHorizonte,
                corte
        );
        if (seguimientos.isEmpty()) {
            return disponibleSinAreas(fechaDesdeAnterior, fechaHastaAnterior);
        }

        List<Long> seguimientoIds = seguimientos.stream()
                .map(SeguimientoOrdenArea::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<SeguimientoOrdenAreaEvento>> eventosPorSeguimiento =
                cargarEventos(seguimientoIds);
        Map<Long, ReporteProduccionLote> reportesPorSeguimiento =
                cargarReportes(seguimientoIds);

        Set<Integer> areaIds = seguimientos.stream()
                .map(SeguimientoOrdenArea::getAreaOperativa)
                .filter(Objects::nonNull)
                .map(area -> area.getAreaId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<AreaCategoriaKey, AreaOperativaCategoriaUnidadMedida> unidadesPorAreaCategoria =
                cargarUnidades(areaIds);

        Map<Integer, AreaAccumulator> areas = new LinkedHashMap<>();
        for (SeguimientoOrdenArea seguimiento : seguimientos) {
            if (seguimiento.getId() == null || seguimiento.getAreaOperativa() == null) {
                continue;
            }
            int areaId = seguimiento.getAreaOperativa().getAreaId();
            AreaAccumulator area = areas.computeIfAbsent(
                    areaId,
                    ignored -> new AreaAccumulator(
                            areaId,
                            seguimiento.getAreaOperativa().getNombre(),
                            fechaDesde,
                            fechaHasta,
                            fechaDesdeAnterior,
                            fechaHastaAnterior
                    )
            );

            List<SeguimientoOrdenAreaEvento> eventos = eventosPorSeguimiento
                    .getOrDefault(seguimiento.getId(), List.of())
                    .stream()
                    .filter(evento -> !evento.getFechaEvento().isAfter(corte))
                    .toList();
            acumularSeguimiento(
                    area,
                    seguimiento,
                    eventos,
                    reportesPorSeguimiento.get(seguimiento.getId()),
                    unidadesPorAreaCategoria
            );
        }

        List<InformeGlobalProduccionDTO.AreaOperativaAnaliticaDTO> areaDtos = areas.values()
                .stream()
                .filter(AreaAccumulator::hasAnyActivity)
                .map(area -> area.toDto(diasRango))
                .sorted(areaComparator())
                .toList();

        return InformeGlobalProduccionDTO.AnaliticaAreasDTO.builder()
                .disponible(true)
                .mensaje(areaDtos.isEmpty()
                        ? "No hay actividad por areas operativas en el periodo consultado."
                        : null)
                .fechaDesdePeriodoAnterior(fechaDesdeAnterior)
                .fechaHastaPeriodoAnterior(fechaHastaAnterior)
                .areas(areaDtos)
                .build();
    }

    private Map<Long, List<SeguimientoOrdenAreaEvento>> cargarEventos(List<Long> seguimientoIds) {
        if (seguimientoIds.isEmpty()) {
            return Map.of();
        }
        return eventoRepo
                .findBySeguimientoOrdenArea_IdInOrderByFechaEventoAscIdAsc(seguimientoIds)
                .stream()
                .collect(Collectors.groupingBy(
                        evento -> evento.getSeguimientoOrdenArea().getId(),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), this::filterUnrevertedEvents)
                ));
    }

    private Map<Long, ReporteProduccionLote> cargarReportes(List<Long> seguimientoIds) {
        if (seguimientoIds.isEmpty()) {
            return Map.of();
        }
        return reporteRepo.findBySeguimientoOrdenArea_IdInAndEstadoNot(
                        seguimientoIds,
                        ReporteProduccionLote.Estado.ANULADO
                )
                .stream()
                .filter(reporte -> reporte.getSeguimientoOrdenArea() != null)
                .collect(Collectors.toMap(
                        reporte -> reporte.getSeguimientoOrdenArea().getId(),
                        Function.identity(),
                        this::latestReport,
                        HashMap::new
                ));
    }

    private ReporteProduccionLote latestReport(
            ReporteProduccionLote left,
            ReporteProduccionLote right
    ) {
        Comparator<ReporteProduccionLote> comparator = Comparator
                .comparing(
                        ReporteProduccionLote::getReportadoEn,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                )
                .thenComparing(
                        ReporteProduccionLote::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                );
        return comparator.compare(left, right) >= 0 ? left : right;
    }

    private Map<AreaCategoriaKey, AreaOperativaCategoriaUnidadMedida> cargarUnidades(
            Collection<Integer> areaIds
    ) {
        if (areaIds.isEmpty()) {
            return Map.of();
        }
        return unidadCategoriaRepo.findAnaliticaByAreaIds(areaIds)
                .stream()
                .collect(Collectors.toMap(
                        association -> new AreaCategoriaKey(
                                association.getAreaOperativa().getAreaId(),
                                association.getCategoria().getCategoriaId()
                        ),
                        Function.identity(),
                        (left, right) -> left,
                        HashMap::new
                ));
    }

    private void acumularSeguimiento(
            AreaAccumulator area,
            SeguimientoOrdenArea seguimiento,
            List<SeguimientoOrdenAreaEvento> eventos,
            ReporteProduccionLote reporte,
            Map<AreaCategoriaKey, AreaOperativaCategoriaUnidadMedida> unidadesPorAreaCategoria
    ) {
        Integer categoriaId = resolveCategoriaId(seguimiento);
        AreaOperativaCategoriaUnidadMedida unidadConfigurada = categoriaId == null
                ? null
                : unidadesPorAreaCategoria.get(new AreaCategoriaKey(area.areaId(), categoriaId));

        for (SeguimientoOrdenAreaEvento evento : eventos) {
            PeriodAccumulator periodo = area.periodFor(evento.getFechaEvento().toLocalDate());
            if (periodo == null) {
                continue;
            }
            if (Objects.equals(
                    evento.getEstadoOrigen(),
                    EstadoSeguimientoOrdenArea.COLA.getCode())
                    && evento.getEstadoDestino() == EstadoSeguimientoOrdenArea.ESPERA.getCode()) {
                periodo.addArrival(evento.getFechaEvento().toLocalDate());
            }
            if (evento.getEstadoDestino() == EstadoSeguimientoOrdenArea.COMPLETADO.getCode()) {
                periodo.addCompletion(evento.getFechaEvento().toLocalDate());
                addProduction(periodo, seguimiento, reporte, unidadConfigurada);
            }
        }

        addClosedDurations(area, eventos, EstadoSeguimientoOrdenArea.ESPERA.getCode());
        addClosedDurations(area, eventos, EstadoSeguimientoOrdenArea.EN_PROCESO.getCode());
        addBacklogByDay(area.current(), eventos);
        addBacklogByDay(area.previous(), eventos);
    }

    private Integer resolveCategoriaId(SeguimientoOrdenArea seguimiento) {
        if (seguimiento.getOrdenProduccion() == null) {
            return null;
        }
        Producto producto = seguimiento.getOrdenProduccion().getProducto();
        if (producto instanceof Terminado terminado && terminado.getCategoria() != null) {
            return terminado.getCategoria().getCategoriaId();
        }
        return null;
    }

    private void addProduction(
            PeriodAccumulator periodo,
            SeguimientoOrdenArea seguimiento,
            ReporteProduccionLote reporte,
            AreaOperativaCategoriaUnidadMedida unidadConfigurada
    ) {
        if (reporte != null) {
            BigDecimal cantidad = reporte.getCantidadConfirmada() != null
                    ? reporte.getCantidadConfirmada()
                    : reporte.getCantidadReportada();
            if (cantidad != null && cantidad.compareTo(BigDecimal.ZERO) > 0) {
                Producto producto = seguimiento.getOrdenProduccion() != null
                        ? seguimiento.getOrdenProduccion().getProducto()
                        : null;
                String unidad = producto != null && producto.getTipoUnidades() != null
                        && !producto.getTipoUnidades().isBlank()
                        ? producto.getTipoUnidades().trim().toUpperCase()
                        : "U";
                periodo.addProduction(
                        new ProductionKey(SOURCE_REPORTED, unidad, null),
                        cantidad.doubleValue(),
                        null
                );
                periodo.incrementRepresentedCompletion();
                return;
            }
        }

        if (unidadConfigurada != null && unidadConfigurada.getUnidadMedida() != null) {
            UnidadMedidaAreaOperativa unidad = unidadConfigurada.getUnidadMedida();
            BigDecimal factorLote = unidadConfigurada.getFactorLote();
            if (factorLote != null && factorLote.compareTo(BigDecimal.ZERO) > 0) {
                double cantidad = factorLote.doubleValue();
                double equivalente = factorLote
                        .multiply(unidad.getRelacionEstandar())
                        .doubleValue();
                periodo.addProduction(
                        new ProductionKey(
                                SOURCE_STANDARD,
                                unidad.getNombre(),
                                unidad.getUnidadRelacion().name()
                        ),
                        cantidad,
                        equivalente
                );
                periodo.incrementRepresentedCompletion();
                return;
            }
        }

        periodo.addProduction(
                new ProductionKey(SOURCE_BATCHES, "lotes", null),
                1d,
                null
        );
    }

    private void addClosedDurations(
            AreaAccumulator area,
            List<SeguimientoOrdenAreaEvento> eventos,
            int targetState
    ) {
        for (int entryIndex = 0; entryIndex < eventos.size(); entryIndex++) {
            SeguimientoOrdenAreaEvento entry = eventos.get(entryIndex);
            if (entry.getEstadoDestino() != targetState) {
                continue;
            }
            for (int exitIndex = entryIndex + 1; exitIndex < eventos.size(); exitIndex++) {
                SeguimientoOrdenAreaEvento exit = eventos.get(exitIndex);
                if (!Objects.equals(exit.getEstadoOrigen(), targetState)) {
                    continue;
                }
                long minutes = Math.max(
                        Duration.between(entry.getFechaEvento(), exit.getFechaEvento()).toMinutes(),
                        0
                );
                PeriodAccumulator period = area.periodFor(exit.getFechaEvento().toLocalDate());
                if (period != null) {
                    period.addDuration(targetState, minutes);
                }
                break;
            }
        }
    }

    private void addBacklogByDay(
            PeriodAccumulator period,
            List<SeguimientoOrdenAreaEvento> eventos
    ) {
        for (DailyAccumulator day : period.days()) {
            Integer state = resolveStateAt(eventos, LocalDateTime.of(day.date(), LocalTime.MAX));
            if (state != null && (
                    state == EstadoSeguimientoOrdenArea.ESPERA.getCode()
                            || state == EstadoSeguimientoOrdenArea.EN_PROCESO.getCode())) {
                day.incrementBacklog();
            }
        }
    }

    private Integer resolveStateAt(
            List<SeguimientoOrdenAreaEvento> eventos,
            LocalDateTime cutoff
    ) {
        Integer state = null;
        for (SeguimientoOrdenAreaEvento evento : eventos) {
            if (evento.getFechaEvento().isAfter(cutoff)) {
                break;
            }
            state = evento.getEstadoDestino();
        }
        return state;
    }

    private List<SeguimientoOrdenAreaEvento> filterUnrevertedEvents(
            List<SeguimientoOrdenAreaEvento> eventos
    ) {
        Set<Long> revertedIds = eventos.stream()
                .map(SeguimientoOrdenAreaEvento::getEventoRevertido)
                .filter(Objects::nonNull)
                .map(SeguimientoOrdenAreaEvento::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return eventos.stream()
                .filter(evento -> evento.getId() == null || !revertedIds.contains(evento.getId()))
                .sorted(Comparator
                        .comparing(SeguimientoOrdenAreaEvento::getFechaEvento)
                        .thenComparing(
                                SeguimientoOrdenAreaEvento::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                .toList();
    }

    private Comparator<InformeGlobalProduccionDTO.AreaOperativaAnaliticaDTO> areaComparator() {
        return Comparator
                .comparingInt((InformeGlobalProduccionDTO.AreaOperativaAnaliticaDTO area) ->
                        signalPriority(area.getEstado()))
                .thenComparing(
                        area -> area.getActual().getDiasBacklog(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        InformeGlobalProduccionDTO.AreaOperativaAnaliticaDTO::getAreaNombre,
                        String.CASE_INSENSITIVE_ORDER
                );
    }

    private int signalPriority(String state) {
        return switch (state) {
            case "POSIBLE_CUELLO" -> 0;
            case "OBSERVACION" -> 1;
            case "ESTABLE" -> 2;
            default -> 3;
        };
    }

    private InformeGlobalProduccionDTO.AnaliticaAreasDTO disponibleSinAreas(
            LocalDate fechaDesdeAnterior,
            LocalDate fechaHastaAnterior
    ) {
        return InformeGlobalProduccionDTO.AnaliticaAreasDTO.builder()
                .disponible(true)
                .mensaje("No hay actividad por areas operativas en el periodo consultado.")
                .fechaDesdePeriodoAnterior(fechaDesdeAnterior)
                .fechaHastaPeriodoAnterior(fechaHastaAnterior)
                .areas(List.of())
                .build();
    }

    private record AreaCategoriaKey(int areaId, int categoriaId) {
    }

    private record ProductionKey(
            String source,
            String unit,
            String equivalentUnit
    ) {
    }

    private static final class MutableProduction {
        private double quantity;
        private double equivalentQuantity;

        void add(double value, Double equivalentValue) {
            quantity += value;
            if (equivalentValue != null) {
                equivalentQuantity += equivalentValue;
            }
        }
    }

    private static final class DailyAccumulator {
        private final LocalDate date;
        private final int dayIndex;
        private int arrivals;
        private int completions;
        private int backlog;

        private DailyAccumulator(LocalDate date, int dayIndex) {
            this.date = date;
            this.dayIndex = dayIndex;
        }

        LocalDate date() {
            return date;
        }

        void incrementArrival() {
            arrivals += 1;
        }

        void incrementCompletion() {
            completions += 1;
        }

        void incrementBacklog() {
            backlog += 1;
        }

        InformeGlobalProduccionDTO.SerieFlujoAreaDTO toDto() {
            return InformeGlobalProduccionDTO.SerieFlujoAreaDTO.builder()
                    .fecha(date)
                    .indiceDia(dayIndex)
                    .entradas(arrivals)
                    .salidas(completions)
                    .backlogCierre(backlog)
                    .build();
        }
    }

    private static final class PeriodAccumulator {
        private final LocalDate start;
        private final LocalDate end;
        private final Map<LocalDate, DailyAccumulator> days = new LinkedHashMap<>();
        private final List<Long> waitDurations = new ArrayList<>();
        private final List<Long> processDurations = new ArrayList<>();
        private final Map<ProductionKey, MutableProduction> production = new LinkedHashMap<>();
        private int completedSteps;
        private int representedCompletions;

        private PeriodAccumulator(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
            int index = 0;
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                days.put(date, new DailyAccumulator(date, index));
                index += 1;
            }
        }

        boolean contains(LocalDate date) {
            return date != null && !date.isBefore(start) && !date.isAfter(end);
        }

        Collection<DailyAccumulator> days() {
            return days.values();
        }

        void addArrival(LocalDate date) {
            DailyAccumulator day = days.get(date);
            if (day != null) {
                day.incrementArrival();
            }
        }

        void addCompletion(LocalDate date) {
            DailyAccumulator day = days.get(date);
            if (day != null) {
                day.incrementCompletion();
                completedSteps += 1;
            }
        }

        void incrementRepresentedCompletion() {
            representedCompletions += 1;
        }

        void addProduction(ProductionKey key, double quantity, Double equivalentQuantity) {
            production.computeIfAbsent(key, ignored -> new MutableProduction())
                    .add(quantity, equivalentQuantity);
        }

        void addDuration(int state, long minutes) {
            if (state == EstadoSeguimientoOrdenArea.ESPERA.getCode()) {
                waitDurations.add(minutes);
            } else if (state == EstadoSeguimientoOrdenArea.EN_PROCESO.getCode()) {
                processDurations.add(minutes);
            }
        }

        int arrivals() {
            return days.values().stream().mapToInt(day -> day.arrivals).sum();
        }

        int completions() {
            return days.values().stream().mapToInt(day -> day.completions).sum();
        }

        int finalBacklog() {
            return days.isEmpty()
                    ? 0
                    : new ArrayList<>(days.values()).get(days.size() - 1).backlog;
        }

        boolean hasActivity() {
            return arrivals() > 0
                    || completions() > 0
                    || finalBacklog() > 0
                    || !waitDurations.isEmpty()
                    || !processDurations.isEmpty();
        }

        Double coveragePct() {
            if (completedSteps == 0) {
                return null;
            }
            return (representedCompletions * 100d) / completedSteps;
        }

        InformeGlobalProduccionDTO.MetricasFlujoAreaDTO metrics(int rangeDays) {
            int output = completions();
            double outputRate = rangeDays > 0 ? output / (double) rangeDays : 0d;
            int readyWork = finalBacklog();
            Double backlogDays = outputRate > 0 ? readyWork / outputRate : null;
            return InformeGlobalProduccionDTO.MetricasFlujoAreaDTO.builder()
                    .entradas(arrivals())
                    .salidas(output)
                    .trabajoListo(readyWork)
                    .ritmoSalidaDiario(outputRate)
                    .diasBacklog(backlogDays)
                    .medianaMinutosEspera(median(waitDurations))
                    .medianaMinutosProceso(median(processDurations))
                    .muestrasEspera(waitDurations.size())
                    .muestrasProceso(processDurations.size())
                    .build();
        }

        List<InformeGlobalProduccionDTO.SerieFlujoAreaDTO> series() {
            return days.values().stream().map(DailyAccumulator::toDto).toList();
        }

        private static Double median(List<Long> values) {
            if (values.isEmpty()) {
                return null;
            }
            List<Long> sorted = values.stream().sorted().toList();
            int middle = sorted.size() / 2;
            if (sorted.size() % 2 == 1) {
                return sorted.get(middle).doubleValue();
            }
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2d;
        }
    }

    private record SignalResult(
            String state,
            String confidence,
            List<String> reasons
    ) {
    }

    private static final class AreaAccumulator {
        private final int areaId;
        private final String areaName;
        private final PeriodAccumulator current;
        private final PeriodAccumulator previous;

        private AreaAccumulator(
                int areaId,
                String areaName,
                LocalDate currentStart,
                LocalDate currentEnd,
                LocalDate previousStart,
                LocalDate previousEnd
        ) {
            this.areaId = areaId;
            this.areaName = areaName;
            this.current = new PeriodAccumulator(currentStart, currentEnd);
            this.previous = new PeriodAccumulator(previousStart, previousEnd);
        }

        int areaId() {
            return areaId;
        }

        PeriodAccumulator current() {
            return current;
        }

        PeriodAccumulator previous() {
            return previous;
        }

        PeriodAccumulator periodFor(LocalDate date) {
            if (current.contains(date)) {
                return current;
            }
            if (previous.contains(date)) {
                return previous;
            }
            return null;
        }

        boolean hasAnyActivity() {
            return current.hasActivity() || previous.hasActivity();
        }

        InformeGlobalProduccionDTO.AreaOperativaAnaliticaDTO toDto(int rangeDays) {
            InformeGlobalProduccionDTO.MetricasFlujoAreaDTO currentMetrics =
                    current.metrics(rangeDays);
            InformeGlobalProduccionDTO.MetricasFlujoAreaDTO previousMetrics =
                    previous.metrics(rangeDays);
            SignalResult signal = resolveSignal(currentMetrics, previousMetrics, rangeDays);

            return InformeGlobalProduccionDTO.AreaOperativaAnaliticaDTO.builder()
                    .areaId(areaId)
                    .areaNombre(areaName)
                    .estado(signal.state())
                    .confiabilidad(signal.confidence())
                    .motivos(signal.reasons())
                    .comparacionDisponible(previous.hasActivity())
                    .coberturaUnidadPct(current.coveragePct())
                    .produccion(buildProductionComparison())
                    .actual(currentMetrics)
                    .anterior(previousMetrics)
                    .serieActual(current.series())
                    .serieAnterior(previous.series())
                    .build();
        }

        private List<InformeGlobalProduccionDTO.ProduccionUnidadAreaDTO> buildProductionComparison() {
            Set<ProductionKey> keys = new LinkedHashSet<>();
            keys.addAll(current.production.keySet());
            keys.addAll(previous.production.keySet());

            return keys.stream()
                    .map(key -> {
                        MutableProduction currentValue = current.production.get(key);
                        MutableProduction previousValue = previous.production.get(key);
                        double currentQuantity = currentValue != null ? currentValue.quantity : 0d;
                        double previousQuantity = previousValue != null ? previousValue.quantity : 0d;
                        return InformeGlobalProduccionDTO.ProduccionUnidadAreaDTO.builder()
                                .fuente(key.source())
                                .unidad(key.unit())
                                .cantidadActual(currentQuantity)
                                .cantidadAnterior(previousQuantity)
                                .variacionPct(trend(currentQuantity, previousQuantity))
                                .cantidadEquivalenteActual(key.equivalentUnit() != null
                                        ? currentValue != null ? currentValue.equivalentQuantity : 0d
                                        : null)
                                .cantidadEquivalenteAnterior(key.equivalentUnit() != null
                                        ? previousValue != null ? previousValue.equivalentQuantity : 0d
                                        : null)
                                .unidadEquivalente(key.equivalentUnit())
                                .build();
                    })
                    .sorted(Comparator
                            .comparingInt((InformeGlobalProduccionDTO.ProduccionUnidadAreaDTO value) ->
                                    sourcePriority(value.getFuente()))
                            .thenComparing(
                                    InformeGlobalProduccionDTO.ProduccionUnidadAreaDTO::getCantidadActual,
                                    Comparator.reverseOrder()
                            )
                            .thenComparing(
                                    InformeGlobalProduccionDTO.ProduccionUnidadAreaDTO::getUnidad,
                                    String.CASE_INSENSITIVE_ORDER
                            ))
                    .toList();
        }

        private SignalResult resolveSignal(
                InformeGlobalProduccionDTO.MetricasFlujoAreaDTO currentMetrics,
                InformeGlobalProduccionDTO.MetricasFlujoAreaDTO previousMetrics,
                int rangeDays
        ) {
            boolean hasCurrentActivity = current.hasActivity();
            if (!hasCurrentActivity) {
                return new SignalResult(
                        "SIN_DATOS",
                        "LIMITADA",
                        List.of("No hay actividad suficiente en el periodo actual.")
                );
            }

            int observations = currentMetrics.getEntradas()
                    + currentMetrics.getSalidas()
                    + currentMetrics.getTrabajoListo();
            boolean reliable = rangeDays >= 3 && observations >= 3;
            String confidence = reliable ? "SUFICIENTE" : "LIMITADA";
            Double waitTrend = trend(
                    currentMetrics.getMedianaMinutosEspera(),
                    previousMetrics.getMedianaMinutosEspera()
            );
            boolean noOutputWithWork = currentMetrics.getTrabajoListo() > 0
                    && currentMetrics.getSalidas() == 0;
            boolean accumulating = currentMetrics.getDiasBacklog() != null
                    && currentMetrics.getDiasBacklog() >= 3d
                    && currentMetrics.getEntradas() > currentMetrics.getSalidas() * 1.10d;

            List<String> reasons = new ArrayList<>();
            if (noOutputWithWork) {
                reasons.add("Hay trabajo listo, pero no se registraron salidas en el periodo.");
            }
            if (currentMetrics.getDiasBacklog() != null
                    && currentMetrics.getDiasBacklog() >= 1d) {
                reasons.add(String.format(
                        java.util.Locale.ROOT,
                        "El backlog equivale a %.1f dias al ritmo actual.",
                        currentMetrics.getDiasBacklog()
                ));
            }
            if (currentMetrics.getEntradas() > currentMetrics.getSalidas()) {
                reasons.add("Las entradas superaron las salidas en "
                        + (currentMetrics.getEntradas() - currentMetrics.getSalidas())
                        + " lote(s).");
            }
            if (waitTrend != null && waitTrend >= 20d) {
                reasons.add(String.format(
                        java.util.Locale.ROOT,
                        "La mediana de espera aumento %.1f%% frente al periodo anterior.",
                        waitTrend
                ));
            }

            if (reliable && (noOutputWithWork || accumulating)) {
                return new SignalResult("POSIBLE_CUELLO", confidence, reasons);
            }

            boolean observation = currentMetrics.getTrabajoListo() > 0 && (
                    noOutputWithWork
                            || currentMetrics.getDiasBacklog() != null
                            && currentMetrics.getDiasBacklog() >= 1d
                            || currentMetrics.getEntradas() > currentMetrics.getSalidas()
                            || waitTrend != null && waitTrend >= 20d
            );
            if (observation) {
                if (!reliable) {
                    reasons.add("La muestra es limitada; la señal debe interpretarse con cautela.");
                }
                return new SignalResult("OBSERVACION", confidence, reasons);
            }

            reasons.add("El flujo no presenta acumulacion relevante en el periodo.");
            if (!reliable) {
                reasons.add("La muestra es limitada para una conclusion definitiva.");
            }
            return new SignalResult("ESTABLE", confidence, reasons);
        }

        private static int sourcePriority(String source) {
            return switch (source) {
                case SOURCE_REPORTED -> 0;
                case SOURCE_STANDARD -> 1;
                default -> 2;
            };
        }

        private static Double trend(double current, double previous) {
            if (previous <= 0d) {
                return null;
            }
            return ((current - previous) / previous) * 100d;
        }

        private static Double trend(Double current, Double previous) {
            if (current == null || previous == null || previous <= 0d) {
                return null;
            }
            return ((current - previous) / previous) * 100d;
        }
    }
}
