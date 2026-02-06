package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.eliminaciones.EstudiarEliminacionOCMResponseDTO;
import exotic.app.planta.model.commons.dto.eliminaciones.EstudiarEliminacionOPResponseDTO;
import exotic.app.planta.service.commons.EliminacionesForzadasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eliminaciones-forzadas")
@RequiredArgsConstructor
@Slf4j
public class EliminacionesForzadasResource {

    private final EliminacionesForzadasService eliminacionesForzadasService;

    /**
     * Estudia qué registros bloquean la eliminación forzada de una Orden de Compra de Materiales (OCM)
     * por integridad referencial (ítems, lotes, transacciones de almacén, asientos contables).
     *
     * @param ordenCompraId ID de la orden de compra
     * @return DTO con listas de dependencias que habría que eliminar o ajustar
     */
    @GetMapping("/estudiar/orden-compra/{ordenCompraId}")
    public ResponseEntity<EstudiarEliminacionOCMResponseDTO> estudiarEliminacionOrdenCompra(
            @PathVariable int ordenCompraId
    ) {
        EstudiarEliminacionOCMResponseDTO result = eliminacionesForzadasService.estudiarEliminacionOrdenCompra(ordenCompraId);
        return ResponseEntity.ok(result);
    }

    /**
     * Ejecuta la eliminación forzada de una Orden de Compra de Materiales (solo si no tiene transacciones asociadas).
     *
     * @param ordenCompraId ID de la orden de compra
     * @return 204 No Content en éxito, 400 si tiene transacciones de almacén asociadas
     */
    @DeleteMapping("/orden-compra/{ordenCompraId}")
    public ResponseEntity<?> ejecutarEliminacionOrdenCompra(@PathVariable int ordenCompraId) {
        try {
            eliminacionesForzadasService.ejecutarEliminacionOrdenCompra(ordenCompraId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            log.warn("Eliminación OCM rechazada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of("message", e.getMessage()));
        }
    }

    /**
     * Estudia qué registros están asociados a una Orden de Producción (OP).
     * eliminable = true solo cuando no hay transacciones de almacén asociadas.
     *
     * @param ordenProduccionId ID de la orden de producción
     * @return DTO con dependencias y flag eliminable
     */
    @GetMapping("/estudiar/orden-produccion/{ordenProduccionId}")
    public ResponseEntity<EstudiarEliminacionOPResponseDTO> estudiarEliminacionOrdenProduccion(
            @PathVariable int ordenProduccionId
    ) {
        EstudiarEliminacionOPResponseDTO result = eliminacionesForzadasService.estudiarEliminacionOrdenProduccion(ordenProduccionId);
        return ResponseEntity.ok(result);
    }

    /**
     * Ejecuta la eliminación forzada de una Orden de Producción (solo si no tiene transacciones asociadas).
     *
     * @param ordenProduccionId ID de la orden de producción
     * @return 204 No Content en éxito
     */
    @DeleteMapping("/orden-produccion/{ordenProduccionId}")
    public ResponseEntity<?> ejecutarEliminacionOrdenProduccion(@PathVariable int ordenProduccionId) {
        try {
            eliminacionesForzadasService.ejecutarEliminacionOrdenProduccion(ordenProduccionId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            log.warn("Eliminación OP rechazada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of("message", e.getMessage()));
        }
    }
}
