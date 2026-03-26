package exotic.app.planta.repo.produccion.ruprocatdesigner;

import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RutaProcesoCatRepo extends JpaRepository<RutaProcesoCat, Long> {
    Optional<RutaProcesoCat> findByCategoria_CategoriaId(int categoriaId);
    boolean existsByCategoria_CategoriaId(int categoriaId);
}
