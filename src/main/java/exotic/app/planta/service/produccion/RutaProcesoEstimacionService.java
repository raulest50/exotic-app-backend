package exotic.app.planta.service.produccion;

import exotic.app.planta.model.empresa.JornadaLaboralBloque;
import exotic.app.planta.model.empresa.JornadaLaboralVersion;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import exotic.app.planta.repo.empresa.JornadaLaboralVersionRepo;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RutaProcesoEstimacionService {

    private final JornadaLaboralVersionRepo jornadaLaboralVersionRepo;

    @Transactional(readOnly = true)
    public RutaProcesoEstimacionDTO estimarOrden(
            OrdenProduccion orden,
            List<SeguimientoOrdenArea> seguimientos
    ) {
        if (orden == null || seguimientos == null || seguimientos.isEmpty()) {
            return null;
        }

        LocalDateTime inicio = resolveInicioEstimacion(orden);
        if (inicio == null) {
            return null;
        }

        Map<Long, SeguimientoOrdenArea> seguimientoPorNodeId = new LinkedHashMap<>();
        seguimientos.stream()
                .filter(seguimiento -> seguimiento.getRutaProcesoNode() != null)
                .sorted(Comparator.comparing(
                        SeguimientoOrdenArea::getPosicionSecuencia,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .forEach(seguimiento -> seguimientoPorNodeId.put(
                        seguimiento.getRutaProcesoNode().getId(),
                        seguimiento
                ));

        if (seguimientoPorNodeId.isEmpty()) {
            return null;
        }

        RutaProcesoCatVersion rutaVersion = resolveRutaVersion(orden, seguimientos);
        if (rutaVersion == null) {
            return buildContinuousSequentialEstimate(inicio, seguimientoPorNodeId.values().stream().toList(), orden);
        }

        Graph graph = buildGraph(rutaVersion, seguimientoPorNodeId);
        JornadaLaboralVersion jornada = resolveJornada(orden);
        Map<Long, LocalDateTime> finishByNodeId = new HashMap<>();
        ArrayDeque<Long> queue = graph.zeroIndegreeQueue();
        int processed = 0;

        while (!queue.isEmpty()) {
            Long nodeId = queue.removeFirst();
            SeguimientoOrdenArea seguimiento = seguimientoPorNodeId.get(nodeId);
            if (seguimiento == null) {
                continue;
            }

            LocalDateTime nodeStart = graph.incoming().getOrDefault(nodeId, List.of())
                    .stream()
                    .map(finishByNodeId::get)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(inicio);

            LocalDateTime nodeFinish = addMinutes(
                    nodeStart,
                    Math.max(seguimiento.getDuracionEstimadaMinutos(), 0),
                    seguimiento.isRequiereJornadaLaboral(),
                    jornada
            );
            finishByNodeId.put(nodeId, nodeFinish);
            processed++;

            for (Long targetId : graph.outgoing().getOrDefault(nodeId, List.of())) {
                int remainingIndegree = graph.remainingIndegree().merge(targetId, -1, Integer::sum);
                if (remainingIndegree == 0) {
                    queue.addLast(targetId);
                }
            }
        }

        if (processed != seguimientoPorNodeId.size()) {
            return buildContinuousSequentialEstimate(inicio, seguimientoPorNodeId.values().stream().toList(), orden);
        }

        LocalDateTime fin = finishByNodeId.values()
                .stream()
                .max(LocalDateTime::compareTo)
                .orElse(inicio);

        return buildDTO(inicio, fin, orden);
    }

    private RutaProcesoEstimacionDTO buildContinuousSequentialEstimate(
            LocalDateTime inicio,
            List<SeguimientoOrdenArea> seguimientos,
            OrdenProduccion orden
    ) {
        JornadaLaboralVersion jornada = resolveJornada(orden);
        LocalDateTime current = inicio;
        for (SeguimientoOrdenArea seguimiento : seguimientos) {
            current = addMinutes(
                    current,
                    Math.max(seguimiento.getDuracionEstimadaMinutos(), 0),
                    seguimiento.isRequiereJornadaLaboral(),
                    jornada
            );
        }
        return buildDTO(inicio, current, orden);
    }

    private RutaProcesoEstimacionDTO buildDTO(
            LocalDateTime inicio,
            LocalDateTime fin,
            OrdenProduccion orden
    ) {
        RutaProcesoEstimacionDTO dto = new RutaProcesoEstimacionDTO();
        dto.setFechaInicioEstimacion(inicio);
        dto.setFechaFinalEstimada(fin);
        dto.setDuracionCalendarioRutaCriticaMinutos(Math.max(Duration.between(inicio, fin).toMinutes(), 0));
        if (orden.getJornadaLaboralVersion() != null) {
            dto.setJornadaLaboralVersionId(orden.getJornadaLaboralVersion().getId());
        }
        return dto;
    }

    private LocalDateTime resolveInicioEstimacion(OrdenProduccion orden) {
        if (orden.getFechaLanzamiento() != null) {
            return orden.getFechaLanzamiento();
        }
        return orden.getFechaCreacion();
    }

    private RutaProcesoCatVersion resolveRutaVersion(
            OrdenProduccion orden,
            List<SeguimientoOrdenArea> seguimientos
    ) {
        if (orden.getRutaProcesoCatVersion() != null) {
            return orden.getRutaProcesoCatVersion();
        }
        return seguimientos.stream()
                .map(SeguimientoOrdenArea::getRutaProcesoNode)
                .filter(Objects::nonNull)
                .map(node -> node.getRutaProcesoCatVersion())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private JornadaLaboralVersion resolveJornada(OrdenProduccion orden) {
        if (orden.getJornadaLaboralVersion() != null) {
            return orden.getJornadaLaboralVersion();
        }
        return jornadaLaboralVersionRepo.findFirstByEstadoOrderByVersionDesc(JornadaLaboralVersion.Estado.VIGENTE)
                .orElse(null);
    }

    private Graph buildGraph(
            RutaProcesoCatVersion rutaVersion,
            Map<Long, SeguimientoOrdenArea> seguimientoPorNodeId
    ) {
        Map<Long, List<Long>> outgoing = new LinkedHashMap<>();
        Map<Long, List<Long>> incoming = new LinkedHashMap<>();
        Map<Long, Integer> indegree = new LinkedHashMap<>();
        seguimientoPorNodeId.keySet().forEach(nodeId -> {
            outgoing.put(nodeId, new ArrayList<>());
            incoming.put(nodeId, new ArrayList<>());
            indegree.put(nodeId, 0);
        });

        for (RutaProcesoEdge edge : rutaVersion.getEdges()) {
            Long sourceId = edge.getSourceNode() != null ? edge.getSourceNode().getId() : null;
            Long targetId = edge.getTargetNode() != null ? edge.getTargetNode().getId() : null;
            if (!seguimientoPorNodeId.containsKey(sourceId) || !seguimientoPorNodeId.containsKey(targetId)) {
                continue;
            }
            outgoing.get(sourceId).add(targetId);
            incoming.get(targetId).add(sourceId);
            indegree.put(targetId, indegree.get(targetId) + 1);
        }

        return new Graph(outgoing, incoming, new LinkedHashMap<>(indegree));
    }

    private LocalDateTime addMinutes(
            LocalDateTime start,
            int minutes,
            boolean requiereJornadaLaboral,
            JornadaLaboralVersion jornada
    ) {
        if (minutes <= 0) {
            return start;
        }
        if (!requiereJornadaLaboral || jornada == null || jornada.getBloques() == null || jornada.getBloques().isEmpty()) {
            return start.plusMinutes(minutes);
        }
        return addWorkingMinutes(start, minutes, jornada);
    }

    private LocalDateTime addWorkingMinutes(
            LocalDateTime start,
            long minutes,
            JornadaLaboralVersion jornada
    ) {
        Map<Integer, List<JornadaLaboralBloque>> bloquesPorDia = buildBloquesPorDia(jornada);
        if (bloquesPorDia.isEmpty()) {
            return start.plusMinutes(minutes);
        }

        LocalDateTime current = start;
        long remaining = minutes;
        while (remaining > 0) {
            current = moveToWorkingTime(current, bloquesPorDia);
            LocalDateTime blockEnd = resolveCurrentBlockEnd(current, bloquesPorDia);
            if (blockEnd == null) {
                return current.plusMinutes(remaining);
            }

            long available = Math.max(Duration.between(current, blockEnd).toMinutes(), 0);
            if (available <= 0) {
                current = current.plusMinutes(1);
                continue;
            }

            long consumed = Math.min(remaining, available);
            current = current.plusMinutes(consumed);
            remaining -= consumed;
        }

        return current;
    }

    private Map<Integer, List<JornadaLaboralBloque>> buildBloquesPorDia(JornadaLaboralVersion jornada) {
        Map<Integer, List<JornadaLaboralBloque>> result = new HashMap<>();
        for (JornadaLaboralBloque bloque : jornada.getBloques()) {
            result.computeIfAbsent(bloque.getDiaSemana(), ignored -> new ArrayList<>()).add(bloque);
        }
        result.values().forEach(blocks -> blocks.sort(Comparator.comparing(JornadaLaboralBloque::getHoraInicio)));
        return result;
    }

    private LocalDateTime moveToWorkingTime(
            LocalDateTime value,
            Map<Integer, List<JornadaLaboralBloque>> bloquesPorDia
    ) {
        for (int offset = 0; offset <= 7; offset++) {
            LocalDate date = value.toLocalDate().plusDays(offset);
            List<JornadaLaboralBloque> blocks = bloquesPorDia.get(date.getDayOfWeek().getValue());
            if (blocks == null || blocks.isEmpty()) {
                continue;
            }

            for (JornadaLaboralBloque block : blocks) {
                LocalDateTime blockStart = LocalDateTime.of(date, block.getHoraInicio());
                LocalDateTime blockEnd = LocalDateTime.of(date, block.getHoraFin());
                if (offset == 0 && !value.isBefore(blockStart) && value.isBefore(blockEnd)) {
                    return value;
                }
                if (value.isBefore(blockStart)) {
                    return blockStart;
                }
            }
        }

        return value;
    }

    private LocalDateTime resolveCurrentBlockEnd(
            LocalDateTime value,
            Map<Integer, List<JornadaLaboralBloque>> bloquesPorDia
    ) {
        List<JornadaLaboralBloque> blocks = bloquesPorDia.get(value.getDayOfWeek().getValue());
        if (blocks == null) {
            return null;
        }

        LocalTime time = value.toLocalTime();
        for (JornadaLaboralBloque block : blocks) {
            if (!time.isBefore(block.getHoraInicio()) && time.isBefore(block.getHoraFin())) {
                return LocalDateTime.of(value.toLocalDate(), block.getHoraFin());
            }
        }

        return null;
    }

    private record Graph(
            Map<Long, List<Long>> outgoing,
            Map<Long, List<Long>> incoming,
            Map<Long, Integer> remainingIndegree
    ) {
        private ArrayDeque<Long> zeroIndegreeQueue() {
            ArrayDeque<Long> queue = new ArrayDeque<>();
            remainingIndegree.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue() == 0)
                    .map(Map.Entry::getKey)
                    .forEach(queue::addLast);
            return queue;
        }
    }

    @Data
    public static class RutaProcesoEstimacionDTO {
        private LocalDateTime fechaInicioEstimacion;
        private LocalDateTime fechaFinalEstimada;
        private Long duracionCalendarioRutaCriticaMinutos;
        private Long jornadaLaboralVersionId;
    }
}
