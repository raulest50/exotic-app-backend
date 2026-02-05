package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.DuplicateIdException;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.DuplicateNameException;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.EmptyFieldException;
import exotic.app.planta.resource.productos.exceptions.CategoriaExceptions.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoriaService {

    private final CategoriaRepo categoriaRepo;
    private final TerminadoRepo terminadoRepo;

    /**
     * Guarda una nueva categoría o actualiza una existente, verificando que el ID y nombre sean únicos
     * @param categoria La categoría a guardar
     * @return La categoría guardada
     * @throws EmptyFieldException si el nombre de la categoría está vacío
     * @throws DuplicateIdException si ya existe una categoría con el mismo ID
     * @throws DuplicateNameException si ya existe una categoría con el mismo nombre
     */
    @Transactional
    public Categoria saveCategoria(Categoria categoria) {
        log.info("Intentando guardar categoría: {}", categoria.getCategoriaNombre());

        // Validar que el nombre no esté vacío
        if (categoria.getCategoriaNombre() == null || categoria.getCategoriaNombre().trim().isEmpty()) {
            throw new EmptyFieldException("El nombre de la categoría no puede estar vacío");
        }

        // Verificar si ya existe una categoría con el mismo ID
        if (categoria.getCategoriaId() > 0 && categoriaRepo.existsById(categoria.getCategoriaId())) {
            Optional<Categoria> existingCategoria = categoriaRepo.findById(categoria.getCategoriaId());
            // Si estamos actualizando la misma categoría (mismo ID), verificamos que el nombre no colisione con otra categoría
            if (existingCategoria.isPresent() && !existingCategoria.get().getCategoriaNombre().equals(categoria.getCategoriaNombre())) {
                if (categoriaRepo.existsByCategoriaNombre(categoria.getCategoriaNombre())) {
                    throw new DuplicateNameException("Ya existe una categoría con el nombre: " + categoria.getCategoriaNombre());
                }
            }
            // Es una actualización válida
            log.info("Actualizando categoría existente con ID: {}", categoria.getCategoriaId());
            return categoriaRepo.save(categoria);
        }

        // Es una nueva categoría, verificar que el ID no exista
        if (categoria.getCategoriaId() > 0 && categoriaRepo.existsById(categoria.getCategoriaId())) {
            throw new DuplicateIdException("Ya existe una categoría con el ID: " + categoria.getCategoriaId());
        }

        // Verificar que el nombre no exista
        if (categoriaRepo.existsByCategoriaNombre(categoria.getCategoriaNombre())) {
            throw new DuplicateNameException("Ya existe una categoría con el nombre: " + categoria.getCategoriaNombre());
        }

        log.info("Guardando nueva categoría: {}", categoria.getCategoriaNombre());
        return categoriaRepo.save(categoria);
    }

    /**
     * Obtiene todas las categorías registradas
     * @return Lista de todas las categorías
     */
    public List<Categoria> getAllCategorias() {
        log.info("Obteniendo todas las categorías");
        return categoriaRepo.findAll();
    }

    /**
     * Elimina una categoría por su ID, solo si no está siendo referenciada por ningún producto terminado
     * @param categoriaId ID de la categoría a eliminar
     * @return true si la categoría fue eliminada, false si no se pudo eliminar porque está siendo referenciada
     * @throws ValidationException si la categoría no existe
     */
    @Transactional
    public boolean deleteCategoriaById(int categoriaId) {
        log.info("Intentando eliminar categoría con ID: {}", categoriaId);

        // Verificar si la categoría existe
        if (!categoriaRepo.existsById(categoriaId)) {
            log.error("No se encontró categoría con ID: {}", categoriaId);
            throw new ValidationException("No existe categoría con ID: " + categoriaId);
        }

        // Verificar si hay productos terminados que referencian esta categoría
        Specification<Terminado> spec = (root, query, cb) -> 
            cb.equal(root.get("categoria").get("categoriaId"), categoriaId);

        long count = terminadoRepo.count(spec);

        if (count > 0) {
            log.warn("No se puede eliminar la categoría con ID: {} porque está siendo referenciada por {} productos terminados", 
                    categoriaId, count);
            return false;
        }

        // Si no hay referencias, eliminar la categoría
        categoriaRepo.deleteById(categoriaId);
        log.info("Categoría con ID: {} eliminada exitosamente", categoriaId);
        return true;
    }

    /**
     * Obtiene una categoría por su ID
     * @param categoriaId ID de la categoría
     * @return La categoría encontrada o vacío si no existe
     */
    public Optional<Categoria> getCategoriaById(int categoriaId) {
        return categoriaRepo.findById(categoriaId);
    }

    /**
     * Busca categorías por nombre con coincidencia parcial (case-insensitive)
     * @param nombre nombre o fragmento a buscar; null o vacío retorna todas las categorías
     * @param page número de página (0-based)
     * @param size tamaño de página
     * @return página de categorías
     */
    public Page<Categoria> searchCategorias(String nombre, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (nombre == null || nombre.isBlank()) {
            return categoriaRepo.findAll(pageable);
        }
        return categoriaRepo.findByCategoriaNombreContainingIgnoreCase(nombre.trim(), pageable);
    }

    /**
     * Actualiza el tamaño de lote de una categoría
     * @param categoriaId ID de la categoría
     * @param loteSize nuevo tamaño de lote (debe ser >= 0)
     * @return la categoría actualizada
     * @throws ValidationException si la categoría no existe
     * @throws IllegalArgumentException si loteSize es negativo
     */
    @Transactional
    public Categoria updateLoteSize(int categoriaId, int loteSize) {
        if (loteSize < 0) {
            throw new IllegalArgumentException("El tamaño de lote debe ser mayor o igual a 0");
        }
        Categoria categoria = categoriaRepo.findById(categoriaId)
                .orElseThrow(() -> new ValidationException("No se encontró categoría con ID: " + categoriaId));
        categoria.setLoteSize(loteSize);
        return categoriaRepo.save(categoria);
    }
}
