package exotic.app.planta.repo.producto.manufacturing.templates;

import exotic.app.planta.model.producto.manufacturing.templates.CategoriaManufacturingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoriaManufacturingTemplateRepo extends JpaRepository<CategoriaManufacturingTemplate, Long> {

    Optional<CategoriaManufacturingTemplate> findByCategoria_CategoriaId(int categoriaId);

    boolean existsByCategoria_CategoriaId(int categoriaId);
}
