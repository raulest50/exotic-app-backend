package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.despeje.DespejeLineaEjecucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DespejeLineaEjecucionRepo extends JpaRepository<DespejeLineaEjecucion, Long> {
    @Query("""
            select e from DespejeLineaEjecucion e
            left join e.seguimiento s left join e.operacion o
            where (:seguimientoId is not null and s.id = :seguimientoId)
               or (:operacionId is not null and o.id = :operacionId)
            order by e.intento desc
            """)
    List<DespejeLineaEjecucion> findIntentos(@Param("seguimientoId") Long seguimientoId,
                                           @Param("operacionId") Long operacionId);
    List<DespejeLineaEjecucion> findBySeguimiento_OrdenProduccion_OrdenIdOrderByCreadaEnAscIdAsc(int ordenId);
    List<DespejeLineaEjecucion> findByOperacion_OrdenFabricacion_OrdenFabricacionIdOrderByCreadaEnAscIdAsc(Long ordenId);
}
