package exotic.app.planta.resource.productos;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.DuplicateIdException;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.DuplicateNameException;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.EmptyFieldException;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.ValidationException;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.ErrorResponse;
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

    /**
     * Endpoint para guardar una nueva categoría o actualizar una existente
     * @param categoria La categoría a guardar
     * @return La categoría guardada
     * @throws EmptyFieldException si el nombre de la categoría está vacío
     * @throws DuplicateIdException si ya existe una categoría con el mismo ID
     * @throws DuplicateNameException si ya existe una categoría con el mismo nombre
     */
    @PostMapping
    public ResponseEntity<Categoria> saveCategoria(@RequestBody Categoria categoria) {
        Categoria savedCategoria = categoriaService.saveCategoria(categoria);
        return ResponseEntity
                .created(URI.create("/categorias/" + savedCategoria.getCategoriaId()))
                .body(savedCategoria);
    }

    /**
     * Endpoint para obtener todas las categorías registradas
     * @return Lista de todas las categorías
     */
    @GetMapping
    public ResponseEntity<List<Categoria>> getAllCategorias() {
        List<Categoria> categorias = categoriaService.getAllCategorias();
        return ResponseEntity.ok(categorias);
    }

    /**
     * Endpoint para buscar categorías por nombre con coincidencia parcial y paginación
     * @param nombre nombre o fragmento a buscar; vacío retorna todas las categorías
     * @param page número de página (default 0)
     * @param size tamaño de página (default 10)
     * @return página de categorías
     */
    @GetMapping("/search")
    public ResponseEntity<Page<Categoria>> searchCategorias(
            @RequestParam(value = "nombre", required = false) String nombre,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        Page<Categoria> categorias = categoriaService.searchCategorias(nombre, page, size);
        return ResponseEntity.ok(categorias);
    }

    /**
     * Endpoint para actualizar el tamaño de lote de una categoría
     * @param categoriaId ID de la categoría
     * @param body map con clave "loteSize" y valor numérico >= 0
     * @return categoría actualizada
     */
    @PatchMapping("/{categoriaId}/lote-size")
    public ResponseEntity<Categoria> updateLoteSize(
            @PathVariable int categoriaId,
            @RequestBody Map<String, Integer> body
    ) {
        Integer loteSize = body != null ? body.get("loteSize") : null;
        if (loteSize == null) {
            return ResponseEntity.badRequest().build();
        }
        Categoria updated = categoriaService.updateLoteSize(categoriaId, loteSize);
        return ResponseEntity.ok(updated);
    }

    /**
     * Endpoint para eliminar una categoría por su ID
     * Solo se puede eliminar si no está siendo referenciada por ningún producto terminado
     * @param categoriaId ID de la categoría a eliminar
     * @return Mensaje de éxito o error
     * @throws ValidationException si la categoría no existe
     */
    @DeleteMapping("/{categoriaId}")
    public ResponseEntity<Map<String, Object>> deleteCategoria(@PathVariable int categoriaId) {
        boolean deleted = categoriaService.deleteCategoriaById(categoriaId);

        if (deleted) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Categoría eliminada exitosamente"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "success", false,
                "message", "No se puede eliminar la categoría porque está siendo utilizada por uno o más productos terminados"
            ));
        }
    }

    /**
     * Manejador para argumentos inválidos (ej. loteSize negativo)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Argumento inválido: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
    }

    /**
     * Manejador para excepciones de campos vacíos
     */
    @ExceptionHandler(EmptyFieldException.class)
    public ResponseEntity<ErrorResponse> handleEmptyFieldException(EmptyFieldException e) {
        log.warn("Error de campo vacío: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
    }

    /**
     * Manejador para excepciones de ID duplicado
     */
    @ExceptionHandler(DuplicateIdException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateIdException(DuplicateIdException e) {
        log.warn("Error de ID duplicado: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
    }

    /**
     * Manejador para excepciones de nombre duplicado
     */
    @ExceptionHandler(DuplicateNameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateNameException(DuplicateNameException e) {
        log.warn("Error de nombre duplicado: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
    }

    /**
     * Manejador para otras excepciones de validación
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException e) {
        log.warn("Error de validación: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
    }

    /**
     * Manejador para excepciones generales
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        log.error("Error inesperado: {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Error al procesar la solicitud: " + e.getMessage()));
    }

    /**
     * Endpoint para obtener una categoría por su ID
     * @param categoriaId ID de la categoría
     * @return La categoría encontrada
     * @throws ValidationException si la categoría no existe
     */
    @GetMapping("/{categoriaId}")
    public ResponseEntity<Categoria> getCategoriaById(@PathVariable int categoriaId) {
        Optional<Categoria> categoriaOpt = categoriaService.getCategoriaById(categoriaId);

        if (categoriaOpt.isPresent()) {
            return ResponseEntity.ok(categoriaOpt.get());
        } else {
            throw new ValidationException("No se encontró categoría con ID: " + categoriaId);
        }
    }
}