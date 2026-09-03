package exotic.app.planta.resource.calidad;

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
@RequestMapping("/api/calidad/controles-calidad")
@RequiredArgsConstructor
public class CalidadControlUnificadoResource {
    public static final String TAB_PLANES = "PLANES_CONTROL_CALIDAD";
    public static final String TAB_REGISTRO = "REGISTRAR_CONTROL_CALIDAD";
    public static final String TAB_DESVIACIONES = "DESVIACIONES_CONTROL_CALIDAD";
    public static final String TAB_HISTORIAL = "HISTORIAL_CONTROL_CALIDAD";

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
        return planService.listar(AmbitoControl.CALIDAD, search);
    }

    @GetMapping("/planes/{planId}")
    public PlanResponse detallePlan(Authentication auth, @PathVariable Long planId) {
        requirePlan(auth, 1);
        return planService.detalle(AmbitoControl.CALIDAD, planId);
    }

    @PostMapping("/planes")
    public PlanResponse crearPlan(Authentication auth, @Valid @RequestBody PlanWriteRequest request) {
        return planService.crear(AmbitoControl.CALIDAD, requirePlan(auth, 2), request);
    }

    @PutMapping("/planes/{planId}/borrador")
    public PlanResponse guardarBorrador(Authentication auth, @PathVariable Long planId,
                                         @Valid @RequestBody PlanWriteRequest request) {
        return planService.guardarBorrador(
                AmbitoControl.CALIDAD, requirePlan(auth, 2), planId, request);
    }

    @PostMapping("/planes/{planId}/versiones/{versionId}/publicar")
    public PlanResponse publicar(Authentication auth, @PathVariable Long planId, @PathVariable Long versionId) {
        return planService.publicar(
                AmbitoControl.CALIDAD, requirePlan(auth, 3), planId, versionId);
    }

    @PostMapping("/planes/{planId}/versiones/{versionId}/retirar")
    public PlanResponse retirar(Authentication auth, @PathVariable Long planId, @PathVariable Long versionId) {
        return planService.retirar(
                AmbitoControl.CALIDAD, requirePlan(auth, 3), planId, versionId);
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
                AmbitoControl.CALIDAD, loteId, batchRecordId, batchRecordEtapaId, areaId,
                tipoOrden, momento, estado, vencimientoDesde, vencimientoHasta, search, page, size);
    }

    @GetMapping("/lotes")
    public List<LoteControlResponse> lotes(
            Authentication auth, @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "20") int size) {
        accessGuard.requireAnyTabAccessWithSuperMasterBypass(auth, ModuloSistema.CALIDAD,
                Map.of(TAB_REGISTRO, 1, TAB_PLANES, 3),
                "Se requiere acceso a registro o administracion de planes para buscar lotes.");
        return workflowService.buscarLotes(search, size);
    }

    @PostMapping("/pendientes/independientes")
    public List<PendienteResponse> resolverIndependientes(
            Authentication auth, @Valid @RequestBody IndependienteWriteRequest request) {
        requireExact(auth, TAB_REGISTRO, 2);
        return workflowService.resolverIndependientes(AmbitoControl.CALIDAD, request.loteId())
                .stream().map(executionService::toPendiente).toList();
    }

    @PostMapping("/requisitos/excepcionales")
    public PendienteResponse agregarExcepcional(
            Authentication auth, HttpServletRequest servletRequest,
            @RequestHeader(ControlIdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody AdicionExcepcionalWriteRequest request) {
        User actor = requirePlan(auth, 3);
        return idempotencyService.ejecutar(
                actor, "ADICION_EXCEPCIONAL_CALIDAD",
                "batch-record/" + request.batchRecordId(), idempotencyKey, request,
                PendienteResponse.class,
                () -> executionService.toPendiente(excepcionalService.agregarFirmado(
                        AmbitoControl.CALIDAD, actor, request, servletRequest.getRemoteAddr(),
                        servletRequest.getHeader("User-Agent"))));
    }

    @GetMapping("/requisitos/excepcionales/opciones")
    public List<OpcionAdicionExcepcionalResponse> opcionesExcepcionales(
            Authentication auth, @RequestParam Long batchRecordId,
            @RequestParam(required = false) Long batchRecordEtapaId) {
        requirePlan(auth, 3);
        return workflowService.opcionesAdicionExcepcional(
                AmbitoControl.CALIDAD, batchRecordId, batchRecordEtapaId);
    }

    @GetMapping("/requisitos/excepcionales/etapas")
    public List<EtapaAdicionExcepcionalResponse> etapasExcepcionales(
            Authentication auth, @RequestParam Long batchRecordId) {
        requirePlan(auth, 3);
        return workflowService.etapasAdicionExcepcional(AmbitoControl.CALIDAD, batchRecordId);
    }

    @PostMapping("/ejecuciones")
    public EjecucionDetalleResponse ejecutar(
            Authentication auth,
            @RequestHeader(ControlIdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody EjecucionWriteRequest request) {
        User actor = requireExact(auth, TAB_REGISTRO, 2);
        return idempotencyService.ejecutar(
                actor, "EJECUCION_CONTROL_CALIDAD",
                "control-requerido/" + request.controlRequeridoId(), idempotencyKey, request,
                EjecucionDetalleResponse.class,
                () -> executionService.ejecutar(AmbitoControl.CALIDAD, actor, request));
    }

    @PostMapping("/requisitos/{id}/revalidaciones")
    public RevalidacionResponse revalidar(
            Authentication auth, @PathVariable Long id,
            @RequestHeader(ControlIdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody RevalidacionWriteRequest request) {
        User actor = requireExact(auth, TAB_REGISTRO, 2);
        return idempotencyService.ejecutar(
                actor, "REVALIDACION_CONTROL_CALIDAD", "control-requerido/" + id,
                idempotencyKey, request, RevalidacionResponse.class,
                () -> executionService.revalidar(AmbitoControl.CALIDAD, id, actor, request));
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
                AmbitoControl.CALIDAD, loteId, batchRecordId, search, resultado,
                desde, hasta, page, size);
    }

    @GetMapping("/ejecuciones/{id}")
    public EjecucionDetalleResponse detalleEjecucion(Authentication auth, @PathVariable Long id) {
        requireExact(auth, TAB_HISTORIAL, 1);
        return executionService.detalle(AmbitoControl.CALIDAD, id);
    }

    @GetMapping("/desviaciones")
    public Page<DesviacionResponse> desviaciones(
            Authentication auth, @RequestParam(required = false) List<EstadoDesviacionControl> estado,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        requireExact(auth, TAB_DESVIACIONES, 1);
        return deviationService.listar(AmbitoControl.CALIDAD, estado, search, page, size);
    }

    @PostMapping("/desviaciones/{id}/resolver")
    public DesviacionResponse resolver(
            Authentication auth, @PathVariable Long id,
            @RequestHeader(ControlIdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody DesviacionResolveRequest request) {
        User actor = accessGuard.requireTabAccessWithoutMasterBypass(
                auth, ModuloSistema.CALIDAD, TAB_DESVIACIONES, 2,
                "Se requiere nivel 2 explicito para proponer la disposicion de una desviacion de Calidad.");
        return idempotencyService.ejecutar(
                actor, "RESOLUCION_DESVIACION_CALIDAD", "desviacion-control/" + id,
                idempotencyKey, request, DesviacionResponse.class,
                () -> deviationService.resolver(AmbitoControl.CALIDAD, id, actor, request));
    }

    @PostMapping("/desviaciones/{id}/cerrar")
    public DesviacionResponse cerrar(
            Authentication auth, @PathVariable Long id,
            @RequestHeader(ControlIdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody DesviacionCloseRequest request) {
        User actor = accessGuard.requireTabAccessWithoutMasterBypass(auth, ModuloSistema.CALIDAD,
                TAB_DESVIACIONES, 3, "Se requiere nivel 3 explicito para disponer una desviacion de Calidad.");
        return idempotencyService.ejecutar(
                actor, "CIERRE_DESVIACION_CALIDAD", "desviacion-control/" + id,
                idempotencyKey, request, DesviacionResponse.class,
                () -> deviationService.cerrar(AmbitoControl.CALIDAD, id, actor, request));
    }

    private User requirePlan(Authentication auth, int nivel) {
        return accessGuard.requireTabAccessWithSuperMasterBypass(
                auth, ModuloSistema.CALIDAD, TAB_PLANES, nivel,
                "No tiene el nivel requerido para administrar planes de Calidad.");
    }

    private User requireExact(Authentication auth, String tab, int nivel) {
        return accessGuard.requireTabAccessWithoutMasterBypass(
                auth, ModuloSistema.CALIDAD, tab, nivel,
                "No tiene el nivel explicito requerido para operar controles de Calidad.");
    }
}
