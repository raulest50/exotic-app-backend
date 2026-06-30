package exotic.app.planta.resource.produccion;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.service.produccion.RutaProcesoCatService;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoCatDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
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

    @GetMapping("/{categoriaId}/versiones")
    public ResponseEntity<List<RutaProcesoCatDTO>> getVersionesByCategoria(@PathVariable int categoriaId) {
        return ResponseEntity.ok(rutaProcesoCatService.getVersionesByCategoria(categoriaId));
    }

    @GetMapping("/{categoriaId}/versiones/{versionId}")
    public ResponseEntity<RutaProcesoCatDTO> getVersionByCategoria(
            @PathVariable int categoriaId,
            @PathVariable Long versionId) {
        RutaProcesoCatDTO ruta = rutaProcesoCatService.getVersionByCategoria(categoriaId, versionId);
        if (ruta == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(ruta);
    }

    @PostMapping("/save_ruprocat")
    public ResponseEntity<?> saveRuta(Authentication authentication, @RequestBody RutaProcesoCatDTO dto) {
        Integer categoriaId = dto != null ? dto.getCategoriaId() : null;
        try {
            if (dto == null) {
                throw new IllegalArgumentException("La ruta de proceso no puede estar vacía.");
            }

            String username = authentication != null && authentication.isAuthenticated()
                    ? authentication.getName()
                    : null;
            RutaProcesoCatDTO saved = rutaProcesoCatService.saveRuta(dto.getCategoriaId(), dto, username);
            return ResponseEntity.created(URI.create("/api/ruta-proceso-cat/" + dto.getCategoriaId())).body(saved);
        } catch (IllegalArgumentException e) {
            log.warn("Ruta de proceso inválida para categoria {}: {}", categoriaId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Ruta de proceso inválida", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Conflicto al versionar ruta de proceso para categoria {}: {}", categoriaId, e.getMessage());
            return ResponseEntity.status(409)
                    .body(new ErrorResponse("Conflicto al versionar ruta de proceso", e.getMessage()));
        }
    }

    @DeleteMapping("/{categoriaId}")
    public ResponseEntity<?> deleteRuta(@PathVariable int categoriaId) {
        try {
            rutaProcesoCatService.deleteRuta(categoriaId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            log.warn("Conflicto al retirar ruta vigente para categoria {}: {}", categoriaId, e.getMessage());
            return ResponseEntity.status(409)
                    .body(new ErrorResponse("Conflicto al retirar ruta vigente", e.getMessage()));
        }
    }

    @GetMapping("/exists-batch")
    public ResponseEntity<Map<Integer, Boolean>> checkRoutesExist(
            @RequestParam List<Integer> categoriaIds) {
        return ResponseEntity.ok(rutaProcesoCatService.checkRoutesExist(categoriaIds));
    }
}
