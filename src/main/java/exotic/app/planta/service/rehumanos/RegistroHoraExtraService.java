package exotic.app.planta.service.rehumanos;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.organizacion.personal.DocTranDePersonal;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.organizacion.personal.RegistroHoraExtra;
import exotic.app.planta.model.organizacion.personal.dto.RegistroHoraExtraDecisionDTO;
import exotic.app.planta.model.organizacion.personal.dto.RegistroHoraExtraRequestDTO;
import exotic.app.planta.model.organizacion.personal.dto.RegistroHoraExtraResponseDTO;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.personal.DocTranDePersonalRepo;
import exotic.app.planta.repo.personal.IntegrantePersonalRepo;
import exotic.app.planta.repo.personal.RegistroHoraExtraRepo;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class RegistroHoraExtraService {

    private final RegistroHoraExtraRepo registroHoraExtraRepo;
    private final IntegrantePersonalRepo integrantePersonalRepo;
    private final DocTranDePersonalRepo docTranDePersonalRepo;

    public RegistroHoraExtraResponseDTO registrar(
            Long integranteId,
            RegistroHoraExtraRequestDTO request,
            User registradoPor
    ) {
        IntegrantePersonal integrante = requireIntegranteActivo(integranteId);
        validateRequest(request);
        User gestor = requireGestor(registradoPor);

        RegistroHoraExtra registro = new RegistroHoraExtra();
        registro.setIntegrante(integrante);
        registro.setFecha(request.getFecha());
        registro.setHoraInicio(request.getHoraInicio());
        registro.setHoraFin(request.getHoraFin());
        registro.setMinutos(calcularMinutos(request));
        registro.setMotivo(normalizeRequired(request.getMotivo(), "El motivo es obligatorio."));
        registro.setObservaciones(normalizeOptional(request.getObservaciones()));
        registro.setEstado(RegistroHoraExtra.Estado.REGISTRADA);
        registro.setRegistradoPor(gestor);
        registro.setFechaRegistro(AppTime.now());

        RegistroHoraExtra saved = registroHoraExtraRepo.save(registro);
        registrarDocumento(saved, DocTranDePersonal.TipoDocTran.HORAS_EXTRA_REGISTRO, gestor, null);
        return RegistroHoraExtraResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<RegistroHoraExtraResponseDTO> listarPorIntegrante(Long integranteId) {
        if (!integrantePersonalRepo.existsById(integranteId)) {
            throw new EntityNotFoundException("Integrante de personal no encontrado: " + integranteId);
        }
        return registroHoraExtraRepo.findByIntegrante_IdOrderByFechaDescHoraInicioDesc(integranteId)
                .stream()
                .map(RegistroHoraExtraResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<RegistroHoraExtraResponseDTO> buscar(
            LocalDate desde,
            LocalDate hasta,
            RegistroHoraExtra.Estado estado,
            String q,
            Pageable pageable
    ) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial.");
        }
        return registroHoraExtraRepo.buscar(desde, hasta, estado, normalizeOptional(q), pageable)
                .map(RegistroHoraExtraResponseDTO::fromEntity);
    }

    public RegistroHoraExtraResponseDTO aprobar(Long registroId, User aprobadoPor) {
        RegistroHoraExtra registro = requireRegistro(registroId);
        User gestor = requireGestor(aprobadoPor);
        requireEstado(registro, RegistroHoraExtra.Estado.REGISTRADA, "Solo se pueden aprobar registros en estado REGISTRADA.");

        cambiarEstado(registro, RegistroHoraExtra.Estado.APROBADA, gestor, null);
        registrarDocumento(registro, DocTranDePersonal.TipoDocTran.HORAS_EXTRA_APROBACION, gestor, null);
        return RegistroHoraExtraResponseDTO.fromEntity(registro);
    }

    public RegistroHoraExtraResponseDTO rechazar(Long registroId, RegistroHoraExtraDecisionDTO decision, User rechazadoPor) {
        RegistroHoraExtra registro = requireRegistro(registroId);
        User gestor = requireGestor(rechazadoPor);
        requireEstado(registro, RegistroHoraExtra.Estado.REGISTRADA, "Solo se pueden rechazar registros en estado REGISTRADA.");
        String motivo = normalizeRequired(decision != null ? decision.getMotivo() : null, "El motivo de rechazo es obligatorio.");

        cambiarEstado(registro, RegistroHoraExtra.Estado.RECHAZADA, gestor, motivo);
        registrarDocumento(registro, DocTranDePersonal.TipoDocTran.HORAS_EXTRA_RECHAZO, gestor, motivo);
        return RegistroHoraExtraResponseDTO.fromEntity(registro);
    }

    public RegistroHoraExtraResponseDTO anular(Long registroId, RegistroHoraExtraDecisionDTO decision, User anuladoPor) {
        RegistroHoraExtra registro = requireRegistro(registroId);
        User gestor = requireGestor(anuladoPor);
        if (registro.getEstado() == RegistroHoraExtra.Estado.ANULADA) {
            throw new IllegalArgumentException("No se puede anular un registro ya anulado.");
        }
        String motivo = normalizeRequired(decision != null ? decision.getMotivo() : null, "El motivo de anulación es obligatorio.");

        cambiarEstado(registro, RegistroHoraExtra.Estado.ANULADA, gestor, motivo);
        registrarDocumento(registro, DocTranDePersonal.TipoDocTran.HORAS_EXTRA_ANULACION, gestor, motivo);
        return RegistroHoraExtraResponseDTO.fromEntity(registro);
    }

    private IntegrantePersonal requireIntegranteActivo(Long integranteId) {
        IntegrantePersonal integrante = integrantePersonalRepo.findById(integranteId)
                .orElseThrow(() -> new EntityNotFoundException("Integrante de personal no encontrado: " + integranteId));
        if (integrante.getEstado() != IntegrantePersonal.Estado.ACTIVO) {
            throw new IllegalArgumentException("Solo se pueden registrar horas extra para integrantes activos.");
        }
        return integrante;
    }

    private RegistroHoraExtra requireRegistro(Long registroId) {
        return registroHoraExtraRepo.findById(registroId)
                .orElseThrow(() -> new EntityNotFoundException("Registro de horas extra no encontrado: " + registroId));
    }

    private User requireGestor(User gestor) {
        if (gestor == null || gestor.getId() == null) {
            throw new IllegalArgumentException("Se requiere un usuario gestor válido.");
        }
        return gestor;
    }

    private void validateRequest(RegistroHoraExtraRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de horas extra es obligatoria.");
        }
        if (request.getFecha() == null) {
            throw new IllegalArgumentException("La fecha es obligatoria.");
        }
        if (request.getHoraInicio() == null || request.getHoraFin() == null) {
            throw new IllegalArgumentException("La hora de inicio y la hora de fin son obligatorias.");
        }
        if (!request.getHoraFin().isAfter(request.getHoraInicio())) {
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la hora de inicio.");
        }
        normalizeRequired(request.getMotivo(), "El motivo es obligatorio.");
    }

    private int calcularMinutos(RegistroHoraExtraRequestDTO request) {
        return Math.toIntExact(Duration.between(request.getHoraInicio(), request.getHoraFin()).toMinutes());
    }

    private void requireEstado(RegistroHoraExtra registro, RegistroHoraExtra.Estado esperado, String message) {
        if (registro.getEstado() != esperado) {
            throw new IllegalArgumentException(message);
        }
    }

    private void cambiarEstado(
            RegistroHoraExtra registro,
            RegistroHoraExtra.Estado estado,
            User gestor,
            String motivoRechazoOAnulacion
    ) {
        registro.setEstado(estado);
        registro.setAprobadoPor(gestor);
        registro.setFechaDecision(AppTime.now());
        registro.setMotivoRechazoOAnulacion(motivoRechazoOAnulacion);
        registroHoraExtraRepo.save(registro);
    }

    private void registrarDocumento(
            RegistroHoraExtra registro,
            DocTranDePersonal.TipoDocTran tipo,
            User gestor,
            String motivoDecision
    ) {
        String descripcion = switch (tipo) {
            case HORAS_EXTRA_REGISTRO -> "Registro de horas extra #" + registro.getId();
            case HORAS_EXTRA_APROBACION -> "Aprobación de horas extra #" + registro.getId();
            case HORAS_EXTRA_RECHAZO -> "Rechazo de horas extra #" + registro.getId();
            case HORAS_EXTRA_ANULACION -> "Anulación de horas extra #" + registro.getId();
            default -> "Evento de horas extra #" + registro.getId();
        };

        DocTranDePersonal documento = DocTranDePersonal.crearDocumentoModificacion(
                registro.getIntegrante(),
                tipo,
                descripcion,
                null,
                buildValoresNuevos(registro, motivoDecision),
                gestor.getUsername()
        );
        docTranDePersonalRepo.save(documento);
    }

    private String buildValoresNuevos(RegistroHoraExtra registro, String motivoDecision) {
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"registroHoraExtraId\":").append(registro.getId()).append(",")
                .append("\"estado\":\"").append(registro.getEstado()).append("\",")
                .append("\"minutos\":").append(registro.getMinutos());
        if (motivoDecision != null && !motivoDecision.isBlank()) {
            json.append(",\"motivoDecision\":\"").append(escapeJson(motivoDecision)).append("\"");
        }
        json.append("}");
        return json.toString();
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
