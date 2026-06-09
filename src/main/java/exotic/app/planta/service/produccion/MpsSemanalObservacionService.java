package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.EstadoMpsSemanalObservacion;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalObservacion;
import exotic.app.planta.model.produccion.TipoMpsSemanalObservacion;
import exotic.app.planta.model.produccion.dto.AtenderMpsSemanalObservacionRequestDTO;
import exotic.app.planta.model.produccion.dto.CrearMpsSemanalObservacionRequestDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalObservacionDTO;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.MpsSemanalObservacionRepo;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class MpsSemanalObservacionService {

    private final MasterProductionScheduleSemanalRepo mpsRepo;
    private final MpsSemanalObservacionRepo observacionRepo;

    @Transactional(readOnly = true)
    public List<MpsSemanalObservacionDTO> listarPorSemana(LocalDate weekStartDate) {
        log.debug("[MPS_SEMANAL] service listarObservaciones begin weekStartDate={}", weekStartDate);
        MasterProductionScheduleSemanal mps = requireMpsByWeekStartDate(weekStartDate);
        List<MpsSemanalObservacionDTO> response = observacionRepo.findAllByMpsSemanal_MpsIdOrderByFechaCreacionAsc(mps.getMpsId())
                .stream()
                .map(MpsSemanalObservacionDTO::fromEntity)
                .toList();
        log.info("[MPS_SEMANAL] service listarObservaciones success weekStartDate={} mpsId={} count={}", weekStartDate, mps.getMpsId(), response.size());
        return response;
    }

    public MpsSemanalObservacionDTO crearObservacion(
            CrearMpsSemanalObservacionRequestDTO request,
            String autorUsername
    ) {
        log.info(
                "[MPS_SEMANAL] service crearObservacion begin weekStartDate={} tipo={} autorUsername={} mensajePresent={}",
                request != null ? request.getWeekStartDate() : null,
                request != null ? request.getTipo() : null,
                autorUsername,
                request != null && request.getMensaje() != null && !request.getMensaje().isBlank()
        );
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de observacion no puede ser nula.");
        }
        validateUsername(autorUsername, "No se pudo determinar el usuario que crea la observacion.");
        String mensaje = normalizeRequiredText(request.getMensaje(), "La observacion no puede estar vacia.");
        TipoMpsSemanalObservacion tipo = request.getTipo() != null
                ? request.getTipo()
                : TipoMpsSemanalObservacion.BLOQUEANTE;
        MasterProductionScheduleSemanal mps = requireMpsByWeekStartDate(request.getWeekStartDate());
        requireDraftMps(mps);

        MpsSemanalObservacion observacion = new MpsSemanalObservacion();
        observacion.setMpsSemanal(mps);
        observacion.setRevisionMps(resolveRevisionNumero(mps));
        observacion.setAutorUsername(autorUsername.trim());
        observacion.setMensaje(mensaje);
        observacion.setTipo(tipo);
        observacion.setEstado(EstadoMpsSemanalObservacion.ABIERTA);

        MpsSemanalObservacionDTO response = MpsSemanalObservacionDTO.fromEntity(observacionRepo.save(observacion));
        log.info(
                "[MPS_SEMANAL] service crearObservacion success observacionId={} mpsId={} weekStartDate={} tipo={} estado={} autorUsername={}",
                response.getObservacionId(),
                response.getMpsId(),
                response.getWeekStartDate(),
                response.getTipo(),
                response.getEstado(),
                autorUsername
        );
        return response;
    }

    public MpsSemanalObservacionDTO atenderObservacion(
            Long observacionId,
            AtenderMpsSemanalObservacionRequestDTO request,
            String atendidaPorUsername
    ) {
        log.info(
                "[MPS_SEMANAL] service atenderObservacion begin observacionId={} atendidaPorUsername={} respuestaPresent={}",
                observacionId,
                atendidaPorUsername,
                request != null && request.getRespuestaCorreccion() != null && !request.getRespuestaCorreccion().isBlank()
        );
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de atencion no puede ser nula.");
        }
        validateUsername(atendidaPorUsername, "No se pudo determinar el usuario que atiende la observacion.");
        String respuesta = normalizeRequiredText(request.getRespuestaCorreccion(), "La respuesta de correccion no puede estar vacia.");

        MpsSemanalObservacion observacion = requireObservacion(observacionId);
        requireDraftMps(observacion.getMpsSemanal());
        if (observacion.getEstado() != EstadoMpsSemanalObservacion.ABIERTA) {
            log.warn("[MPS_SEMANAL] service atenderObservacion rejected observacionId={} estado={}", observacionId, observacion.getEstado());
            throw new IllegalStateException("Solo se pueden atender observaciones ABIERTAS.");
        }

        observacion.setEstado(EstadoMpsSemanalObservacion.ATENDIDA);
        observacion.setRespuestaCorreccion(respuesta);
        observacion.setAtendidaPorUsername(atendidaPorUsername.trim());
        observacion.setFechaAtencion(LocalDateTime.now());

        MpsSemanalObservacionDTO response = MpsSemanalObservacionDTO.fromEntity(observacionRepo.save(observacion));
        log.info(
                "[MPS_SEMANAL] service atenderObservacion success observacionId={} mpsId={} weekStartDate={} estado={} atendidaPorUsername={}",
                response.getObservacionId(),
                response.getMpsId(),
                response.getWeekStartDate(),
                response.getEstado(),
                atendidaPorUsername
        );
        return response;
    }

    public MpsSemanalObservacionDTO cerrarObservacion(Long observacionId, String cerradaPorUsername) {
        log.info("[MPS_SEMANAL] service cerrarObservacion begin observacionId={} cerradaPorUsername={}", observacionId, cerradaPorUsername);
        validateUsername(cerradaPorUsername, "No se pudo determinar el usuario que cierra la observacion.");

        MpsSemanalObservacion observacion = requireObservacion(observacionId);
        requireDraftMps(observacion.getMpsSemanal());
        if (observacion.getEstado() != EstadoMpsSemanalObservacion.ATENDIDA) {
            log.warn("[MPS_SEMANAL] service cerrarObservacion rejected observacionId={} estado={}", observacionId, observacion.getEstado());
            throw new IllegalStateException("Solo se pueden cerrar observaciones ATENDIDAS.");
        }

        observacion.setEstado(EstadoMpsSemanalObservacion.CERRADA);
        observacion.setCerradaPorUsername(cerradaPorUsername.trim());
        observacion.setFechaCierre(LocalDateTime.now());

        MpsSemanalObservacionDTO response = MpsSemanalObservacionDTO.fromEntity(observacionRepo.save(observacion));
        log.info(
                "[MPS_SEMANAL] service cerrarObservacion success observacionId={} mpsId={} weekStartDate={} estado={} cerradaPorUsername={}",
                response.getObservacionId(),
                response.getMpsId(),
                response.getWeekStartDate(),
                response.getEstado(),
                cerradaPorUsername
        );
        return response;
    }

    private MasterProductionScheduleSemanal requireMpsByWeekStartDate(LocalDate weekStartDate) {
        validateWeekStartDate(weekStartDate);
        return mpsRepo.findByWeekStartDate(weekStartDate)
                .orElseThrow(() -> new MpsSemanalNotFoundException(
                        "No existe MPS semanal para la semana iniciando en " + weekStartDate + "."
                ));
    }

    private MpsSemanalObservacion requireObservacion(Long observacionId) {
        if (observacionId == null) {
            throw new IllegalArgumentException("observacionId es obligatorio.");
        }
        return observacionRepo.findById(observacionId)
                .orElseThrow(() -> new MpsSemanalNotFoundException(
                        "No existe observacion MPS con id " + observacionId + "."
                ));
    }

    private void validateWeekStartDate(LocalDate weekStartDate) {
        if (weekStartDate == null) {
            throw new IllegalArgumentException("weekStartDate es obligatorio.");
        }
        if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStartDate debe corresponder a un lunes.");
        }
    }

    private void requireDraftMps(MasterProductionScheduleSemanal mps) {
        if (mps.getEstado() != EstadoMpsSemanal.BORRADOR) {
            log.warn("[MPS_SEMANAL] service requireDraftMps rejected mpsId={} estado={}", mps.getMpsId(), mps.getEstado());
            throw new IllegalStateException("Solo se pueden gestionar observaciones de MPS en estado BORRADOR.");
        }
    }

    private void validateUsername(String username, String message) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private int resolveRevisionNumero(MasterProductionScheduleSemanal mps) {
        return mps.getRevisionNumero() != null && mps.getRevisionNumero() > 0
                ? mps.getRevisionNumero()
                : 1;
    }
}
