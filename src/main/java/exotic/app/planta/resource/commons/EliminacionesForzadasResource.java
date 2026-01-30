package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.eliminaciones.EstudiarEliminacionOCMResponseDTO;
import exotic.app.planta.service.commons.EliminacionesForzadasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
}
