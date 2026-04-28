package exotic.app.planta.resource.produccion;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.service.produccion.RutaProcesoCatService;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoCatDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ruta-proceso-cat")
@RequiredArgsConstructor
@Slf4j
public class RutaProcesoCatResource {

    private final RutaProcesoCatService rutaProcesoCatService;

    @GetMapping("/{categoriaId}")
    public ResponseEntity<RutaProcesoCatDTO> getRutaByCategoria(@PathVariable int categoriaId) {
        RutaProcesoCatDTO ruta = rutaProcesoCatService.getRutaByCategoria(categoriaId);
        if (ruta == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(ruta);
    }

    @PostMapping("/save_ruprocat")
    public ResponseEntity<?> saveRuta(@RequestBody RutaProcesoCatDTO dto) {
        Integer categoriaId = dto != null ? dto.getCategoriaId() : null;
        try {
            if (dto == null) {
                throw new IllegalArgumentException("La ruta de proceso no puede estar vacía.");
            }

            RutaProcesoCatDTO saved = rutaProcesoCatService.saveRuta(dto.getCategoriaId(), dto);
            return ResponseEntity.created(URI.create("/api/ruta-proceso-cat/" + dto.getCategoriaId())).body(saved);
        } catch (IllegalArgumentException e) {
            log.warn("Ruta de proceso inválida para categoria {}: {}", categoriaId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Ruta de proceso inválida", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Ruta de proceso bloqueada para categoria {}: {}", categoriaId, e.getMessage());
            return ResponseEntity.status(409)
                    .body(new ErrorResponse("Ruta de proceso bloqueada", e.getMessage()));
        }
    }

    @DeleteMapping("/{categoriaId}")
    public ResponseEntity<?> deleteRuta(@PathVariable int categoriaId) {
        try {
            rutaProcesoCatService.deleteRuta(categoriaId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            log.warn("Eliminación de ruta bloqueada para categoria {}: {}", categoriaId, e.getMessage());
            return ResponseEntity.status(409)
                    .body(new ErrorResponse("Ruta de proceso bloqueada", e.getMessage()));
        }
    }

    @GetMapping("/exists-batch")
    public ResponseEntity<Map<Integer, Boolean>> checkRoutesExist(
            @RequestParam List<Integer> categoriaIds) {
        return ResponseEntity.ok(rutaProcesoCatService.checkRoutesExist(categoriaIds));
    }
}
