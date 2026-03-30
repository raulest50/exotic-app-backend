package exotic.app.planta.resource.productos;

import exotic.app.planta.service.productos.SemiTerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/semiter")
@RequiredArgsConstructor
@Slf4j
public class SemiTerResource {

    private final SemiTerService semiTerService;

    @GetMapping("/{productoId}/is-deletable")
    public ResponseEntity<?> isProductoDeletable(@PathVariable String productoId) {
        log.info("REST request para verificar si el producto con ID: {} es eliminable", productoId);
        return ResponseEntity.ok(semiTerService.isProductoDeletable(productoId));
    }

    @DeleteMapping("/eliminacion_semiter_provicional/{productoId}")
    public ResponseEntity<?> eliminarProductoProvisional(@PathVariable String productoId) {
        log.info("REST request para eliminar provisionalmente el producto con ID: {}", productoId);

        try {
            Map<String, Object> result = semiTerService.deleteProductoProvisional(productoId);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            log.warn("No se puede eliminar el producto con ID {}: {}", productoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "success", false,
                            "message", "No se puede eliminar el producto",
                            "reason", e.getMessage()
                    ));
        } catch (IllegalArgumentException e) {
            log.warn("Error al eliminar el producto con ID {}: {}", productoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", "Error al eliminar el producto",
                            "reason", e.getMessage()
                    ));
        } catch (Exception e) {
            log.error("Error al eliminar el producto con ID {}: {}", productoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Error interno al eliminar el producto",
                            "reason", e.getMessage()
                    ));
        }
    }

    @DeleteMapping("/force_deletion/{productoId}")
    public ResponseEntity<?> forceDeleteProducto(@PathVariable String productoId) {
        log.info("REST request para forzar la eliminación del producto con ID: {}", productoId);

        try {
            Map<String, Object> result = semiTerService.forceDeleteProducto(productoId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Producto no encontrado o ID inválido {}: {}", productoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "message", "No se encontró el producto a eliminar",
                            "reason", e.getMessage()
                    ));
        } catch (IllegalStateException e) {
            log.warn("No se puede eliminar el producto con ID {}: {}", productoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "success", false,
                            "message", "No se puede eliminar el producto",
                            "reason", e.getMessage()
                    ));
        } catch (Exception e) {
            log.error("Error inesperado al eliminar el producto con ID {}: {}", productoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Error interno al eliminar el producto",
                            "reason", e.getMessage()
                    ));
        }
    }
}
