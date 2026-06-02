package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.dto.AprobarMpsSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.GenerarOdpDesdeMpsRequestDTO;
import exotic.app.planta.model.produccion.dto.GenerarOdpDesdeMpsResponseDTO;
import exotic.app.planta.model.produccion.dto.GuardarProgramacionProduccionSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalListItemDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalOrdenProduccionListItemDTO;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalDraftNotFoundException;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalNotFoundException;
import exotic.app.planta.service.produccion.MasterProductionScheduleDraftService;
import exotic.app.planta.service.produccion.MasterProductionScheduleOrderGenerationService;
import exotic.app.planta.service.produccion.ProgramacionProduccionSemanalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/programacion_produccion")
@RequiredArgsConstructor
public class ProgramacionProduccionResource {

    private final ProgramacionProduccionSemanalService programacionProduccionSemanalService;
    private final MasterProductionScheduleDraftService masterProductionScheduleDraftService;
    private final MasterProductionScheduleOrderGenerationService masterProductionScheduleOrderGenerationService;

    @PostMapping("/mps-semanal/borrador-directo")
    public ResponseEntity<?> guardarBorradorDirecto(
            @RequestBody GuardarProgramacionProduccionSemanalRequestDTO request
    ) {
        try {
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
            @RequestParam LocalDate weekStartDate
    ) {
        try {
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
            @RequestParam LocalDate weekStartDate
    ) {
        try {
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
            @RequestParam(required = false) EstadoMpsSemanal estado
    ) {
        try {
            List<MpsSemanalListItemDTO> response = masterProductionScheduleDraftService.listByEstado(estado);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
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
            String username = requireAuthenticatedUsername(authentication);
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
            String username = requireAuthenticatedUsername(authentication);
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
            @RequestParam LocalDate weekStartDate
    ) {
        try {
            List<MpsSemanalOrdenProduccionListItemDTO> response = masterProductionScheduleOrderGenerationService.getOrdenesGeneradasPorSemana(weekStartDate);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (MpsSemanalNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    private String requireAuthenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new SecurityException("No se pudo determinar el usuario autenticado.");
        }
        return authentication.getName();
    }
}
