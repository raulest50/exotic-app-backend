package exotic.app.planta.repo.produccion.fabricacion;

import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrdenFabricacionOperacionRepo
        extends JpaRepository<OrdenFabricacionOperacion, Long> {

    @EntityGraph(attributePaths = {
            "ordenFabricacion", "ordenFabricacion.semiTerminado", "areaOperativa",
            "poeDocumentoVersion", "poeDocumentoVersion.proceso"
    })
    List<OrdenFabricacionOperacion> findByOrdenFabricacion_OrdenFabricacionIdOrderByPosicionSecuenciaAsc(
            Long ordenFabricacionId);

    boolean existsByOrdenFabricacion_OrdenFabricacionId(Long ordenFabricacionId);

    boolean existsByOrdenFabricacion_OrdenFabricacionIdAndAreaOperativa_ResponsableArea_Id(
            Long ordenFabricacionId, Long userId);

    boolean existsByOrdenFabricacion_OrdenFabricacionIdAndAreaOperativa_AreaId(
            Long ordenFabricacionId, Integer areaId);

    @EntityGraph(attributePaths = {
            "ordenFabricacion", "areaOperativa", "areaOperativa.responsableArea",
            "poeDocumentoVersion", "poeDocumentoVersion.proceso"
    })
    @Query("""
            SELECT o FROM OrdenFabricacionOperacion o
            WHERE o.id = :operacionId
              AND o.ordenFabricacion.ordenFabricacionId = :ordenFabricacionId
            """)
    Optional<OrdenFabricacionOperacion> findPoeDetalle(
            @Param("ordenFabricacionId") Long ordenFabricacionId,
            @Param("operacionId") Long operacionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrdenFabricacionOperacion o WHERE o.id = :id")
    Optional<OrdenFabricacionOperacion> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "ordenFabricacion", "ordenFabricacion.semiTerminado", "areaOperativa"
    })
    @Query("""
            SELECT o FROM OrdenFabricacionOperacion o
            WHERE o.areaOperativa.responsableArea.id = :userId
              AND o.estado IN :estados
              AND o.ordenFabricacion.estado NOT IN :estadosOrdenExcluidos
            ORDER BY o.posicionSecuencia, o.id
            """)
    List<OrdenFabricacionOperacion> findActivasPorResponsable(
            @Param("userId") Long userId,
            @Param("estados") Collection<Integer> estados,
            @Param("estadosOrdenExcluidos") Collection<EstadoOrdenFabricacion> estadosOrdenExcluidos);

    @EntityGraph(attributePaths = {
            "ordenFabricacion", "ordenFabricacion.semiTerminado", "areaOperativa"
    })
    @Query("""
            SELECT o FROM OrdenFabricacionOperacion o
            WHERE o.areaOperativa.responsableArea.id = :userId
              AND o.estado = :estado
              AND (:desde IS NULL OR o.fechaCompletado >= :desde)
              AND (:hasta IS NULL OR o.fechaCompletado < :hasta)
              AND (:search = ''
                   OR LOWER(COALESCE(o.ordenFabricacion.semiTerminado.productoId, '')) LIKE :search
                   OR LOWER(COALESCE(o.ordenFabricacion.semiTerminado.nombre, '')) LIKE :search
                   OR LOWER(COALESCE(o.procesoNombre, '')) LIKE :search
                   OR EXISTS (SELECT l.id FROM Lote l
                              WHERE l.ordenFabricacion = o.ordenFabricacion
                                AND LOWER(l.batchNumber) LIKE :search))
            ORDER BY o.fechaCompletado DESC, o.id DESC
            """)
    List<OrdenFabricacionOperacion> findCompletadasPorResponsable(
            @Param("userId") Long userId,
            @Param("estado") int estado,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("search") String search);

    long countByAreaOperativa_ResponsableArea_IdAndEstado(
            Long userId, int estado);

    @EntityGraph(attributePaths = {
            "ordenFabricacion", "ordenFabricacion.semiTerminado", "areaOperativa"
    })
    @Query("""
            SELECT o FROM OrdenFabricacionOperacion o
            WHERE o.areaOperativa.areaId = :areaId
              AND o.ordenFabricacion.estado IN :estados
              AND (:search = ''
                   OR LOWER(o.ordenFabricacion.semiTerminado.productoId) LIKE :search
                   OR LOWER(o.ordenFabricacion.semiTerminado.nombre) LIKE :search
                   OR EXISTS (SELECT l.id FROM Lote l
                              WHERE l.ordenFabricacion = o.ordenFabricacion
                                AND LOWER(l.batchNumber) LIKE :search))
            ORDER BY o.ordenFabricacion.fechaCreacion DESC, o.id DESC
            """)
    List<OrdenFabricacionOperacion> findParaDispensacionPorArea(
            @Param("areaId") Integer areaId,
            @Param("estados") Collection<EstadoOrdenFabricacion> estados,
            @Param("search") String search);

    @Query("""
            SELECT DISTINCT o.ordenFabricacion.ordenFabricacionId
            FROM OrdenFabricacionOperacion o
            WHERE o.ordenFabricacion.estado <> :estadoCancelado
              AND NOT EXISTS (
                  SELECT e.id FROM OrdenFabricacionOperacionEvento e
                  WHERE e.operacion.ordenFabricacion = o.ordenFabricacion
              )
            """)
    List<Long> findOrdenIdsPendientesBackfillLegado(
            @Param("estadoCancelado") EstadoOrdenFabricacion estadoCancelado);
}
