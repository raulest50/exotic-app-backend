package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SeguimientoOrdenAreaEventoRepo extends JpaRepository<SeguimientoOrdenAreaEvento, Long> {
    List<SeguimientoOrdenAreaEvento> findBySeguimientoOrdenArea_IdOrderByFechaEventoAscIdAsc(Long seguimientoOrdenAreaId);
    List<SeguimientoOrdenAreaEvento> findBySeguimientoOrdenArea_IdInOrderByFechaEventoAscIdAsc(Collection<Long> seguimientoOrdenAreaIds);

    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
            FROM SeguimientoOrdenAreaEvento e
            WHERE e.seguimientoOrdenArea.ordenProduccion.ordenId = :ordenId
              AND e.actorTipo = :actorTipo
              AND e.estadoDestino IN :estadosInicio
            """)
    boolean existsUserStartOrCompletionEventByOrdenId(
            @Param("ordenId") int ordenId,
            @Param("actorTipo") ActorTipoEventoSeguimiento actorTipo,
            @Param("estadosInicio") Collection<Integer> estadosInicio
    );
}
