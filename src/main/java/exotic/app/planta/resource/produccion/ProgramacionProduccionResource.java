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
        try {
            requireAnyTabAccess(authentication, "PROGRAMACION_PRODUCCION", "APROBACION_MPS_WEEK");
            List<SemanaMPSDTO> response = semanaMPSService.listIsoWeeksForYear(anioSemana);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/mps-semanal/borrador-directo")
    public ResponseEntity<?> guardarBorradorDirecto(
            @RequestBody GuardarProgramacionProduccionSemanalRequestDTO request,
            Authentication authentication
    ) {
        try {
            requireTabAccess(authentication, "PROGRAMACION_PRODUCCION");
            MpsSemanalDraftDTO response = programacionProduccionSemanalService.guardarBorradorDirecto(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/mps-semanal/borrador")
    public ResponseEntity<?> guardarBorradorMpsSemanal(
            @RequestBody GuardarProgramacionProduccionSemanalRequestDTO request,
            Authentication authentication
    ) {
        try {
            requireTabAccess(authentication, "PROGRAMACION_PRODUCCION");
            MpsSemanalDraftDTO response = programacionProduccionSemanalService.guardarBorradorDirecto(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/mps-semanal/borrador")
    public ResponseEntity<?> obtenerBorradorMpsSemanal(
            @RequestParam LocalDate weekStartDate,
            Authentication authentication
    ) {
        try {
            requireTabAccess(authentication, "PROGRAMACION_PRODUCCION");
            return ResponseEntity.ok(masterProductionScheduleDraftService.getDraftByWeekStartDate(weekStartDate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (MpsSemanalDraftNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/mps-semanal")
    public ResponseEntity<?> obtenerMpsSemanal(
            @RequestParam LocalDate weekStartDate,
            Authentication authentication
    ) {
        try {
            requireAnyTabAccess(authentication, "PROGRAMACION_PRODUCCION", "APROBACION_MPS_WEEK");
            return ResponseEntity.ok(masterProductionScheduleDraftService.getByWeekStartDate(weekStartDate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (MpsSemanalNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/mps-semanal/list")
    public ResponseEntity<?> listarMpsSemanales(
            @RequestParam(required = false) EstadoMpsSemanal estado,
            Authentication authentication
    ) {
        try {
            requireTabAccess(authentication, "APROBACION_MPS_WEEK");
            List<MpsSemanalListItemDTO> response = masterProductionScheduleDraftService.listByEstado(estado);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/mps-semanal/observaciones")
    public ResponseEntity<?> listarObservacionesMpsSemanal(
            @RequestParam LocalDate weekStartDate,
            Authentication authentication
    ) {
        try {
            requireAnyTabAccess(authentication, "PROGRAMACION_PRODUCCION", "APROBACION_MPS_WEEK");
            List<MpsSemanalObservacionDTO> response = mpsSemanalObservacionService.listarPorSemana(weekStartDate);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (MpsSemanalNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/mps-semanal/observaciones")
    public ResponseEntity<?> crearObservacionMpsSemanal(
            @RequestBody CrearMpsSemanalObservacionRequestDTO request,
            Authentication authentication
    ) {
        try {
            String username = requireAuthorizedUsername(authentication, "APROBACION_MPS_WEEK");
            MpsSemanalObservacionDTO response = mpsSemanalObservacionService.crearObservacion(request, username);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (MpsSemanalNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/mps-semanal/observaciones/{observacionId}/atender")
    public ResponseEntity<?> atenderObservacionMpsSemanal(
            @PathVariable Long observacionId,
            @RequestBody AtenderMpsSemanalObservacionRequestDTO request,
            Authentication authentication
    ) {
        try {
            String username = requireAuthorizedUsername(authentication, "PROGRAMACION_PRODUCCION");
            MpsSemanalObservacionDTO response = mpsSemanalObservacionService.atenderObservacion(observacionId, request, username);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (MpsSemanalNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/mps-semanal/observaciones/{observacionId}/cerrar")
    public ResponseEntity<?> cerrarObservacionMpsSemanal(
            @PathVariable Long observacionId,
            Authentication authentication
    ) {
        try {
            String username = requireAuthorizedUsername(authentication, "APROBACION_MPS_WEEK");
            MpsSemanalObservacionDTO response = mpsSemanalObservacionService.cerrarObservacion(observacionId, username);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (MpsSemanalNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/mps-semanal/aprobar")
    public ResponseEntity<?> aprobarMpsSemanal(
            @RequestBody AprobarMpsSemanalRequestDTO request,
            Authentication authentication
    ) {
        try {
            String username = requireAuthorizedUsername(authentication, "APROBACION_MPS_WEEK");
            MpsSemanalDraftDTO response = masterProductionScheduleDraftService.approveWeek(request, username);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (MpsSemanalNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/mps-semanal/generar-odps")
    public ResponseEntity<?> generarOdpsDesdeMpsSemanal(
            @RequestBody GenerarOdpDesdeMpsRequestDTO request,
            Authentication authentication
    ) {
        try {
            String username = requireAuthorizedUsername(authentication, "APROBACION_MPS_WEEK");
            GenerarOdpDesdeMpsResponseDTO response = masterProductionScheduleOrderGenerationService.generarOrdenesDesdeSemanaAprobada(request, username);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (MpsSemanalNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/mps-semanal/odps")
    public ResponseEntity<?> obtenerOdpsDesdeMpsSemanal(
            @RequestParam LocalDate weekStartDate,
            Authentication authentication
    ) {
        try {
            requireTabAccess(authentication, "APROBACION_MPS_WEEK");
            List<MpsSemanalOrdenProduccionListItemDTO> response = masterProductionScheduleOrderGenerationService.getOrdenesGeneradasPorSemana(weekStartDate);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (MpsSemanalNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
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
