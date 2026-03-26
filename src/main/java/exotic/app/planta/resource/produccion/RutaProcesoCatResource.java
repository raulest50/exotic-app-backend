package exotic.app.planta.resource.produccion;

import exotic.app.planta.service.produccion.RutaProcesoCatService;
import exotic.app.planta.service.produccion.RutaProcesoCatService.RutaProcesoCatDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ruta-proceso-cat")
@RequiredArgsConstructor
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
    public ResponseEntity<RutaProcesoCatDTO> saveRuta(@RequestBody RutaProcesoCatDTO dto) {
        RutaProcesoCatDTO saved = rutaProcesoCatService.saveRuta(dto.getCategoriaId(), dto);
        return ResponseEntity.created(URI.create("/api/ruta-proceso-cat/" + dto.getCategoriaId())).body(saved);
    }

    @DeleteMapping("/{categoriaId}")
    public ResponseEntity<Void> deleteRuta(@PathVariable int categoriaId) {
        rutaProcesoCatService.deleteRuta(categoriaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exists-batch")
    public ResponseEntity<Map<Integer, Boolean>> checkRoutesExist(
            @RequestParam List<Integer> categoriaIds) {
        return ResponseEntity.ok(rutaProcesoCatService.checkRoutesExist(categoriaIds));
    }
}
