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
            log.info("[PRODUCTOS_MANUFACTURING] get start productoId={}", productoId);
            ProductoManufacturingDTO response = productoManufacturingService.getProductoManufacturing(productoId);
            log.info("[PRODUCTOS_MANUFACTURING] get success productoId={} tipoProducto={}", response.getProductoId(), response.getTipoProducto());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("[PRODUCTOS_MANUFACTURING] get not_found productoId={} message={}", productoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("[PRODUCTOS_MANUFACTURING] get unexpected_error productoId={} message={}", productoId, e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping
    public ResponseEntity<?> createProductoManufacturing(@RequestBody ProductoManufacturingDTO dto) {
        try {
            log.info("[PRODUCTOS_MANUFACTURING] create start {}", manufacturingContext(dto));
            ProductoManufacturingDTO saved = productoManufacturingService.createProductoManufacturing(dto);
            log.info(
                    "[PRODUCTOS_MANUFACTURING] create success productoId={} tipoProducto={} categoriaId={} prefijoLotePresent={}",
                    saved.getProductoId(),
                    saved.getTipoProducto(),
                    saved.getCategoriaId(),
                    hasText(saved.getPrefijoLote())
            );
            return ResponseEntity.created(URI.create("/productos/manufacturing/" + saved.getProductoId())).body(saved);
        } catch (IllegalArgumentException e) {
            log.warn("[PRODUCTOS_MANUFACTURING] create validation_failed {} message={}", manufacturingContext(dto), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("[PRODUCTOS_MANUFACTURING] create unexpected_error {} message={}", manufacturingContext(dto), e.getMessage(), e);
            throw e;
        }
    }

    @PutMapping("/{productoId}")
    public ResponseEntity<?> updateProductoManufacturing(
            @PathVariable String productoId,
            @RequestBody ProductoManufacturingDTO dto
    ) {
        try {
            log.info("[PRODUCTOS_MANUFACTURING] update start pathProductoId={} {}", productoId, manufacturingContext(dto));
            ProductoManufacturingDTO response = productoManufacturingService.updateProductoManufacturing(productoId, dto);
            log.info(
                    "[PRODUCTOS_MANUFACTURING] update success productoId={} tipoProducto={} categoriaId={} prefijoLotePresent={}",
                    response.getProductoId(),
                    response.getTipoProducto(),
                    response.getCategoriaId(),
                    hasText(response.getPrefijoLote())
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("[PRODUCTOS_MANUFACTURING] update validation_failed pathProductoId={} {} message={}", productoId, manufacturingContext(dto), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("[PRODUCTOS_MANUFACTURING] update unexpected_error pathProductoId={} {} message={}", productoId, manufacturingContext(dto), e.getMessage(), e);
            throw e;
        }
    }

    private String manufacturingContext(ProductoManufacturingDTO dto) {
        if (dto == null) {
            return "productoId=<null> tipoProducto=<null> categoriaId=<null> prefijoLotePresent=false insumos=0 casePackPresent=false casePackInsumos=0 procesoNodes=0 procesoEdges=0";
        }

        int insumosCount = dto.getInsumos() != null ? dto.getInsumos().size() : 0;
        int casePackInsumosCount = dto.getCasePack() != null && dto.getCasePack().getInsumosEmpaque() != null
                ? dto.getCasePack().getInsumosEmpaque().size()
                : 0;
        var proceso = dto.getProcesoProduccionCompleto();
        int nodesCount = proceso != null && proceso.getNodes() != null ? proceso.getNodes().size() : 0;
        int edgesCount = proceso != null && proceso.getEdges() != null ? proceso.getEdges().size() : 0;

        return "productoId=" + nullSafe(dto.getProductoId())
                + " tipoProducto=" + nullSafe(dto.getTipoProducto())
                + " categoriaId=" + dto.getCategoriaId()
                + " prefijoLotePresent=" + hasText(dto.getPrefijoLote())
                + " insumos=" + insumosCount
                + " casePackPresent=" + (dto.getCasePack() != null)
                + " casePackInsumos=" + casePackInsumosCount
                + " procesoNodes=" + nodesCount
                + " procesoEdges=" + edgesCount;
    }

    private String nullSafe(String value) {
        return hasText(value) ? value.trim() : "<null>";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
