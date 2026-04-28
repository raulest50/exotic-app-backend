package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.LeadTimeProveedorMaterialDTO;
import exotic.app.planta.model.bi.dto.LeadTimeProveedorMaterialPageRowDTO;
import exotic.app.planta.model.bi.dto.PuntoReordenEstimadoDTO;
import exotic.app.planta.service.bi.ProveedoresBiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/bi/proveedores")
@RequiredArgsConstructor
@Slf4j
public class ProveedoresBiResource {

    private final ProveedoresBiService proveedoresBiService;

    @GetMapping("/lead-time")
    public ResponseEntity<?> calcularLeadTimeProveedorMaterial(
            @RequestParam String proveedorId,
            @RequestParam String materialId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCorte,
            @RequestParam(defaultValue = "365") int ventanaDias
    ) {
        try {
            LeadTimeProveedorMaterialDTO result = proveedoresBiService.calcularLeadTimeProveedorMaterial(
                    proveedorId,
                    materialId,
                    fechaCorte,
                    ventanaDias
            );
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            log.warn("Entidad no encontrada en lead time BI: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Solicitud invalida para lead time BI: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al calcular lead time proveedor-material", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/materiales/{materialId}/lead-times")
    public ResponseEntity<?> listarLeadTimesPorMaterial(
            @PathVariable String materialId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCorte,
            @RequestParam(defaultValue = "365") int ventanaDias,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        try {
            Page<LeadTimeProveedorMaterialPageRowDTO> result = proveedoresBiService.listarLeadTimesPorMaterial(
                    materialId,
                    fechaCorte,
                    ventanaDias,
                    page,
                    size,
                    direction
            );
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            log.warn("Material no encontrado para ranking BI: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Solicitud invalida para ranking BI: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al listar lead times por material", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/materiales/{materialId}/reorder-point-estimate")
    public ResponseEntity<?> estimarPuntoReorden(
            @PathVariable String materialId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCorte,
            @RequestParam(defaultValue = "365") int ventanaDias
    ) {
        try {
            PuntoReordenEstimadoDTO result = proveedoresBiService.estimarPuntoReorden(
                    materialId,
                    fechaCorte,
                    ventanaDias
            );
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            log.warn("Material no encontrado para ROP BI: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Solicitud invalida para ROP BI: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al estimar punto de reorden", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
