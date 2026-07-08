package exotic.app.planta.service.rehumanos;

import exotic.app.planta.model.organizacion.personal.DocTranDePersonal;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.organizacion.personal.dto.IntegrantePersonalDetalleDTO;
import exotic.app.planta.model.organizacion.personal.dto.IntegrantePersonalRequestDTO;
import exotic.app.planta.model.organizacion.personal.dto.IntegrantePersonalResumenDTO;
import exotic.app.planta.repo.personal.DocTranDePersonalRepo;
import exotic.app.planta.repo.personal.IntegrantePersonalRepo;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for managing IntegrantePersonal entities
 */
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class IntegrantePersonalService {

    private final IntegrantePersonalRepo integrantePersonalRepo;
    private final DocTranDePersonalRepo docTranDePersonalRepo;

    /**
     * Save a new IntegrantePersonal entity
     *
     * @param request The IntegrantePersonal data to save
     * @param usuarioResponsable The username of the user who is creating the record
     * @return The saved IntegrantePersonal entity
     */
    @Transactional
    public IntegrantePersonalDetalleDTO saveIntegrantePersonal(
            IntegrantePersonalRequestDTO request,
            String usuarioResponsable
    ) {
        validateRequest(request, true);
        if (integrantePersonalRepo.existsById(request.getId())) {
            throw new IllegalArgumentException("Ya existe un Integrante de Personal con el ID " + request.getId());
        }

        IntegrantePersonal integrantePersonal = new IntegrantePersonal();
        integrantePersonal.setId(request.getId());
        applyRequest(integrantePersonal, request, true);
        IntegrantePersonal savedIntegrante = integrantePersonalRepo.save(integrantePersonal);

        DocTranDePersonal documento = DocTranDePersonal.crearDocumentoIngreso(savedIntegrante, usuarioResponsable);
        docTranDePersonalRepo.save(documento);
        return IntegrantePersonalDetalleDTO.fromEntity(savedIntegrante, documento.getFechaHora());
    }

    /**
     * Find an IntegrantePersonal by ID
     *
     * @param id The ID of the IntegrantePersonal to find
     * @return An Optional containing the IntegrantePersonal if found, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<IntegrantePersonalDetalleDTO> findById(Long id) {
        return integrantePersonalRepo.findById(id).map(this::toDetalleDto);
    }

    /**
     * Find IntegrantePersonal entities by name or surname
     *
     * @param searchText The text to search for in names or surnames
     * @param page The page number
     * @param size The page size
     * @return A page of IntegrantePersonal entities matching the search criteria
     */
    @Transactional(readOnly = true)
    public Page<IntegrantePersonalResumenDTO> searchIntegrantes(String searchText, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return integrantePersonalRepo.findByNombresOrApellidosContainingIgnoreCase(normalizeOptional(searchText), pageable)
                .map(IntegrantePersonalResumenDTO::fromEntity);
    }

    /**
     * Find IntegrantePersonal entities by department
     *
     * @param departamento The department to search for
     * @return A list of IntegrantePersonal entities in the specified department
     */
    @Transactional(readOnly = true)
    public List<IntegrantePersonalResumenDTO> findByDepartamento(IntegrantePersonal.Departamento departamento) {
        return integrantePersonalRepo.findByDepartamento(departamento)
                .stream()
                .map(IntegrantePersonalResumenDTO::fromEntity)
                .toList();
    }

    /**
     * Find IntegrantePersonal entities by status
     *
     * @param estado The status to search for
     * @return A list of IntegrantePersonal entities with the specified status
     */
    @Transactional(readOnly = true)
    public List<IntegrantePersonalResumenDTO> findByEstado(IntegrantePersonal.Estado estado) {
        return integrantePersonalRepo.findByEstado(estado)
                .stream()
                .map(IntegrantePersonalResumenDTO::fromEntity)
                .toList();
    }

    @Transactional
    public IntegrantePersonalDetalleDTO updateIntegrantePersonal(
            Long id,
            IntegrantePersonalRequestDTO request,
            String usuarioResponsable
    ) {
        validateRequest(request, false);
        if (request.getId() != null && !Objects.equals(request.getId(), id)) {
            throw new IllegalArgumentException("La cédula del cuerpo no coincide con la cédula de la ruta.");
        }
        IntegrantePersonal integrante = integrantePersonalRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Integrante de personal no encontrado: " + id));

        Map<String, Object> valoresAnteriores = new LinkedHashMap<>();
        Map<String, Object> valoresNuevos = new LinkedHashMap<>();
        trackChanges(integrante, request, valoresAnteriores, valoresNuevos);
        applyRequest(integrante, request, false);

        IntegrantePersonal saved = integrantePersonalRepo.save(integrante);
        if (!valoresNuevos.isEmpty()) {
            DocTranDePersonal documento = DocTranDePersonal.crearDocumentoModificacion(
                    saved,
                    DocTranDePersonal.TipoDocTran.MODIFICACION_DATOS_PERSONALES,
                    "Actualización de datos de integrante de personal",
                    toJson(valoresAnteriores),
                    toJson(valoresNuevos),
                    usuarioResponsable
            );
            docTranDePersonalRepo.save(documento);
        }

        return toDetalleDto(saved);
    }

    private void validateRequest(IntegrantePersonalRequestDTO request, boolean requireId) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de integrante de personal es obligatoria.");
        }
        if (requireId && request.getId() == null) {
            throw new IllegalArgumentException("La cédula es obligatoria.");
        }
        normalizeRequired(request.getNombres(), "Los nombres son obligatorios.");
        normalizeRequired(request.getApellidos(), "Los apellidos son obligatorios.");
        validatePhone(normalizeRequired(request.getCelular(), "El celular es obligatorio."), "El celular no es válido.");
        normalizeRequired(request.getDireccion(), "La dirección es obligatoria.");
        if (request.getFechaIngreso() == null) {
            throw new IllegalArgumentException("La fecha de ingreso es obligatoria.");
        }
        String emergencia = normalizeOptional(request.getCelularContactoEmergencia());
        if (emergencia != null) {
            validatePhone(emergencia, "El celular de emergencia no es válido.");
        }
        if (request.getNumeroHijos() != null && request.getNumeroHijos() < 0) {
            throw new IllegalArgumentException("El número de hijos no puede ser negativo.");
        }
        if (request.getEmail() != null && normalizeOptional(request.getEmail()) != null
                && !normalizeOptional(request.getEmail()).matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("El correo electrónico no tiene un formato válido.");
        }
        if (request.getSalario() != null && request.getSalario() < 0) {
            throw new IllegalArgumentException("El salario no puede ser negativo.");
        }
    }

    private void applyRequest(IntegrantePersonal integrante, IntegrantePersonalRequestDTO request, boolean creating) {
        integrante.setNombres(normalizeRequired(request.getNombres(), "Los nombres son obligatorios."));
        integrante.setApellidos(normalizeRequired(request.getApellidos(), "Los apellidos son obligatorios."));
        integrante.setCelular(normalizeRequired(request.getCelular(), "El celular es obligatorio."));
        integrante.setDireccion(normalizeRequired(request.getDireccion(), "La dirección es obligatoria."));
        integrante.setEmail(normalizeOptional(request.getEmail()));
        integrante.setNombreContactoEmergencia(normalizeOptional(request.getNombreContactoEmergencia()));
        integrante.setCelularContactoEmergencia(normalizeOptional(request.getCelularContactoEmergencia()));
        integrante.setEstadoCivil(request.getEstadoCivil());
        integrante.setNumeroHijos(request.getNumeroHijos());
        integrante.setFechaIngreso(request.getFechaIngreso());
        integrante.setNumeroCuentaBancaria(normalizeOptional(request.getNumeroCuentaBancaria()));
        integrante.setBanco(normalizeOptional(request.getBanco()));
        integrante.setCargo(normalizeOptional(request.getCargo()));
        integrante.setDepartamento(request.getDepartamento());
        integrante.setCentroDeCosto(normalizeOptional(request.getCentroDeCosto()));
        integrante.setCentroDeProduccion(normalizeOptional(request.getCentroDeProduccion()));
        integrante.setSalario(request.getSalario() != null ? request.getSalario() : 0);
        if (request.getEstado() != null) {
            integrante.setEstado(request.getEstado());
        } else if (creating || integrante.getEstado() == null) {
            integrante.setEstado(IntegrantePersonal.Estado.ACTIVO);
        }
    }

    private IntegrantePersonalDetalleDTO toDetalleDto(IntegrantePersonal integrante) {
        return IntegrantePersonalDetalleDTO.fromEntity(integrante, fechaRegistroIngreso(integrante.getId()));
    }

    private LocalDateTime fechaRegistroIngreso(Long integranteId) {
        return docTranDePersonalRepo.findFirstByIdIntegrante_IdAndTipoDocTranOrderByFechaHoraAsc(
                        integranteId,
                        DocTranDePersonal.TipoDocTran.INGRESO
                )
                .map(DocTranDePersonal::getFechaHora)
                .orElse(null);
    }

    private void trackChanges(
            IntegrantePersonal current,
            IntegrantePersonalRequestDTO request,
            Map<String, Object> anteriores,
            Map<String, Object> nuevos
    ) {
        track(anteriores, nuevos, "nombres", current.getNombres(), normalizeOptional(request.getNombres()));
        track(anteriores, nuevos, "apellidos", current.getApellidos(), normalizeOptional(request.getApellidos()));
        track(anteriores, nuevos, "celular", current.getCelular(), normalizeOptional(request.getCelular()));
        track(anteriores, nuevos, "direccion", current.getDireccion(), normalizeOptional(request.getDireccion()));
        track(anteriores, nuevos, "email", current.getEmail(), normalizeOptional(request.getEmail()));
        track(anteriores, nuevos, "nombreContactoEmergencia", current.getNombreContactoEmergencia(), normalizeOptional(request.getNombreContactoEmergencia()));
        track(anteriores, nuevos, "celularContactoEmergencia", current.getCelularContactoEmergencia(), normalizeOptional(request.getCelularContactoEmergencia()));
        track(anteriores, nuevos, "estadoCivil", current.getEstadoCivil(), request.getEstadoCivil());
        track(anteriores, nuevos, "numeroHijos", current.getNumeroHijos(), request.getNumeroHijos());
        track(anteriores, nuevos, "fechaIngreso", current.getFechaIngreso(), request.getFechaIngreso());
        track(anteriores, nuevos, "numeroCuentaBancaria", current.getNumeroCuentaBancaria(), normalizeOptional(request.getNumeroCuentaBancaria()));
        track(anteriores, nuevos, "banco", current.getBanco(), normalizeOptional(request.getBanco()));
        track(anteriores, nuevos, "cargo", current.getCargo(), normalizeOptional(request.getCargo()));
        track(anteriores, nuevos, "departamento", current.getDepartamento(), request.getDepartamento());
        track(anteriores, nuevos, "centroDeCosto", current.getCentroDeCosto(), normalizeOptional(request.getCentroDeCosto()));
        track(anteriores, nuevos, "centroDeProduccion", current.getCentroDeProduccion(), normalizeOptional(request.getCentroDeProduccion()));
        track(anteriores, nuevos, "salario", current.getSalario(), request.getSalario() != null ? request.getSalario() : 0);
        track(anteriores, nuevos, "estado", current.getEstado(), request.getEstado() != null ? request.getEstado() : current.getEstado());
    }

    private void track(
            Map<String, Object> anteriores,
            Map<String, Object> nuevos,
            String field,
            Object oldValue,
            Object newValue
    ) {
        if (!Objects.equals(oldValue, newValue)) {
            anteriores.put(field, oldValue);
            nuevos.put(field, newValue);
        }
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

    private void validatePhone(String value, String message) {
        if (!value.matches("^\\+?\\d{7,}$")) {
            throw new IllegalArgumentException(message);
        }
    }

    private String toJson(Map<String, Object> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append("\"").append(escapeJson(entry.getKey())).append("\":");
            appendJsonValue(json, entry.getValue());
        }
        json.append("}");
        return json.toString();
    }

    private void appendJsonValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else if (value instanceof Enum<?> enumValue) {
            json.append("\"").append(enumValue.name()).append("\"");
        } else {
            json.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
