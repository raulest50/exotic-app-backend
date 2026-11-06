package exotic.app.planta.repo.produccion.fabricacion;

import exotic.app.planta.model.produccion.fabricacion.MpsFabricacionDetalle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface MpsFabricacionDetalleRepo extends JpaRepository<MpsFabricacionDetalle, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from MpsFabricacionDetalle d where d.id = :id")
    Optional<MpsFabricacionDetalle> findByIdForUpdate(@Param("id") Long id);
}
