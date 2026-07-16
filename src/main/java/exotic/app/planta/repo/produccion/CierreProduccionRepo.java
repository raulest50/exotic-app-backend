package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.CierreProduccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CierreProduccionRepo extends JpaRepository<CierreProduccion, Long> {
    Optional<CierreProduccion> findByIdempotencyKey(UUID idempotencyKey);
}
