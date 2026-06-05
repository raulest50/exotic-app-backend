package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanalObservacion;
import exotic.app.planta.model.produccion.MpsSemanalObservacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MpsSemanalObservacionRepo extends JpaRepository<MpsSemanalObservacion, Long> {
    List<MpsSemanalObservacion> findAllByMpsSemanal_MpsIdOrderByFechaCreacionAsc(Integer mpsId);
    List<MpsSemanalObservacion> findAllByMpsSemanal_MpsIdAndEstadoOrderByFechaCreacionAsc(
            Integer mpsId,
            EstadoMpsSemanalObservacion estado
    );
    List<MpsSemanalObservacion> findAllByMpsSemanal_MpsIdAndRevisionMpsOrderByFechaCreacionAsc(
            Integer mpsId,
            Integer revisionMps
    );
    long countByMpsSemanal_MpsIdAndEstado(Integer mpsId, EstadoMpsSemanalObservacion estado);
    boolean existsByMpsSemanal_MpsIdAndEstado(Integer mpsId, EstadoMpsSemanalObservacion estado);
}
