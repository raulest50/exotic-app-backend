package exotic.app.planta.resource.productos;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.dto.CategoriaResponseDTO;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.DuplicateIdException;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.DuplicateNameException;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.EmptyFieldException;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.ErrorResponse;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.ValidationException;
import exotic.app.planta.service.productos.CategoriaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/categorias")
@RequiredArgsConstructor
@Slf4j
public class CategoriaResource {

    private final CategoriaService categoriaService;

    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> saveCategoria(@RequestBody Categoria categoria) {
        CategoriaResponseDTO savedCategoria = categoriaService.saveCategoria(categoria);
        return ResponseEntity
                .created(URI.create("/categorias/" + savedCategoria.getCategoriaId()))
                .body(savedCategoria);
    }

    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> getAllCategorias() {
        List<CategoriaResponseDTO> categorias = categoriaService.getAllCategorias();
        return ResponseEntity.ok(categorias);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<CategoriaResponseDTO>> searchCategorias(
            @RequestParam(value = "nombre", required = false) String nombre,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        Page<CategoriaResponseDTO> categorias = categoriaService.searchCategorias(nombre, page, size);
        return ResponseEntity.ok(categorias);
    }

    @PatchMapping("/{categoriaId}/lote-size")
    public ResponseEntity<CategoriaResponseDTO> updateLoteSize(
            @PathVariable int categoriaId,
            @RequestBody Map<String, Integer> body
    ) {
        Integer loteSize = body != null ? body.get("loteSize") : null;
        if (loteSize == null) {
            return ResponseEntity.badRequest().build();
        }
        CategoriaResponseDTO updated = categoriaService.updateLoteSize(categoriaId, loteSize);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{categoriaId}/tiempo-dias-fabricacion")
    public ResponseEntity<CategoriaResponseDTO> updateTiempoDiasFabricacion(
            @PathVariable int categoriaId,
            @RequestBody Map<String, Integer> body
    ) {
        Integer tiempoDiasFabricacion = body != null ? body.get("tiempoDiasFabricacion") : null;
        if (tiempoDiasFabricacion == null) {
            return ResponseEntity.badRequest().build();
        }
        CategoriaResponseDTO updated = categoriaService.updateTiempoDiasFabricacion(categoriaId, tiempoDiasFabricacion);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{categoriaId}/capacidad-productiva-diaria")
    public ResponseEntity<CategoriaResponseDTO> updateCapacidadProductivaDiaria(
            @PathVariable int categoriaId,
            @RequestBody Map<String, Integer> body
    ) {
        Integer capacidadProductivaDiaria = body != null ? body.get("capacidadProductivaDiaria") : null;
        if (capacidadProductivaDiaria == null) {
            return ResponseEntity.badRequest().build();
        }
        CategoriaResponseDTO updated = categoriaService.updateCapacidadProductivaDiaria(categoriaId, capacidadProductivaDiaria);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{categoriaId}/pool-capacidad")
    public ResponseEntity<?> updatePoolCapacidad(
            @PathVariable int categoriaId,
            @RequestBody Map<String, Object> body
    ) {
        if (body == null || !body.containsKey("poolCapacidadId")) {
            return ResponseEntity.badRequest().body(Map.of("error", "El campo poolCapacidadId es obligatorio."));
        }

        Object rawValue = body.get("poolCapacidadId");
        Integer poolCapacidadId = null;
        if (rawValue != null) {
            if (!(rawValue instanceof Number number)
                    || number.doubleValue() != Math.rint(number.doubleValue())
                    || number.doubleValue() < Integer.MIN_VALUE
                    || number.doubleValue() > Integer.MAX_VALUE) {
                return ResponseEntity.badRequest().body(Map.of("error", "poolCapacidadId debe ser un entero o null."));
            }
            poolCapacidadId = number.intValue();
        }

        CategoriaResponseDTO updated = categoriaService.updatePoolCapacidad(categoriaId, poolCapacidadId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{categoriaId}")
    public ResponseEntity<Map<String, Object>> deleteCategoria(@PathVariable int categoriaId) {
        boolean deleted = categoriaService.deleteCategoriaById(categoriaId);

        if (deleted) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Categoria eliminada exitosamente"
            ));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "success", false,
                "message", "No se puede eliminar la categoria porque esta siendo utilizada por uno o mas productos terminados"
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Argumento invalido: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(EmptyFieldException.class)
    public ResponseEntity<ErrorResponse> handleEmptyFieldException(EmptyFieldException e) {
        log.warn("Error de campo vacio: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(DuplicateIdException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateIdException(DuplicateIdException e) {
        log.warn("Error de ID duplicado: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(DuplicateNameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateNameException(DuplicateNameException e) {
        log.warn("Error de nombre duplicado: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException e) {
        log.warn("Error de validacion: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        log.error("Error inesperado: {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Error al procesar la solicitud: " + e.getMessage()));
    }

    @GetMapping("/{categoriaId}")
    public ResponseEntity<CategoriaResponseDTO> getCategoriaById(@PathVariable int categoriaId) {
        Optional<CategoriaResponseDTO> categoriaOpt = categoriaService.getCategoriaById(categoriaId);

        if (categoriaOpt.isPresent()) {
            return ResponseEntity.ok(categoriaOpt.get());
        }
        throw new ValidationException("No se encontro categoria con ID: " + categoriaId);
    }
}
