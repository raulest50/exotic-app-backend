package exotic.app.planta.resource.inventarios;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.inventarios.dto.*;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.inventarios.DispensacionV2MpsService;
import exotic.app.planta.service.inventarios.DispensacionV2WorkflowService;
import exotic.app.planta.service.inventarios.SalidaAlmacenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/salidas_almacen")
@RequiredArgsConstructor
@Slf4j
public class SalidaAlmacenResource {

    private final SalidaAlmacenService salidaAlmacenService;
    private final DispensacionV2MpsService dispensacionV2MpsService;
    private final DispensacionV2WorkflowService dispensacionV2WorkflowService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/dispensacion-v2/ordenes-fabricacion")
    public ResponseEntity<List<DispensacionV2OrdenFabricacionDTOs.Option>>
    buscarOrdenesFabricacionDispensacionV2(
            Authentication authentication,
            @RequestParam int areaId,
            @RequestParam(defaultValue = "") String search
    ) {
        User currentUser = getCurrentUser(authentication);
        requireDispensacionV2Access(currentUser);
        return ResponseEntity.ok(dispensacionV2WorkflowService
                .buscarOrdenesFabricacion(areaId, search));
    }

    @GetMapping("/dispensacion-v2/ordenes-fabricacion/{ordenFabricacionId}/preparacion")
    public ResponseEntity<DispensacionV2OrdenFabricacionDTOs.PreparationResponse>
    prepararOrdenFabricacionDispensacionV2(
            Authentication authentication,
            @PathVariable Long ordenFabricacionId,
            @RequestParam int areaId
    ) {
        User currentUser = getCurrentUser(authentication);
        requireDispensacionV2Access(currentUser);
        return ResponseEntity.ok(dispensacionV2WorkflowService
                .prepararOrdenFabricacion(ordenFabricacionId, areaId));
    }

    @PostMapping("/dispensacion-v2/ordenes-fabricacion/{ordenFabricacionId}/asignacion-lotes")
    public ResponseEntity<DispensacionV2OrdenFabricacionDTOs.PreparationResponse>
    asignarLotesOrdenFabricacionDispensacionV2(
            Authentication authentication,
            @PathVariable Long ordenFabricacionId,
            @RequestBody DispensacionV2OrdenFabricacionDTOs.AssignmentRequest request
    ) {
        User currentUser = getCurrentUser(authentication);
        requireDispensacionV2Access(currentUser);
        return ResponseEntity.ok(dispensacionV2WorkflowService
                .asignarLotesOrdenFabricacion(ordenFabricacionId, request));
    }

    @PostMapping("/dispensacion-v2/ordenes-fabricacion/{ordenFabricacionId}/finalizar")
    public ResponseEntity<DispensacionV2OrdenFabricacionDTOs.FinalizationResponse>
    finalizarOrdenFabricacionDispensacionV2(
            Authentication authentication,
            @PathVariable Long ordenFabricacionId,
            @RequestBody DispensacionV2OrdenFabricacionDTOs.FinalizationRequest request
    ) {
        User currentUser = getCurrentUser(authentication);
        requireDispensacionV2Access(currentUser);
        return ResponseEntity.ok(dispensacionV2WorkflowService
                .finalizarOrdenFabricacion(ordenFabricacionId, request, currentUser));
    }

    @GetMapping("/dispensacion-v2/mps-semanal")
    public ResponseEntity<MpsSemanalDraftDTO> getDispensacionV2MpsSemanal(
            Authentication authentication,
            @RequestParam LocalDate weekStartDate,
            @RequestParam int areaId
    ) {
        return executeDispensacionV2Diagnostic(
                "mps-semanal",
                authentication,
                Map.of("weekStartDate", weekStartDate, "areaId", areaId),
                () -> {
                    User currentUser = getCurrentUser(authentication);
                    requireDispensacionV2Access(currentUser);
                    MpsSemanalDraftDTO response = dispensacionV2MpsService
                            .getMpsSemanalFiltradoPorArea(weekStartDate, areaId);
                    return ResponseEntity.ok(response);
                }
        );
    }

    @PostMapping("/dispensacion-v2/preparacion")
    public ResponseEntity<DispensacionV2PreparacionResponseDTO> prepararDispensacionV2(
            Authentication authentication,
            @RequestBody DispensacionV2PreparacionRequestDTO request
    ) {
        return executeDispensacionV2Diagnostic(
                "preparacion",
                authentication,
                request,
                () -> {
                    User currentUser = getCurrentUser(authentication);
                    requireDispensacionV2Access(currentUser);
                    return ResponseEntity.ok(dispensacionV2WorkflowService.preparar(request));
                }
        );
    }

    @PostMapping("/dispensacion-v2/materiales-receta")
    public ResponseEntity<DispensacionV2MaterialesRecetaResponseDTO> prepararMaterialesRecetaDispensacionV2(
            Authentication authentication,
            @RequestBody DispensacionV2MaterialesRecetaRequestDTO request
    ) {
        return executeDispensacionV2Diagnostic(
                "materiales-receta",
                authentication,
                request,
                () -> {
                    User currentUser = getCurrentUser(authentication);
                    requireDispensacionV2Access(currentUser);
                    return ResponseEntity.ok(dispensacionV2WorkflowService.prepararMaterialesReceta(request));
                }
        );
    }

    @PostMapping("/dispensacion-v2/asignacion-lotes")
    public ResponseEntity<DispensacionV2PreparacionResponseDTO> asignarLotesDispensacionV2(
            Authentication authentication,
            @RequestBody DispensacionV2AsignacionLotesRequestDTO request
    ) {
        return executeDispensacionV2Diagnostic(
                "asignacion-lotes",
                authentication,
                request,
                () -> {
                    User currentUser = getCurrentUser(authentication);
                    requireDispensacionV2Access(currentUser);
                    return ResponseEntity.ok(dispensacionV2WorkflowService.asignarLotes(request));
                }
        );
    }

    @PostMapping("/dispensacion-v2/finalizar")
    public ResponseEntity<DispensacionV2FinalizacionResponseDTO> finalizarDispensacionV2(
            Authentication authentication,
            @RequestBody DispensacionV2FinalizacionRequestDTO request
    ) {
        return executeDispensacionV2Diagnostic(
                "finalizar",
                authentication,
                request,
                () -> {
                    User currentUser = getCurrentUser(authentication);
                    requireDispensacionV2Access(currentUser);
                    return ResponseEntity.ok(dispensacionV2WorkflowService.finalizar(request, currentUser));
                }
        );
    }

    @GetMapping("/dispensacion-v2/materiales/{productoId}/lotes-disponibles")
    public ResponseEntity<LoteDisponiblePageResponseDTO> getLotesDisponiblesDispensacionV2(
            Authentication authentication,
            @PathVariable String productoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return executeDispensacionV2Diagnostic(
                "lotes-disponibles",
                authentication,
                Map.of("productoId", productoId, "page", page, "size", size),
                () -> {
                    User currentUser = getCurrentUser(authentication);
                    requireDispensacionV2Access(currentUser);
                    return ResponseEntity.ok(
                            dispensacionV2WorkflowService.getLotesDisponiblesV2(productoId, page, size)
                    );
                }
        );
    }

    /**
     * Endpoint para buscar dispensaciones con filtros flexibles.
     * Permite filtrar por ID de transacción, ID de orden de producción, lote de producción,
     * producto terminado, y fechas (rango o específica).
     * Retorna DTOs para evitar problemas de serialización JSON con relaciones circulares.
     *
     * @param filtro DTO con los criterios de búsqueda
     * @return Página de DTOs de transacciones que cumplen con los filtros
     */
    @PostMapping("/historial_dispensacion_filter")
    public ResponseEntity<Page<TransaccionAlmacenResponseDTO>> buscarDispensacionesFiltradas(
            @RequestBody FiltroHistDispensacionDTO filtro) {
        Page<TransaccionAlmacenResponseDTO> resultados = salidaAlmacenService.buscarDispensacionesFiltradasDTO(filtro);
        return ResponseEntity.ok(resultados);
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }

    private void requireDispensacionV2Access(User user) {
        if (isMasterLike(user.getUsername())) {
            return;
        }

        boolean hasTabAccess = UserAccessEvaluator
                .tabNivel(user, ModuloSistema.TRANSACCIONES_ALMACEN, "DISPENSACION_V2")
                .orElse(0) >= 1;

        if (!hasTabAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para hacer dispensacion.");
        }
    }

    private boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return "master".equals(normalized) || "super_master".equals(normalized);
    }

    private <T> ResponseEntity<T> executeDispensacionV2Diagnostic(
            String step,
            Authentication authentication,
            Object payload,
            Supplier<ResponseEntity<T>> operation
    ) {
        if (!log.isErrorEnabled()) {
            return operation.get();
        }

        String diagnosticId = UUID.randomUUID().toString();
        String previousDiagnosticId = MDC.get("dispensacionV2TraceId");
        long startedAt = System.nanoTime();
        MDC.put("dispensacionV2TraceId", diagnosticId);

        log.info(
                "[DISP_V2][REQUEST] diagnosticId={} step={} username={} payload={}",
                diagnosticId,
                step,
                resolveUsername(authentication),
                toDiagnosticJson(payload)
        );

        try {
            ResponseEntity<T> response = operation.get();
            double durationMs = (System.nanoTime() - startedAt) / 1_000_000.0;
            log.info(
                    "[DISP_V2][RESPONSE] diagnosticId={} step={} status={} durationMs={} body={}",
                    diagnosticId,
                    step,
                    response.getStatusCode().value(),
                    durationMs,
                    toDiagnosticJson(response.getBody())
            );
            return response;
        } catch (ResponseStatusException exception) {
            double durationMs = (System.nanoTime() - startedAt) / 1_000_000.0;
            log.warn(
                    "[DISP_V2][REJECTED] diagnosticId={} step={} status={} reason={} durationMs={} payload={}",
                    diagnosticId,
                    step,
                    exception.getStatusCode().value(),
                    exception.getReason(),
                    durationMs,
                    toDiagnosticJson(payload),
                    exception
            );
            throw exception;
        } catch (RuntimeException exception) {
            double durationMs = (System.nanoTime() - startedAt) / 1_000_000.0;
            log.error(
                    "[DISP_V2][ERROR] diagnosticId={} step={} exceptionType={} message={} durationMs={} payload={}",
                    diagnosticId,
                    step,
                    exception.getClass().getName(),
                    exception.getMessage(),
                    durationMs,
                    toDiagnosticJson(payload),
                    exception
            );
            throw exception;
        } finally {
            if (previousDiagnosticId == null) {
                MDC.remove("dispensacionV2TraceId");
            } else {
                MDC.put("dispensacionV2TraceId", previousDiagnosticId);
            }
        }
    }

    private String resolveUsername(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private String toDiagnosticJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "[DISP_V2][SERIALIZATION_WARNING] type={} message={}",
                    value.getClass().getName(),
                    exception.getMessage()
            );
            return String.valueOf(value);
        }
    }


}
