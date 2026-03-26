package exotic.app.planta.repo.producto.procesos;

import exotic.app.planta.model.organizacion.AreaOperativa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AreaProduccionRepo extends JpaRepository<AreaOperativa, Integer>, JpaSpecificationExecutor<AreaOperativa> {
    Optional<AreaOperativa> findByNombre(String nombre);
}
