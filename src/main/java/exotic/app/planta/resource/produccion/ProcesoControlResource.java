package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.controles.dto.ControlDTOs.*;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.controles.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/produccion/controles-proceso")
@RequiredArgsConstructor
public class ProcesoControlResource {
    public static final String TAB_PLANES = "PLANES_CONTROL_PROCESO";
    public static final String TAB_REGISTRO = "REGISTRAR_CONTROL_PROCESO";
    public static final String TAB_DESVIACIONES = "DESVIACIONES_CONTROL_PROCESO";
    public static final String TAB_HISTORIAL = "HISTORIAL_CONTROL_PROCESO";

    private final ControlPlanService planService;
    private final ControlExecutionService executionService;
    private final ControlDeviationService deviationService;
    private final ControlWorkflowService workflowService;
    private final ControlRequeridoExcepcionalService excepcionalService;
    private final ControlIdempotencyService idempotencyService;
    private final ModuleTabAccessGuard accessGuard;

    @GetMapping("/planes")
    public List<PlanResponse> listarPlanes(
            Authentication auth, @RequestParam(required = false) String search) {
        requirePlan(auth, 1);
        return planService.listar(AmbitoControl.PROCESO, search);
    }

    @GetMapping("/planes/{planId}")
    public PlanResponse detallePlan(Authentication auth, @PathVariable Long planId) {
        requirePlan(auth, 1);
        return planService.detalle(AmbitoControl.PROCESO, planId);
    }

    @PostMapping("/planes")
    public PlanResponse crearPlan(Authentication auth, @Valid @RequestBody PlanWriteRequest request) {
        return planService.crear(AmbitoControl.PROCESO, requirePlan(auth, 2), request);
    }

    @PutMapping("/planes/{planId}/borrador")
    public PlanResponse guardarBorrador(Authentication auth, @PathVariable Long planId,
                                         @Valid @RequestBody PlanWriteRequest request) {
        return planService.guardarBorrador(
                AmbitoControl.PROCESO, requirePlan(auth, 2), planId, request);
    }

    @PostMapping("/planes/{planId}/versiones/{versionId}/publicar")
    public PlanResponse publicar(Authentication auth, @PathVariable Long planId, @PathVariable Long versionId) {
        return planService.publicar(
                AmbitoControl.PROCESO, requirePlan(auth, 3), planId, versionId);
    }

    @PostMapping("/planes/{planId}/versiones/{versionId}/retirar")
    public PlanResponse retirar(Authentication auth, @PathVariable Long planId, @PathVariable Long versionId) {
        return planService.retirar(
                AmbitoControl.PROCESO, requirePlan(auth, 3), planId, versionId);
    }

    @GetMapping("/pendientes")
    public Page<PendienteResponse> pendientes(
            Authentication auth, @RequestParam(required = false) Long loteId,
            @RequestParam(required = false) Long batchRecordId,
            @RequestParam(required = false) Long batchRecordEtapaId,
            @RequestParam(required = false) Integer areaId,
            @RequestParam(required = false) TipoOrdenControl tipoOrden,
            @RequestParam(required = false) MomentoControl momento,
            @RequestParam(required = false) List<EstadoControlRequerido> estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate vencimientoDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate vencimientoHasta,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        requireExact(auth, TAB_REGISTRO, 1);
        return executionService.pendientes(
                AmbitoControl.PROCESO, loteId, batchRecordId, batchRecordEtapaId, areaId,
                tipoOrden, momento, estado, vencimientoDesde, vencimientoHasta, search, page, size);
    }

    @GetMapping("/lotes")
    public List<LoteControlResponse> lotes(
            Authentication auth, @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "20") int size) {
        accessGuard.requireAnyTabAccessWithSuperMasterBypass(auth, ModuloSistema.PRODUCCION,
                Map.of(TAB_REGISTRO, 1, TAB_PLANES, 3),
                "Se requiere acceso a registro o administracion de planes para buscar lotes.");
        return workflowService.buscarLotes(search, size);
    }

    @PostMapping("/pendientes/independientes")
    public List<PendienteResponse> resolverIndependientes(
            Authentication auth, @Valid @RequestBody IndependienteWriteRequest request) {
        requireExact(auth, TAB_REGISTRO, 2);
        return workflowService.resolverIndependientes(AmbitoControl.PROCESO, request.loteId())
                .stream().map(executionService::toPendiente).toList();
    }

    @PostMapping("/requisitos/excepcionales")
    public PendienteResponse agregarExcepcional(
            Authentication auth, HttpServletRequest servletRequest,
            @RequestHeader(ControlIdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody AdicionExcepcionalWriteRequest request) {
        User actor = requirePlan(auth, 3);
        return idempotencyService.ejecutar(
                actor, "ADICION_EXCEPCIONAL_PROCESO",
                "batch-record/" + request.batchRecordId(), idempotencyKey, request,
                PendienteResponse.class,
                () -> executionService.toPendiente(excepcionalService.agregarFirmado(
                        AmbitoControl.PROCESO, actor, request, servletRequest.getRemoteAddr(),
                        servletRequest.getHeader("User-Agent"))));
    }

    @GetMapping("/requisitos/excepcionales/opciones")
    public List<OpcionAdicionExcepcionalResponse> opcionesExcepcionales(
            Authentication auth, @RequestParam Long batchRecordId,
            @RequestParam(required = false) Long batchRecordEtapaId) {
        requirePlan(auth, 3);
        return workflowService.opcionesAdicionExcepcional(
                AmbitoControl.PROCESO, batchRecordId, batchRecordEtapaId);
    }

    @GetMapping("/requisitos/excepcionales/etapas")
    public List<EtapaAdicionExcepcionalResponse> etapasExcepcionales(
            Authentication auth, @RequestParam Long batchRecordId) {
        requirePlan(auth, 3);
        return workflowService.etapasAdicionExcepcional(AmbitoControl.PROCESO, batchRecordId);
    }

    @PostMapping("/ejecuciones")
    public EjecucionDetalleResponse ejecutar(
            Authentication auth,
            @RequestHeader(ControlIdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody EjecucionWriteRequest request) {
        User actor = requireExact(auth, TAB_REGISTRO, 2);
        return idempotencyService.ejecutar(
                actor, "EJECUCION_CONTROL_PROCESO",
                "control-requerido/" + request.controlRequeridoId(), idempotencyKey, request,
                EjecucionDetalleResponse.class,
                () -> executionService.ejecutar(AmbitoControl.PROCESO, actor, request));
    }

    @GetMapping("/historial")
    public Page<EjecucionResumenResponse> historial(
            Authentication auth, @RequestParam(required = false) Long loteId,
            @RequestParam(required = false) Long batchRecordId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ResultadoEjecucionControl resultado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        requireExact(auth, TAB_HISTORIAL, 1);
        return executionService.historial(
                AmbitoControl.PROCESO, loteId, batchRecordId, search, resultado,
                desde, hasta, page, size);
    }

    @GetMapping("/ejecuciones/{id}")
    public EjecucionDetalleResponse detalleEjecucion(Authentication auth, @PathVariable Long id) {
        requireExact(auth, TAB_HISTORIAL, 1);
        return executionService.detalle(AmbitoControl.PROCESO, id);
    }

    @GetMapping("/desviaciones")
    public Page<DesviacionResponse> desviaciones(
            Authentication auth, @RequestParam(required = false) List<EstadoDesviacionControl> estado,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        requireExact(auth, TAB_DESVIACIONES, 1);
        return deviationService.listar(AmbitoControl.PROCESO, estado, search, page, size);
    }

    @PostMapping("/desviaciones/{id}/resolver")
    public DesviacionResponse resolver(
            Authentication auth, @PathVariable Long id,
            @RequestHeader(ControlIdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody DesviacionResolveRequest request) {
        User actor = accessGuard.requireTabAccessWithoutMasterBypass(
                auth, ModuloSistema.PRODUCCION, TAB_DESVIACIONES, 2,
                "Se requiere nivel 2 explicito para disponer una desviacion de proceso.");
        return idempotencyService.ejecutar(
                actor, "RESOLUCION_DESVIACION_PROCESO", "desviacion-control/" + id,
                idempotencyKey, request, DesviacionResponse.class,
                () -> deviationService.resolver(AmbitoControl.PROCESO, id, actor, request));
    }

    private User requirePlan(Authentication auth, int nivel) {
        return accessGuard.requireTabAccessWithSuperMasterBypass(
                auth, ModuloSistema.PRODUCCION, TAB_PLANES, nivel,
                "No tiene el nivel requerido para administrar planes de proceso.");
    }

    private User requireExact(Authentication auth, String tab, int nivel) {
        return accessGuard.requireTabAccessWithoutMasterBypass(
                auth, ModuloSistema.PRODUCCION, tab, nivel,
                "No tiene el nivel explicito requerido para operar controles de proceso.");
    }
}
