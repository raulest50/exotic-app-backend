package exotic.app.planta.repo.producto;

import exotic.app.planta.model.producto.PoolCapacidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PoolCapacidadRepo extends JpaRepository<PoolCapacidad, Integer> {
    boolean existsByNombreIgnoreCase(String nombre);
}
