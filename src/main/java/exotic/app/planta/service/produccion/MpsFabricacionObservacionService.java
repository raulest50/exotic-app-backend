package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.*;
import exotic.app.planta.model.produccion.dto.MpsFabricacionDTOs.*;
import exotic.app.planta.model.produccion.fabricacion.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.fabricacion.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MpsFabricacionObservacionService {
    private final MpsFabricacionService programaService;
    private final MpsFabricacionSemanalRepo programaRepo;
    private final MpsFabricacionObservacionRepo observacionRepo;
    private final Clock applicationClock;

    @Transactional(readOnly = true)
    public List<ObservacionResponse> listar(LocalDate week) {
        MpsFabricacionService.validarSemana(week);
        return programaRepo.findByWeekStartDate(week)
                .map(p -> observacionRepo.findByMps_IdOrderByFechaCreacionAscObservacionIdAsc(p.getId())
                        .stream().map(this::toResponse).toList()).orElse(List.of());
    }

    public ObservacionResponse crear(LocalDate week, CrearObservacionRequest request, User actor) {
        MpsFabricacionSemanal programa = programaService.bloquear(week, request.getVersion());
        requireBorrador(programa);
        MpsFabricacionObservacion observacion = new MpsFabricacionObservacion();
        observacion.setMps(programa);
        observacion.setRevisionMps(programa.getRevisionNumero());
        observacion.setAutorUsername(actor.getUsername());
        observacion.setMensaje(texto(request.getMensaje()));
        observacion.setTipo(request.getTipo() == null ? TipoMpsSemanalObservacion.BLOQUEANTE : request.getTipo());
        observacion.setFechaCreacion(LocalDateTime.now(applicationClock));
        observacionRepo.saveAndFlush(observacion);
        programaService.actualizar(programa, actor, false);
        return toResponse(observacion);
    }

    public ObservacionResponse atender(Long id, AtenderObservacionRequest request, User actor) {
        MpsFabricacionObservacion observacion = bloquear(id, request.getVersion());
        if (observacion.getEstado() != EstadoMpsSemanalObservacion.ABIERTA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden atender observaciones abiertas.");
        }
        observacion.setRespuestaCorreccion(texto(request.getRespuestaCorreccion()));
        observacion.setEstado(EstadoMpsSemanalObservacion.ATENDIDA);
        observacion.setAtendidaPorUsername(actor.getUsername());
        observacion.setFechaAtencion(LocalDateTime.now(applicationClock));
        observacionRepo.saveAndFlush(observacion);
        programaService.actualizar(observacion.getMps(), actor, false);
        return toResponse(observacion);
    }

    public ObservacionResponse cerrar(Long id, VersionRequest request, User actor) {
        MpsFabricacionObservacion observacion = bloquear(id, request.getVersion());
        if (observacion.getEstado() != EstadoMpsSemanalObservacion.ATENDIDA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden cerrar observaciones atendidas.");
        }
        observacion.setEstado(EstadoMpsSemanalObservacion.CERRADA);
        observacion.setCerradaPorUsername(actor.getUsername());
        observacion.setFechaCierre(LocalDateTime.now(applicationClock));
        observacionRepo.saveAndFlush(observacion);
        programaService.actualizar(observacion.getMps(), actor, false);
        return toResponse(observacion);
    }

    private MpsFabricacionObservacion bloquear(Long id, Long version) {
        LocalDate week = observacionRepo.findWeekStartDateById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Observacion OF no encontrada."));
        MpsFabricacionSemanal programa = programaService.bloquear(week, version);
        requireBorrador(programa);
        return observacionRepo.findById(id).orElseThrow();
    }

    private void requireBorrador(MpsFabricacionSemanal programa) {
        if (programa.getEstado() != EstadoMpsSemanal.BORRADOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Las observaciones solo se gestionan en borrador.");
        }
    }

    private String texto(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("El texto es obligatorio.");
        return value.trim();
    }

    private ObservacionResponse toResponse(MpsFabricacionObservacion o) {
        ObservacionResponse dto = new ObservacionResponse();
        dto.setObservacionId(o.getObservacionId()); dto.setMpsId(o.getMps().getId());
        dto.setWeekStartDate(o.getMps().getWeekStartDate()); dto.setRevisionMps(o.getRevisionMps());
        dto.setAutorUsername(o.getAutorUsername()); dto.setMensaje(o.getMensaje()); dto.setTipo(o.getTipo());
        dto.setEstado(o.getEstado()); dto.setRespuestaCorreccion(o.getRespuestaCorreccion());
        dto.setAtendidaPorUsername(o.getAtendidaPorUsername()); dto.setFechaAtencion(o.getFechaAtencion());
        dto.setCerradaPorUsername(o.getCerradaPorUsername()); dto.setFechaCierre(o.getFechaCierre());
        dto.setFechaCreacion(o.getFechaCreacion()); dto.setVersion(o.getMps().getVersion());
        return dto;
    }
}
