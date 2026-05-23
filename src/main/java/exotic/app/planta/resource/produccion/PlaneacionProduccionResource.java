package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.produccion.dto.FilaInfVentasDTO;
import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.dto.AprobarMpsSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.GenerarOdpDesdeMpsRequestDTO;
import exotic.app.planta.model.produccion.dto.GenerarOdpDesdeMpsResponseDTO;
import exotic.app.planta.model.produccion.dto.GuardarMpsSemanalDraftRequestDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalListItemDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalOrdenProduccionListItemDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalResponseDTO;
import exotic.app.planta.model.produccion.dto.TerminadoConVentasDTO;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalDraftNotFoundException;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalNotFoundException;
import exotic.app.planta.service.produccion.MasterProductionScheduleDraftService;
import exotic.app.planta.service.produccion.MasterProductionScheduleOrderGenerationService;
import exotic.app.planta.service.produccion.MasterProductionScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/planeacion_produccion")
@RequiredArgsConstructor
@Slf4j
public class PlaneacionProduccionResource {

    private final TerminadoRepo terminadoRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final MasterProductionScheduleService masterProductionScheduleService;
    private final MasterProductionScheduleDraftService masterProductionScheduleDraftService;
    private final MasterProductionScheduleOrderGenerationService masterProductionScheduleOrderGenerationService;

    @PostMapping("/asociar_terminados")
    public ResponseEntity<List<TerminadoConVentasDTO>> asociarTerminados(
            @RequestBody List<FilaInfVentasDTO> filasUnificadas
    ) {
        log.info("[asociarTerminados] Recibidas {} filas unificadas", filasUnificadas.size());

        List<String> codigos = filasUnificadas.stream()
                .map(FilaInfVentasDTO::getCodigo)
                .collect(Collectors.toList());

        Map<String, Terminado> terminadosMap = terminadoRepo.findAllById(codigos).stream()
                .collect(Collectors.toMap(Terminado::getProductoId, Function.identity()));

        Map<String, Double> stockMap = codigos.isEmpty()
                ? Map.of()
                : transaccionAlmacenRepo.findTotalCantidadByProductoIds(codigos).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> row[1] instanceof Number number ? number.doubleValue() : 0.0
                ));

        List<TerminadoConVentasDTO> resultado = new ArrayList<>();

        for (FilaInfVentasDTO fila : filasUnificadas) {
            Terminado terminado = terminadosMap.get(fila.getCodigo());
            if (terminado != null) {
                double stockActualConsolidado = stockMap.getOrDefault(terminado.getProductoId(), 0.0);
                resultado.add(new TerminadoConVentasDTO(
                        terminado,
                        fila.getCantidadVendida(),
                        fila.getValorTotal(),
                        stockActualConsolidado
                ));
            } else {
                log.warn("[asociarTerminados] No se encontro Terminado con codigo: {}", fila.getCodigo());
            }
        }

        log.info("[asociarTerminados] Asociados {} de {} codigos", resultado.size(), filasUnificadas.size());
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/propuesta-mps-semanal")
    public ResponseEntity<?> generarPropuestaMpsSemanal(
            @RequestBody PropuestaMpsSemanalRequestDTO request
    ) {
        try {
            PropuestaMpsSemanalResponseDTO response = masterProductionScheduleService.calcularPropuestaSemanal(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/mps-semanal/borrador")
    public ResponseEntity<?> guardarBorradorMpsSemanal(
            @RequestBody GuardarMpsSemanalDraftRequestDTO request
    ) {
        try {
            MpsSemanalDraftDTO response = masterProductionScheduleDraftService.saveDraft(request);
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
