package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.produccion.dto.FilaInfVentasDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalResponseDTO;
import exotic.app.planta.model.produccion.dto.TerminadoConVentasDTO;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.service.produccion.MasterProductionScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
