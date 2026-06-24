package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.dto.CategoriaResponseDTO;
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
     * Guarda una nueva categoria o actualiza una existente, verificando que el ID y nombre sean unicos.
     * Cuando la categoria ya existe, solo actualiza nombre y descripcion; los parametros de planeacion
     * se administran por endpoints dedicados.
     */
    @Transactional
    public CategoriaResponseDTO saveCategoria(Categoria categoria) {
        log.info("Intentando guardar categoria: {}", categoria.getCategoriaNombre());

        if (categoria.getCategoriaNombre() == null || categoria.getCategoriaNombre().trim().isEmpty()) {
            throw new EmptyFieldException("El nombre de la categoria no puede estar vacio");
        }

        String categoriaNombre = categoria.getCategoriaNombre().trim();
        categoria.setCategoriaNombre(categoriaNombre);

        if (categoria.getCategoriaId() > 0 && categoriaRepo.existsById(categoria.getCategoriaId())) {
            Categoria existingCategoria = categoriaRepo.findById(categoria.getCategoriaId())
                    .orElseThrow(() -> new ValidationException("No existe categoria con ID: " + categoria.getCategoriaId()));

            if (!existingCategoria.getCategoriaNombre().equals(categoriaNombre)
                    && categoriaRepo.existsByCategoriaNombre(categoriaNombre)) {
                throw new DuplicateNameException("Ya existe una categoria con el nombre: " + categoriaNombre);
            }

            log.info("Actualizando categoria existente con ID: {}", categoria.getCategoriaId());
            existingCategoria.setCategoriaNombre(categoriaNombre);
            existingCategoria.setCategoriaDescripcion(categoria.getCategoriaDescripcion());
            return CategoriaResponseDTO.fromEntity(categoriaRepo.save(existingCategoria));
        }

        if (categoria.getCategoriaId() > 0 && categoriaRepo.existsById(categoria.getCategoriaId())) {
            throw new DuplicateIdException("Ya existe una categoria con el ID: " + categoria.getCategoriaId());
        }

        if (categoriaRepo.existsByCategoriaNombre(categoriaNombre)) {
            throw new DuplicateNameException("Ya existe una categoria con el nombre: " + categoriaNombre);
        }

        log.info("Guardando nueva categoria: {}", categoria.getCategoriaNombre());
        return CategoriaResponseDTO.fromEntity(categoriaRepo.save(categoria));
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> getAllCategorias() {
        log.info("Obteniendo todas las categorias");
        return categoriaRepo.findAll().stream()
                .map(CategoriaResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public boolean deleteCategoriaById(int categoriaId) {
        log.info("Intentando eliminar categoria con ID: {}", categoriaId);

        if (!categoriaRepo.existsById(categoriaId)) {
            log.error("No se encontro categoria con ID: {}", categoriaId);
            throw new ValidationException("No existe categoria con ID: " + categoriaId);
        }

        Specification<Terminado> spec = (root, query, cb) ->
                cb.equal(root.get("categoria").get("categoriaId"), categoriaId);

        long count = terminadoRepo.count(spec);

        if (count > 0) {
            log.warn("No se puede eliminar la categoria con ID: {} porque esta siendo referenciada por {} productos terminados",
                    categoriaId, count);
            return false;
        }

        categoriaRepo.deleteById(categoriaId);
        log.info("Categoria con ID: {} eliminada exitosamente", categoriaId);
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<CategoriaResponseDTO> getCategoriaById(int categoriaId) {
        return categoriaRepo.findById(categoriaId)
                .map(CategoriaResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<CategoriaResponseDTO> searchCategorias(String nombre, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (nombre == null || nombre.isBlank()) {
            return categoriaRepo.findAll(pageable)
                    .map(CategoriaResponseDTO::fromEntity);
        }
        return categoriaRepo.findByCategoriaNombreContainingIgnoreCase(nombre.trim(), pageable)
                .map(CategoriaResponseDTO::fromEntity);
    }

    @Transactional
    public CategoriaResponseDTO updateLoteSize(int categoriaId, int loteSize) {
        if (loteSize < 0) {
            throw new IllegalArgumentException("El tamano de lote debe ser mayor o igual a 0");
        }
        Categoria categoria = categoriaRepo.findById(categoriaId)
                .orElseThrow(() -> new ValidationException("No se encontro categoria con ID: " + categoriaId));
        categoria.setLoteSize(loteSize);
        return CategoriaResponseDTO.fromEntity(categoriaRepo.save(categoria));
    }

    @Transactional
    public CategoriaResponseDTO updateTiempoDiasFabricacion(int categoriaId, int tiempoDiasFabricacion) {
        if (tiempoDiasFabricacion < 0) {
            throw new IllegalArgumentException("El tiempo de fabricacion debe ser mayor o igual a 0");
        }
        Categoria categoria = categoriaRepo.findById(categoriaId)
                .orElseThrow(() -> new ValidationException("No se encontro categoria con ID: " + categoriaId));
        categoria.setTiempoDiasFabricacion(tiempoDiasFabricacion);
        return CategoriaResponseDTO.fromEntity(categoriaRepo.save(categoria));
    }

    @Transactional
    public CategoriaResponseDTO updateCapacidadProductivaDiaria(int categoriaId, int capacidadProductivaDiaria) {
        if (capacidadProductivaDiaria < 0) {
            throw new IllegalArgumentException("La capacidad productiva diaria debe ser mayor o igual a 0");
        }
        Categoria categoria = categoriaRepo.findById(categoriaId)
                .orElseThrow(() -> new ValidationException("No se encontro categoria con ID: " + categoriaId));
        categoria.setCapacidadProductivaDiaria(capacidadProductivaDiaria);
        return CategoriaResponseDTO.fromEntity(categoriaRepo.save(categoria));
    }
}
