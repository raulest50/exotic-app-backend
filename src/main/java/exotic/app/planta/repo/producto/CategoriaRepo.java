package exotic.app.planta.repo.producto;

import exotic.app.planta.model.producto.Categoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoriaRepo extends JpaRepository<Categoria, Integer> {
    
    /**
     * Checks if a category with the given name exists
     * @param categoriaNombre the name to check
     * @return true if a category with the given name exists, false otherwise
     */
    boolean existsByCategoriaNombre(String categoriaNombre);
    
    /**
     * Finds a category by its name
     * @param categoriaNombre the name to search for
     * @return the category with the given name, or null if none exists
     */
    Categoria findByCategoriaNombre(String categoriaNombre);

    /**
     * Finds categories whose name contains the given text (case-insensitive)
     * @param nombre the name or partial name to search for
     * @param pageable pagination and sort parameters
     * @return page of categories matching the search
     */
    Page<Categoria> findByCategoriaNombreContainingIgnoreCase(String nombre, Pageable pageable);
}