package exotic.app.planta.resource.productos;

import exotic.app.planta.model.producto.dto.PoolCapacidadDTO;
import exotic.app.planta.model.producto.dto.PoolCapacidadUpsertRequestDTO;
import exotic.app.planta.resource.productos.exceptions.PoolCapacidadNotFoundException;
import exotic.app.planta.service.productos.PoolCapacidadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pool-capacidad")
@RequiredArgsConstructor
@Slf4j
public class PoolCapacidadResource {

    private final PoolCapacidadService poolCapacidadService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody PoolCapacidadUpsertRequestDTO request) {
        try {
            PoolCapacidadDTO created = poolCapacidadService.create(request);
            return ResponseEntity.created(URI.create("/api/pool-capacidad/" + created.getId())).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<PoolCapacidadDTO>> getAll() {
        return ResponseEntity.ok(poolCapacidadService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable int id) {
        try {
            return ResponseEntity.ok(poolCapacidadService.getById(id));
        } catch (PoolCapacidadNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody PoolCapacidadUpsertRequestDTO request) {
        try {
            return ResponseEntity.ok(poolCapacidadService.update(id, request));
        } catch (PoolCapacidadNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        try {
            poolCapacidadService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (PoolCapacidadNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
