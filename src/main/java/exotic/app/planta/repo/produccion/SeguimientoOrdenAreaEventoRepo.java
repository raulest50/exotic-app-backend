package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.model.produccion.TipoEventoSeguimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface SeguimientoOrdenAreaEventoRepo extends JpaRepository<SeguimientoOrdenAreaEvento, Long> {
    List<SeguimientoOrdenAreaEvento> findBySeguimientoOrdenArea_IdOrderByFechaEventoAscIdAsc(Long seguimientoOrdenAreaId);
    List<SeguimientoOrdenAreaEvento> findBySeguimientoOrdenArea_IdInOrderByFechaEventoAscIdAsc(Collection<Long> seguimientoOrdenAreaIds);

    @Query("""
            SELECT e
            FROM SeguimientoOrdenAreaEvento e
            WHERE e.seguimientoOrdenArea.areaOperativa.areaId = :areaId
              AND e.actorTipo = :actorTipo
              AND e.tipoEvento = :tipoEvento
              AND e.usuario.id = :userId
              AND e.fechaEvento <= :instanteFoto
              AND NOT EXISTS (
                  SELECT 1
                  FROM SeguimientoOrdenAreaEvento r
                  WHERE r.eventoRevertido = e
              )
            ORDER BY e.fechaEvento DESC, e.id DESC
            """)
    List<SeguimientoOrdenAreaEvento> findReportesOperativosResponsableBefore(
            @Param("areaId") int areaId,
            @Param("actorTipo") ActorTipoEventoSeguimiento actorTipo,
            @Param("tipoEvento") TipoEventoSeguimiento tipoEvento,
            @Param("userId") Long userId,
            @Param("instanteFoto") LocalDateTime instanteFoto
    );

    @Query("""
            SELECT e
            FROM SeguimientoOrdenAreaEvento e
            WHERE e.seguimientoOrdenArea.id = :seguimientoId
              AND e.estadoDestino = :estadoDestino
              AND NOT EXISTS (
                  SELECT 1
                  FROM SeguimientoOrdenAreaEvento r
                  WHERE r.eventoRevertido = e
              )
            ORDER BY e.fechaEvento DESC, e.id DESC
            """)
    List<SeguimientoOrdenAreaEvento> findEventosActualesNoRevertidos(
            @Param("seguimientoId") Long seguimientoId,
            @Param("estadoDestino") int estadoDestino
    );

    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
            FROM SeguimientoOrdenAreaEvento e
            WHERE e.seguimientoOrdenArea.id IN :seguimientoIds
              AND e.actorTipo = :actorTipo
              AND e.tipoEvento = :tipoEvento
              AND NOT EXISTS (
                  SELECT 1
                  FROM SeguimientoOrdenAreaEvento r
                  WHERE r.eventoRevertido = e
              )
            """)
    boolean existsUnrevertedEventBySeguimientoIdsAndActorTipoAndTipoEvento(
            @Param("seguimientoIds") Collection<Long> seguimientoIds,
            @Param("actorTipo") ActorTipoEventoSeguimiento actorTipo,
            @Param("tipoEvento") TipoEventoSeguimiento tipoEvento
    );

    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
            FROM SeguimientoOrdenAreaEvento e
            WHERE e.seguimientoOrdenArea.ordenProduccion.ordenId = :ordenId
              AND e.actorTipo = :actorTipo
              AND e.tipoEvento = :tipoEvento
              AND e.estadoDestino IN :estadosInicio
              AND NOT EXISTS (
                  SELECT 1
                  FROM SeguimientoOrdenAreaEvento r
                  WHERE r.eventoRevertido = e
              )
            """)
    boolean existsUserStartOrCompletionEventByOrdenId(
            @Param("ordenId") int ordenId,
            @Param("actorTipo") ActorTipoEventoSeguimiento actorTipo,
            @Param("tipoEvento") TipoEventoSeguimiento tipoEvento,
            @Param("estadosInicio") Collection<Integer> estadosInicio
    );

    @Query("""
            SELECT e.seguimientoOrdenArea.areaOperativa.areaId AS areaId,
                   MAX(e.fechaEvento) AS ultimaTerminacionAt
            FROM SeguimientoOrdenAreaEvento e
            WHERE e.seguimientoOrdenArea.areaOperativa.areaId IN :areaIds
              AND e.actorTipo = :actorTipo
              AND e.tipoEvento = :tipoEvento
              AND e.estadoDestino = :estadoDestino
              AND NOT EXISTS (
                  SELECT 1
                  FROM SeguimientoOrdenAreaEvento r
                  WHERE r.eventoRevertido = e
              )
            GROUP BY e.seguimientoOrdenArea.areaOperativa.areaId
            """)
    List<UltimaTerminacionAreaProjection> findUltimasTerminacionesByAreaIds(
            @Param("areaIds") Collection<Integer> areaIds,
            @Param("actorTipo") ActorTipoEventoSeguimiento actorTipo,
            @Param("tipoEvento") TipoEventoSeguimiento tipoEvento,
            @Param("estadoDestino") int estadoDestino
    );

    interface UltimaTerminacionAreaProjection {
        Integer getAreaId();
        LocalDateTime getUltimaTerminacionAt();
    }
}
