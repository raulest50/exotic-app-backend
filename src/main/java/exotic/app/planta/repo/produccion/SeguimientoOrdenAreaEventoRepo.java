package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SeguimientoOrdenAreaEventoRepo extends JpaRepository<SeguimientoOrdenAreaEvento, Long> {
    List<SeguimientoOrdenAreaEvento> findBySeguimientoOrdenArea_IdOrderByFechaEventoAscIdAsc(Long seguimientoOrdenAreaId);
    List<SeguimientoOrdenAreaEvento> findBySeguimientoOrdenArea_IdInOrderByFechaEventoAscIdAsc(Collection<Long> seguimientoOrdenAreaIds);
}
