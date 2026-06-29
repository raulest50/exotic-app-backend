package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeguimientoOrdenAreaEventoRepo extends JpaRepository<SeguimientoOrdenAreaEvento, Long> {
    List<SeguimientoOrdenAreaEvento> findBySeguimientoOrdenArea_IdOrderByFechaEventoAscIdAsc(Long seguimientoOrdenAreaId);
    List<SeguimientoOrdenAreaEvento> findBySeguimientoOrdenArea_IdInOrderByFechaEventoAscIdAsc(Collection<Long> seguimientoOrdenAreaIds);
    Optional<SeguimientoOrdenAreaEvento> findFirstBySeguimientoOrdenArea_AreaOperativa_AreaIdAndActorTipoAndUsuario_IdAndFechaEventoLessThanEqualOrderByFechaEventoDescIdDesc(
            int areaId,
            ActorTipoEventoSeguimiento actorTipo,
            Long userId,
            LocalDateTime instanteFoto
    );

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

    @Query("""
            SELECT e.seguimientoOrdenArea.areaOperativa.areaId AS areaId,
                   MAX(e.fechaEvento) AS ultimaTerminacionAt
            FROM SeguimientoOrdenAreaEvento e
            WHERE e.seguimientoOrdenArea.areaOperativa.areaId IN :areaIds
              AND e.actorTipo = :actorTipo
              AND e.estadoDestino = :estadoDestino
            GROUP BY e.seguimientoOrdenArea.areaOperativa.areaId
            """)
    List<UltimaTerminacionAreaProjection> findUltimasTerminacionesByAreaIds(
            @Param("areaIds") Collection<Integer> areaIds,
            @Param("actorTipo") ActorTipoEventoSeguimiento actorTipo,
            @Param("estadoDestino") int estadoDestino
    );

    interface UltimaTerminacionAreaProjection {
        Integer getAreaId();
        LocalDateTime getUltimaTerminacionAt();
    }
}
