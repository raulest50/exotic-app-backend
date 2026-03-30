package exotic.app.planta.resource.productos;

import exotic.app.planta.model.producto.dto.manufacturing.ProductoManufacturingDTO;
import exotic.app.planta.service.productos.ProductoManufacturingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/productos/manufacturing")
@RequiredArgsConstructor
@Slf4j
public class ProductoManufacturingResource {

    private final ProductoManufacturingService productoManufacturingService;

    @GetMapping("/{productoId}")
    public ResponseEntity<?> getProductoManufacturing(@PathVariable String productoId) {
        try {
            return ResponseEntity.ok(productoManufacturingService.getProductoManufacturing(productoId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createProductoManufacturing(@RequestBody ProductoManufacturingDTO dto) {
        try {
            ProductoManufacturingDTO saved = productoManufacturingService.createProductoManufacturing(dto);
            return ResponseEntity.created(URI.create("/productos/manufacturing/" + saved.getProductoId())).body(saved);
        } catch (IllegalArgumentException e) {
            log.warn("Error validando creación de producto con manufactura: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{productoId}")
    public ResponseEntity<?> updateProductoManufacturing(
            @PathVariable String productoId,
            @RequestBody ProductoManufacturingDTO dto
    ) {
        try {
            return ResponseEntity.ok(productoManufacturingService.updateProductoManufacturing(productoId, dto));
        } catch (IllegalArgumentException e) {
            log.warn("Error validando actualización de producto con manufactura {}: {}", productoId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
