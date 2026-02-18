package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.produccion.dto.FilaInfVentasDTO;
import exotic.app.planta.model.produccion.dto.TerminadoConVentasDTO;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.producto.TerminadoRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @PostMapping("/asociar_terminados")
    public ResponseEntity<List<TerminadoConVentasDTO>> asociarTerminados(
            @RequestBody List<FilaInfVentasDTO> filasUnificadas) {

        log.info("[asociarTerminados] Recibidas {} filas unificadas", filasUnificadas.size());

        List<String> codigos = filasUnificadas.stream()
                .map(FilaInfVentasDTO::getCodigo)
                .collect(Collectors.toList());

        Map<String, Terminado> terminadosMap = terminadoRepo.findAllById(codigos).stream()
                .collect(Collectors.toMap(Terminado::getProductoId, Function.identity()));

        List<TerminadoConVentasDTO> resultado = new ArrayList<>();

        for (FilaInfVentasDTO fila : filasUnificadas) {
            Terminado terminado = terminadosMap.get(fila.getCodigo());
            if (terminado != null) {
                resultado.add(new TerminadoConVentasDTO(terminado, fila.getCantidadVendida(), fila.getValorTotal()));
            } else {
                log.warn("[asociarTerminados] No se encontró Terminado con código: {}", fila.getCodigo());
            }
        }

        log.info("[asociarTerminados] Asociados {} de {} códigos", resultado.size(), filasUnificadas.size());
        return ResponseEntity.ok(resultado);
    }
}
