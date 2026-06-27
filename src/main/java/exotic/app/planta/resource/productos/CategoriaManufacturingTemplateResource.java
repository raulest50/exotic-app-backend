package exotic.app.planta.resource.productos;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.producto.dto.manufacturing.templates.CategoriaManufacturingTemplateDTO;
import exotic.app.planta.service.productos.templates.CategoriaManufacturingTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/productos/categorias")
@RequiredArgsConstructor
@Slf4j
public class CategoriaManufacturingTemplateResource {

    private final CategoriaManufacturingTemplateService templateService;

    @GetMapping("/{categoriaId}/manufacturing-template")
    public ResponseEntity<CategoriaManufacturingTemplateDTO> getTemplate(@PathVariable int categoriaId) {
        CategoriaManufacturingTemplateDTO template = templateService.getTemplateByCategoria(categoriaId);
        if (template == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(template);
    }

    @PutMapping("/{categoriaId}/manufacturing-template")
    public ResponseEntity<?> saveTemplate(
            @PathVariable int categoriaId,
            @RequestBody CategoriaManufacturingTemplateDTO dto
    ) {
        try {
            CategoriaManufacturingTemplateDTO saved = templateService.saveTemplate(categoriaId, dto);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            log.warn("[CATEGORIA_MF_TEMPLATE] validation failed categoriaId={} message={}", categoriaId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Plantilla de manufactura invalida", e.getMessage()));
        }
    }

    @DeleteMapping("/{categoriaId}/manufacturing-template")
    public ResponseEntity<Void> deleteTemplate(@PathVariable int categoriaId) {
        templateService.deleteTemplate(categoriaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/manufacturing-template/exists-batch")
    public ResponseEntity<Map<Integer, Boolean>> existsBatch(@RequestParam List<Integer> categoriaIds) {
        return ResponseEntity.ok(templateService.existsBatch(categoriaIds));
    }
}
