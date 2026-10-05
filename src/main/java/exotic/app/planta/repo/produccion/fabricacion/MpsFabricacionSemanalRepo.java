package exotic.app.planta.repo.produccion.fabricacion;

import exotic.app.planta.model.produccion.fabricacion.MpsFabricacionSemanal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface MpsFabricacionSemanalRepo extends JpaRepository<MpsFabricacionSemanal, Long> {
    Optional<MpsFabricacionSemanal> findByWeekStartDate(LocalDate weekStartDate);

    boolean existsByDetalles_SemiTerminado_ProductoId(String productoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MpsFabricacionSemanal m where m.weekStartDate = :week")
    Optional<MpsFabricacionSemanal> findByWeekForUpdate(@Param("week") LocalDate week);
}
