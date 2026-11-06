package exotic.app.planta.repo.produccion.fabricacion;

import exotic.app.planta.model.produccion.EstadoMpsSemanalObservacion;
import exotic.app.planta.model.produccion.fabricacion.MpsFabricacionObservacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface MpsFabricacionObservacionRepo extends JpaRepository<MpsFabricacionObservacion, Long> {
    List<MpsFabricacionObservacion> findByMps_IdOrderByFechaCreacionAscObservacionIdAsc(Long id);
    long countByMps_IdAndEstadoIn(Long id, Collection<EstadoMpsSemanalObservacion> estados);
    @Query("select o.mps.weekStartDate from MpsFabricacionObservacion o where o.observacionId = :id")
    Optional<LocalDate> findWeekStartDateById(@Param("id") Long id);
}
