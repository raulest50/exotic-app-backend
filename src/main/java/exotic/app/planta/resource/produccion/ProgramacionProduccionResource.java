package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.dto.AprobarMpsSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.AtenderMpsSemanalObservacionRequestDTO;
import exotic.app.planta.model.produccion.dto.CrearMpsSemanalObservacionRequestDTO;
import exotic.app.planta.model.produccion.dto.GenerarOdpDesdeMpsRequestDTO;
import exotic.app.planta.model.produccion.dto.GenerarOdpDesdeMpsResponseDTO;
import exotic.app.planta.model.produccion.dto.GuardarProgramacionProduccionSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalListItemDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalObservacionDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalOrdenProduccionListItemDTO;
import exotic.app.planta.model.produccion.dto.SemanaMPSDTO;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalDraftNotFoundException;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalNotFoundException;
import exotic.app.planta.service.produccion.MasterProductionScheduleDraftService;
import exotic.app.planta.service.produccion.MasterProductionScheduleOrderGenerationService;
import exotic.app.planta.service.produccion.MpsSemanalObservacionService;
import exotic.app.planta.service.produccion.ProgramacionProduccionSemanalService;
import exotic.app.planta.service.produccion.SemanaMPSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/programacion_produccion")
@RequiredArgsConstructor
@Slf4j
public class ProgramacionProduccionResource {

    private final ProgramacionProduccionSemanalService programacionProduccionSemanalService;
    private final MasterProductionScheduleDraftService masterProductionScheduleDraftService;
    private final MasterProductionScheduleOrderGenerationService masterProductionScheduleOrderGenerationService;
    private final MpsSemanalObservacionService mpsSemanalObservacionService;
    private final SemanaMPSService semanaMPSService;
    private final UserRepository userRepository;

    @GetMapping("/mps-semanal/semanas")
    public ResponseEntity<?> listarSemanasMps(
            @RequestParam int anioSemana,
            Authentication authentication
    ) {
        String action = "listarSemanasMps";
        String user = authenticationName(authentication);
        String context = "anioSemana=" + anioSemana;
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            requireAnyTabAccess(authentication, "PROGRAMACION_PRODUCCION", "APROBACION_MPS_WEEK");
            List<SemanaMPSDTO> response = semanaMPSService.listIsoWeeksForYear(anioSemana);
            log.info("[MPS_SEMANAL] {} success user={} {} count={}", action, user, context, response.size());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    @PostMapping("/mps-semanal/borrador-directo")
    public ResponseEntity<?> guardarBorradorDirecto(
            @RequestBody GuardarProgramacionProduccionSemanalRequestDTO request,
            Authentication authentication
    ) {
        String action = "guardarBorradorDirecto";
        String user = authenticationName(authentication);
        String context = requestContext(request);
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            requireTabAccess(authentication, "PROGRAMACION_PRODUCCION");
            MpsSemanalDraftDTO response = programacionProduccionSemanalService.guardarBorradorDirecto(request);
            log.info(
                    "[MPS_SEMANAL] {} success user={} mpsId={} weekStartDate={} estado={} revision={} totalItems={} totalLotes={}",
                    action,
                    user,
                    response.getMpsId(),
                    response.getWeekStartDate(),
                    response.getEstado(),
                    response.getRevisionNumero(),
                    response.getTotalItems(),
                    response.getTotalLotesPlanificados()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (IllegalStateException e) {
            return controlledFailure(action, user, context, HttpStatus.CONFLICT, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    @PostMapping("/mps-semanal/borrador")
    public ResponseEntity<?> guardarBorradorMpsSemanal(
            @RequestBody GuardarProgramacionProduccionSemanalRequestDTO request,
            Authentication authentication
    ) {
        String action = "guardarBorradorMpsSemanal";
        String user = authenticationName(authentication);
        String context = requestContext(request);
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            requireTabAccess(authentication, "PROGRAMACION_PRODUCCION");
            MpsSemanalDraftDTO response = programacionProduccionSemanalService.guardarBorradorDirecto(request);
            log.info(
                    "[MPS_SEMANAL] {} success user={} mpsId={} weekStartDate={} estado={} revision={} totalItems={} totalLotes={}",
                    action,
                    user,
                    response.getMpsId(),
                    response.getWeekStartDate(),
                    response.getEstado(),
                    response.getRevisionNumero(),
                    response.getTotalItems(),
                    response.getTotalLotesPlanificados()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (IllegalStateException e) {
            return controlledFailure(action, user, context, HttpStatus.CONFLICT, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    @GetMapping("/mps-semanal/borrador")
    public ResponseEntity<?> obtenerBorradorMpsSemanal(
            @RequestParam LocalDate weekStartDate,
            Authentication authentication
    ) {
        String action = "obtenerBorradorMpsSemanal";
        String user = authenticationName(authentication);
        String context = "weekStartDate=" + weekStartDate;
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            requireTabAccess(authentication, "PROGRAMACION_PRODUCCION");
            MpsSemanalDraftDTO response = masterProductionScheduleDraftService.getDraftByWeekStartDate(weekStartDate);
            log.info("[MPS_SEMANAL] {} success user={} mpsId={} estado={} revision={} totalItems={} totalLotes={}",
                    action, user, response.getMpsId(), response.getEstado(), response.getRevisionNumero(), response.getTotalItems(), response.getTotalLotesPlanificados());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (MpsSemanalDraftNotFoundException e) {
            return controlledFailure(action, user, context, HttpStatus.NOT_FOUND, e);
        } catch (IllegalStateException e) {
            return controlledFailure(action, user, context, HttpStatus.CONFLICT, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    @GetMapping("/mps-semanal")
    public ResponseEntity<?> obtenerMpsSemanal(
            @RequestParam LocalDate weekStartDate,
            Authentication authentication
    ) {
        String action = "obtenerMpsSemanal";
        String user = authenticationName(authentication);
        String context = "weekStartDate=" + weekStartDate;
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            requireAnyTabAccess(authentication, "PROGRAMACION_PRODUCCION", "APROBACION_MPS_WEEK");
            MpsSemanalDraftDTO response = masterProductionScheduleDraftService.getByWeekStartDate(weekStartDate);
            log.info("[MPS_SEMANAL] {} success user={} mpsId={} estado={} revision={} totalItems={} totalLotes={} totalOdps={}",
                    action, user, response.getMpsId(), response.getEstado(), response.getRevisionNumero(), response.getTotalItems(), response.getTotalLotesPlanificados(), response.getTotalOdpsGeneradas());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (MpsSemanalNotFoundException e) {
            return controlledFailure(action, user, context, HttpStatus.NOT_FOUND, e);
        } catch (IllegalStateException e) {
            return controlledFailure(action, user, context, HttpStatus.CONFLICT, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    @GetMapping("/mps-semanal/list")
    public ResponseEntity<?> listarMpsSemanales(
            @RequestParam(required = false) EstadoMpsSemanal estado,
            Authentication authentication
    ) {
        String action = "listarMpsSemanales";
        String user = authenticationName(authentication);
        String context = "estado=" + estado;
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            requireTabAccess(authentication, "APROBACION_MPS_WEEK");
            List<MpsSemanalListItemDTO> response = masterProductionScheduleDraftService.listByEstado(estado);
            log.info("[MPS_SEMANAL] {} success user={} {} count={}", action, user, context, response.size());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (IllegalStateException e) {
            return controlledFailure(action, user, context, HttpStatus.CONFLICT, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    @GetMapping("/mps-semanal/observaciones")
    public ResponseEntity<?> listarObservacionesMpsSemanal(
            @RequestParam LocalDate weekStartDate,
            Authentication authentication
    ) {
        String action = "listarObservacionesMpsSemanal";
        String user = authenticationName(authentication);
        String context = "weekStartDate=" + weekStartDate;
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            requireAnyTabAccess(authentication, "PROGRAMACION_PRODUCCION", "APROBACION_MPS_WEEK");
            List<MpsSemanalObservacionDTO> response = mpsSemanalObservacionService.listarPorSemana(weekStartDate);
            log.info("[MPS_SEMANAL] {} success user={} {} count={}", action, user, context, response.size());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (MpsSemanalNotFoundException e) {
            return controlledFailure(action, user, context, HttpStatus.NOT_FOUND, e);
        } catch (IllegalStateException e) {
            return controlledFailure(action, user, context, HttpStatus.CONFLICT, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    @PostMapping("/mps-semanal/observaciones")
    public ResponseEntity<?> crearObservacionMpsSemanal(
            @RequestBody CrearMpsSemanalObservacionRequestDTO request,
            Authentication authentication
    ) {
        String action = "crearObservacionMpsSemanal";
        String user = authenticationName(authentication);
        String context = observacionCreateContext(request);
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            String username = requireAuthorizedUsername(authentication, "APROBACION_MPS_WEEK");
            MpsSemanalObservacionDTO response = mpsSemanalObservacionService.crearObservacion(request, username);
            log.info("[MPS_SEMANAL] {} success user={} observacionId={} mpsId={} weekStartDate={} tipo={} estado={}",
                    action, user, response.getObservacionId(), response.getMpsId(), response.getWeekStartDate(), response.getTipo(), response.getEstado());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (MpsSemanalNotFoundException e) {
            return controlledFailure(action, user, context, HttpStatus.NOT_FOUND, e);
        } catch (IllegalStateException e) {
            return controlledFailure(action, user, context, HttpStatus.CONFLICT, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    @PostMapping("/mps-semanal/observaciones/{observacionId}/atender")
    public ResponseEntity<?> atenderObservacionMpsSemanal(
            @PathVariable Long observacionId,
            @RequestBody AtenderMpsSemanalObservacionRequestDTO request,
            Authentication authentication
    ) {
        String action = "atenderObservacionMpsSemanal";
        String user = authenticationName(authentication);
        String context = "observacionId=" + observacionId + " respuestaPresent=" + hasText(request != null ? request.getRespuestaCorreccion() : null);
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            String username = requireAuthorizedUsername(authentication, "PROGRAMACION_PRODUCCION");
            MpsSemanalObservacionDTO response = mpsSemanalObservacionService.atenderObservacion(observacionId, request, username);
            log.info("[MPS_SEMANAL] {} success user={} observacionId={} mpsId={} weekStartDate={} estado={}",
                    action, user, response.getObservacionId(), response.getMpsId(), response.getWeekStartDate(), response.getEstado());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (MpsSemanalNotFoundException e) {
            return controlledFailure(action, user, context, HttpStatus.NOT_FOUND, e);
        } catch (IllegalStateException e) {
            return controlledFailure(action, user, context, HttpStatus.CONFLICT, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    @PostMapping("/mps-semanal/observaciones/{observacionId}/cerrar")
    public ResponseEntity<?> cerrarObservacionMpsSemanal(
            @PathVariable Long observacionId,
            Authentication authentication
    ) {
        String action = "cerrarObservacionMpsSemanal";
        String user = authenticationName(authentication);
        String context = "observacionId=" + observacionId;
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            String username = requireAuthorizedUsername(authentication, "APROBACION_MPS_WEEK");
            MpsSemanalObservacionDTO response = mpsSemanalObservacionService.cerrarObservacion(observacionId, username);
            log.info("[MPS_SEMANAL] {} success user={} observacionId={} mpsId={} weekStartDate={} estado={}",
                    action, user, response.getObservacionId(), response.getMpsId(), response.getWeekStartDate(), response.getEstado());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (MpsSemanalNotFoundException e) {
            return controlledFailure(action, user, context, HttpStatus.NOT_FOUND, e);
        } catch (IllegalStateException e) {
            return controlledFailure(action, user, context, HttpStatus.CONFLICT, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    @PostMapping("/mps-semanal/aprobar")
    public ResponseEntity<?> aprobarMpsSemanal(
            @RequestBody AprobarMpsSemanalRequestDTO request,
            Authentication authentication
    ) {
        String action = "aprobarMpsSemanal";
        String user = authenticationName(authentication);
        String context = "weekStartDate=" + (request != null ? request.getWeekStartDate() : null);
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            String username = requireAuthorizedUsername(authentication, "APROBACION_MPS_WEEK");
            MpsSemanalDraftDTO response = masterProductionScheduleDraftService.approveWeek(request, username);
            log.info("[MPS_SEMANAL] {} success user={} mpsId={} weekStartDate={} estado={} revision={} totalLotes={}",
                    action, user, response.getMpsId(), response.getWeekStartDate(), response.getEstado(), response.getRevisionNumero(), response.getTotalLotesPlanificados());
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return controlledFailure(action, user, context, HttpStatus.UNAUTHORIZED, e);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (MpsSemanalNotFoundException e) {
            return controlledFailure(action, user, context, HttpStatus.NOT_FOUND, e);
        } catch (IllegalStateException e) {
            return controlledFailure(action, user, context, HttpStatus.CONFLICT, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    @PostMapping("/mps-semanal/generar-odps")
    public ResponseEntity<?> generarOdpsDesdeMpsSemanal(
            @RequestBody GenerarOdpDesdeMpsRequestDTO request,
            Authentication authentication
    ) {
        String action = "generarOdpsDesdeMpsSemanal";
        String user = authenticationName(authentication);
        String context = "weekStartDate=" + (request != null ? request.getWeekStartDate() : null);
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            String username = requireAuthorizedUsername(authentication, "APROBACION_MPS_WEEK");
            GenerarOdpDesdeMpsResponseDTO response = masterProductionScheduleOrderGenerationService.generarOrdenesDesdeSemanaAprobada(request, username);
            log.info("[MPS_SEMANAL] {} success user={} mpsId={} weekStartDate={} totalBloques={} totalLotes={} totalOrdenes={}",
                    action, user, response.getMpsId(), response.getWeekStartDate(), response.getTotalBloquesProgramados(), response.getTotalLotesProgramados(), response.getTotalOrdenesCreadas());
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return controlledFailure(action, user, context, HttpStatus.UNAUTHORIZED, e);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (MpsSemanalNotFoundException e) {
            return controlledFailure(action, user, context, HttpStatus.NOT_FOUND, e);
        } catch (IllegalStateException e) {
            return controlledFailure(action, user, context, HttpStatus.CONFLICT, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    @GetMapping("/mps-semanal/odps")
    public ResponseEntity<?> obtenerOdpsDesdeMpsSemanal(
            @RequestParam LocalDate weekStartDate,
            Authentication authentication
    ) {
        String action = "obtenerOdpsDesdeMpsSemanal";
        String user = authenticationName(authentication);
        String context = "weekStartDate=" + weekStartDate;
        try {
            log.info("[MPS_SEMANAL] {} start user={} {}", action, user, context);
            requireTabAccess(authentication, "APROBACION_MPS_WEEK");
            List<MpsSemanalOrdenProduccionListItemDTO> response = masterProductionScheduleOrderGenerationService.getOrdenesGeneradasPorSemana(weekStartDate);
            log.info("[MPS_SEMANAL] {} success user={} {} count={}", action, user, context, response.size());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return controlledFailure(action, user, context, HttpStatus.BAD_REQUEST, e);
        } catch (MpsSemanalNotFoundException e) {
            return controlledFailure(action, user, context, HttpStatus.NOT_FOUND, e);
        } catch (ResponseStatusException e) {
            accessFailure(action, user, context, e);
            throw e;
        } catch (RuntimeException e) {
            unexpectedFailure(action, user, context, e);
            throw e;
        }
    }

    private ResponseEntity<?> controlledFailure(String action, String user, String context, HttpStatus status, RuntimeException e) {
        log.warn("[MPS_SEMANAL] {} controlled_failure status={} user={} {} message={}", action, status.value(), user, context, e.getMessage());
        return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
    }

    private void accessFailure(String action, String user, String context, ResponseStatusException e) {
        log.warn("[MPS_SEMANAL] {} access_failure status={} user={} {} reason={}", action, e.getStatusCode(), user, context, e.getReason());
    }

    private void unexpectedFailure(String action, String user, String context, RuntimeException e) {
        log.error("[MPS_SEMANAL] {} unexpected_error user={} {} message={}", action, user, context, e.getMessage(), e);
    }

    private String authenticationName(Authentication authentication) {
        return authentication != null && hasText(authentication.getName())
                ? authentication.getName().trim()
                : "<anonymous>";
    }

    private String requestContext(GuardarProgramacionProduccionSemanalRequestDTO request) {
        if (request == null) {
            return "weekStartDate=<null> dias=0 items=0 totalLotes=0";
        }
        int diasCount = request.getDias() != null ? request.getDias().size() : 0;
        int itemsCount = 0;
        int totalLotes = 0;
        if (request.getDias() != null) {
            for (var dia : request.getDias()) {
                if (dia == null || dia.getItems() == null) {
                    continue;
                }
                itemsCount += dia.getItems().size();
                for (var item : dia.getItems()) {
                    if (item != null) {
                        totalLotes += item.getNumeroLotes();
                    }
                }
            }
        }
        return "weekStartDate=" + request.getWeekStartDate()
                + " dias=" + diasCount
                + " items=" + itemsCount
                + " totalLotes=" + totalLotes;
    }

    private String observacionCreateContext(CrearMpsSemanalObservacionRequestDTO request) {
        if (request == null) {
            return "weekStartDate=<null> tipo=<null> mensajePresent=false";
        }
        return "weekStartDate=" + request.getWeekStartDate()
                + " tipo=" + request.getTipo()
                + " mensajePresent=" + hasText(request.getMensaje());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void requireTabAccess(Authentication authentication, String tabId) {
        requireAuthorizedUsername(authentication, tabId);
    }

    private void requireAnyTabAccess(Authentication authentication, String... tabIds) {
        requireAuthorizedUsername(authentication, tabIds);
    }

    private String requireAuthorizedUsername(Authentication authentication, String... tabIds) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

        if (isMasterLike(user.getUsername())) {
            return user.getUsername();
        }

        for (String tabId : tabIds) {
            boolean hasTabAccess = UserAccessEvaluator.tabNivel(user, ModuloSistema.PRODUCCION, tabId)
                    .orElse(0) >= 1;
            if (hasTabAccess) {
                return user.getUsername();
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para esta operacion de MPS semanal.");
    }

    private boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return "master".equals(normalized) || "super_master".equals(normalized);
    }
}
