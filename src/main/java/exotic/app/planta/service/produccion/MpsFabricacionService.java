package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.organizacion.AlcanceMps;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.produccion.dto.MpsFabricacionDTOs.*;
import exotic.app.planta.model.produccion.dto.OrdenFabricacionDTOs;
import exotic.app.planta.model.produccion.fabricacion.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.produccion.fabricacion.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

/** Solo planificacion y consulta: no emite ordenes ni mueve inventario. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MpsFabricacionService {
    private final MpsFabricacionSemanalRepo programaRepo;
    private final SemiTerminadoRepo semiRepo;
    private final ManufacturingVersionRepo manufacturingRepo;
    private final OrdenFabricacionRepo ordenRepo;
    private final LoteRepo loteRepo;
    private final OrdenFabricacionService ordenService;
    private final ObjectMapper objectMapper;
    private final Clock applicationClock;

    public ProgramaResponse consultar(LocalDate week, AreaOperativa area) {
        validarSemana(week);
        var programa = programaRepo.findByWeekStartDate(week).orElse(null);
        return toResponse(week, programa, area);
    }

    @Transactional
    public ProgramaResponse guardar(LocalDate week, GuardarRequest request, User actor) {
        validarSemana(week);
        MpsFabricacionSemanal programa = programaRepo.findByWeekForUpdate(week).orElse(null);
        if (programa == null ? request.getVersion() != null
                : !Objects.equals(request.getVersion(), programa.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El MPS OF cambio desde que se abrio. Recargue antes de guardar.");
        }
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        if (programa == null) {
            programa = new MpsFabricacionSemanal();
            programa.setWeekStartDate(week);
            programa.setCreadoEn(ahora);
            programa.setCreadoPor(actor.getUsername());
        }
        Map<Long, MpsFabricacionDetalle> anteriores = new HashMap<>();
        programa.getDetalles().forEach(d -> anteriores.put(d.getId(), d));
        Set<Long> idsUsados = new HashSet<>();
        List<MpsFabricacionDetalle> detalles = new ArrayList<>();
        for (LineaRequest linea : request.getPropuestas()) {
            if (linea.getFechaInicio().toLocalDate().isBefore(week)
                    || !linea.getFechaInicio().toLocalDate().isBefore(week.plusWeeks(1))) {
                throw new IllegalArgumentException("El inicio de cada propuesta debe pertenecer a la semana seleccionada.");
            }
            if (linea.getFechaFinal().isBefore(linea.getFechaInicio())) {
                throw new IllegalArgumentException("La fecha final no puede ser anterior al inicio.");
            }
            MpsFabricacionDetalle detalle = linea.getId() == null ? new MpsFabricacionDetalle() : anteriores.get(linea.getId());
            if (detalle == null || (linea.getId() != null && !idsUsados.add(linea.getId()))) {
                throw new IllegalArgumentException("Una propuesta esta repetida o no pertenece a este MPS OF.");
            }
            SemiTerminado semi = semiRepo.findById(linea.getSemiTerminadoId().trim())
                    .orElseThrow(() -> new IllegalArgumentException("Semiterminado no encontrado."));
            if (!semi.isRequiereOrdenFabricacion()) {
                throw new IllegalArgumentException("El semiterminado " + semi.getProductoId() + " no esta marcado para OF.");
            }
            String unidad = semi.getTipoUnidades();
            if (unidad == null || unidad.isBlank()) {
                throw new IllegalArgumentException("El semiterminado no tiene unidad de medida configurada.");
            }
            if (detalle.getId() != null && detalle.getSemiTerminado().getProductoId().equals(semi.getProductoId())
                    && !Objects.equals(detalle.getUnidadMedida(), unidad.trim())) {
                throw new IllegalArgumentException("Cambio la unidad del semiterminado. Reemplace la propuesta para expresar su nueva cantidad.");
            }
            detalle.setMps(programa);
            detalle.setSemiTerminado(semi);
            detalle.setUnidadMedida(unidad.trim());
            detalle.setCantidad(linea.getCantidad());
            detalle.setFechaInicio(linea.getFechaInicio());
            detalle.setFechaFinal(linea.getFechaFinal());
            detalle.setObservaciones(linea.getObservaciones() == null ? null : linea.getObservaciones().trim());
            detalle.setPosicion(detalles.size());
            detalles.add(detalle);
        }
        programa.getDetalles().removeIf(d -> !detalles.contains(d));
        for (MpsFabricacionDetalle detalle : detalles) {
            if (detalle.getId() == null) programa.getDetalles().add(detalle);
        }
        // El padre debe cambiar incluso cuando solo se modifica una linea.
        if (programa.getActualizadoEn() != null && !ahora.isAfter(programa.getActualizadoEn())) {
            ahora = programa.getActualizadoEn().plusNanos(1000);
        }
        programa.setActualizadoEn(ahora);
        programa.setActualizadoPor(actor.getUsername());
        programaRepo.saveAndFlush(programa);
        return toResponse(week, programa, null);
    }

    public Page<OrdenResponse> ordenes(LocalDate week, int page, int size, AreaOperativa area) {
        validarSemana(week);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Paginacion invalida (tamano de 1 a 100).");
        Integer areaId = soloRuta(area) ? area.getAreaId() : null;
        return ordenRepo.findSemanaMps(week.atStartOfDay(), week.plusWeeks(1).atStartOfDay(), areaId,
                PageRequest.of(page, size)).map(this::toOrdenResponse);
    }

    public OrdenFabricacionDTOs.Response detalleOrden(Long id, AreaOperativa area) {
        if (soloRuta(area) && !ordenRepo.perteneceAlArea(id, area.getAreaId())) {
            throw new AccessDeniedException("La OF no pertenece a la ruta de su area.");
        }
        return ordenService.detalle(id);
    }

    private ProgramaResponse toResponse(LocalDate week, MpsFabricacionSemanal programa, AreaOperativa area) {
        ProgramaResponse dto = new ProgramaResponse();
        dto.setWeekStartDate(week);
        dto.setWeekEndDate(week.plusDays(6));
        if (programa == null) return dto;
        dto.setId(programa.getId());
        dto.setVersion(programa.getVersion());
        dto.setActualizadoEn(programa.getActualizadoEn());
        dto.setActualizadoPor(programa.getActualizadoPor());
        Map<String, Boolean> visiblePorProducto = new HashMap<>();
        dto.setPropuestas(programa.getDetalles().stream()
                .filter(d -> !soloRuta(area) || visiblePorProducto.computeIfAbsent(d.getSemiTerminado().getProductoId(),
                        ignored -> pasaPorArea(d.getSemiTerminado(), area.getAreaId())))
                .sorted(Comparator.comparingInt(MpsFabricacionDetalle::getPosicion))
                .map(this::toLineaResponse).toList());
        return dto;
    }

    private boolean pasaPorArea(SemiTerminado semi, int areaId) {
        var version = manufacturingRepo.findTopByProductoOrderByVersionNumberDesc(semi).orElse(null);
        if (version == null || version.getProcesoProduccionJson() == null || version.getProcesoProduccionJson().isBlank()) return false;
        try {
            var root = objectMapper.readTree(version.getProcesoProduccionJson());
            if (root == null) return false;
            var nodes = root.path("nodes");
            if (!nodes.isArray()) return false;
            for (var node : nodes) {
                if ("PROCESO".equalsIgnoreCase(node.path("nodeType").asText())
                        && node.path("areaOperativaId").asInt(Integer.MIN_VALUE) == areaId) return true;
            }
            return false;
        } catch (JsonProcessingException e) {
            log.warn("No se pudo filtrar la propuesta MPS OF del producto {}: manufactura invalida", semi.getProductoId());
            return false;
        }
    }

    private LineaResponse toLineaResponse(MpsFabricacionDetalle d) {
        LineaResponse dto = new LineaResponse();
        dto.setId(d.getId());
        dto.setSemiTerminadoId(d.getSemiTerminado().getProductoId());
        dto.setSemiTerminadoNombre(d.getSemiTerminado().getNombre());
        dto.setCantidad(d.getCantidad());
        dto.setUnidadMedida(d.getUnidadMedida());
        dto.setFechaInicio(d.getFechaInicio());
        dto.setFechaFinal(d.getFechaFinal());
        dto.setObservaciones(d.getObservaciones());
        dto.setElegible(d.getSemiTerminado().isRequiereOrdenFabricacion());
        return dto;
    }

    private OrdenResponse toOrdenResponse(OrdenFabricacion orden) {
        OrdenResponse dto = new OrdenResponse();
        dto.setOrdenFabricacionId(orden.getOrdenFabricacionId());
        dto.setSemiTerminadoId(orden.getSemiTerminado().getProductoId());
        dto.setSemiTerminadoNombre(orden.getSemiTerminado().getNombre());
        dto.setCantidadPlanificada(orden.getCantidadPlanificada());
        dto.setUnidadMedida(orden.getUnidadMedida());
        dto.setEstado(orden.getEstado().name());
        dto.setUsaFechaCreacion(orden.getFechaLanzamiento() == null);
        dto.setFechaInicioSemana(orden.getFechaLanzamiento() == null ? orden.getFechaCreacion() : orden.getFechaLanzamiento());
        dto.setFechaFinalPlanificada(orden.getFechaFinalPlanificada());
        loteRepo.findByOrdenFabricacion_OrdenFabricacionId(orden.getOrdenFabricacionId()).stream()
                .findFirst().ifPresent(l -> dto.setLote(l.getBatchNumber()));
        return dto;
    }

    private boolean soloRuta(AreaOperativa area) {
        return area != null && area.getAlcanceMps() == AlcanceMps.SOLO_RUTA;
    }

    public static void validarSemana(LocalDate week) {
        if (week == null || week.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("La semana debe comenzar en lunes (semana ISO).");
        }
    }
}
