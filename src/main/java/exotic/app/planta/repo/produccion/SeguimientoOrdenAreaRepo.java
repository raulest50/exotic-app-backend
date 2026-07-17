package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.EstadoMpsSemanalItem;
import exotic.app.planta.model.produccion.EstadoMpsSemanalLotePlanificado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeguimientoOrdenAreaRepo extends JpaRepository<SeguimientoOrdenArea, Long> {

    /**
     * Obtiene todos los seguimientos de una orden de producción
     */
    List<SeguimientoOrdenArea> findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(int ordenId);

    /**
     * Obtiene un seguimiento específico por orden y nodo
     */
    Optional<SeguimientoOrdenArea> findByOrdenProduccion_OrdenIdAndRutaProcesoNode_Id(int ordenId, Long nodeId);

    @Query("""
        SELECT DISTINCT item.id AS mpsItemId,
               lote.id AS mpsLotePlanificadoId
        FROM SeguimientoOrdenArea s
        JOIN s.ordenProduccion op
        JOIN op.mpsLotePlanificado lote
        JOIN lote.mpsItem item
        WHERE op.mpsSemanal.mpsId = :mpsId
        AND s.areaOperativa.areaId = :areaId
        AND op.estadoOrden <> -1
        AND (item.estado IS NULL OR item.estado <> :cancelledItemEstado)
        AND (lote.estado IS NULL OR lote.estado <> :cancelledLoteEstado)
        """)
    List<MpsIntervencionAreaProjection> findMpsIntervencionesByMpsIdAndAreaId(
            @Param("mpsId") Integer mpsId,
            @Param("areaId") int areaId,
            @Param("cancelledItemEstado") EstadoMpsSemanalItem cancelledItemEstado,
            @Param("cancelledLoteEstado") EstadoMpsSemanalLotePlanificado cancelledLoteEstado
    );

    /**
     * Obtiene órdenes visibles para un área específica (estado = VISIBLE)
     */
    @Query("""
        SELECT s FROM SeguimientoOrdenArea s
        JOIN FETCH s.ordenProduccion op
        JOIN FETCH op.producto p
        LEFT JOIN FETCH s.areaOperativa
        WHERE s.areaOperativa.areaId = :areaId
        AND s.estado = 1
        AND op.estadoOrden NOT IN (-1, 2)
        ORDER BY s.fechaVisible ASC
        """)
    Page<SeguimientoOrdenArea> findOrdenesVisiblesByAreaId(@Param("areaId") int areaId, Pageable pageable);

    /**
     * Obtiene órdenes visibles para un usuario que es responsable de áreas
     */
    @Query("""
        SELECT s FROM SeguimientoOrdenArea s
        JOIN FETCH s.ordenProduccion op
        JOIN FETCH op.producto p
        JOIN FETCH s.areaOperativa a
        WHERE a.responsableArea.id = :userId
        AND s.estado = 1
        AND op.estadoOrden NOT IN (-1, 2)
        ORDER BY s.fechaVisible ASC
        """)
    Page<SeguimientoOrdenArea> findOrdenesVisiblesByResponsableUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT s FROM SeguimientoOrdenArea s
        JOIN FETCH s.ordenProduccion op
        JOIN FETCH op.producto p
        JOIN FETCH s.areaOperativa a
        JOIN FETCH s.rutaProcesoNode n
        WHERE a.responsableArea.id = :userId
        AND op.estadoOrden != -1
        AND s.estado IN :activeStates
        ORDER BY s.posicionSecuencia ASC, s.id ASC
        """)
    List<SeguimientoOrdenArea> findTableroActivosByResponsableUserId(
            @Param("userId") Long userId,
            @Param("activeStates") Collection<Integer> activeStates
    );

    @Query(
            value = """
                SELECT s FROM SeguimientoOrdenArea s
                JOIN FETCH s.ordenProduccion op
                JOIN FETCH op.producto p
                JOIN FETCH s.areaOperativa a
                JOIN FETCH s.rutaProcesoNode n
                WHERE a.responsableArea.id = :userId
                AND op.estadoOrden != -1
                AND s.estado = :completedState
                AND (
                    :searchPattern = ''
                    OR LOWER(COALESCE(op.loteAsignado, '')) LIKE :searchPattern
                    OR LOWER(CONCAT('op-', CAST(op.ordenId AS string))) LIKE :searchPattern
                    OR LOWER(COALESCE(p.productoId, '')) LIKE :searchPattern
                    OR LOWER(COALESCE(p.nombre, '')) LIKE :searchPattern
                    OR LOWER(COALESCE(a.nombre, '')) LIKE :searchPattern
                    OR LOWER(COALESCE(n.label, '')) LIKE :searchPattern
                )
                ORDER BY
                    CASE WHEN s.fechaCompletado IS NULL THEN 1 ELSE 0 END ASC,
                    s.fechaCompletado DESC,
                    s.id DESC
                """,
            countQuery = """
                SELECT COUNT(s) FROM SeguimientoOrdenArea s
                JOIN s.ordenProduccion op
                JOIN op.producto p
                JOIN s.areaOperativa a
                JOIN s.rutaProcesoNode n
                WHERE a.responsableArea.id = :userId
                AND op.estadoOrden != -1
                AND s.estado = :completedState
                AND (
                    :searchPattern = ''
                    OR LOWER(COALESCE(op.loteAsignado, '')) LIKE :searchPattern
                    OR LOWER(CONCAT('op-', CAST(op.ordenId AS string))) LIKE :searchPattern
                    OR LOWER(COALESCE(p.productoId, '')) LIKE :searchPattern
                    OR LOWER(COALESCE(p.nombre, '')) LIKE :searchPattern
                    OR LOWER(COALESCE(a.nombre, '')) LIKE :searchPattern
                    OR LOWER(COALESCE(n.label, '')) LIKE :searchPattern
                )
                """
    )
    Page<SeguimientoOrdenArea> findTableroCompletadosHistoricosByResponsableUserId(
            @Param("userId") Long userId,
            @Param("completedState") int completedState,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(s) FROM SeguimientoOrdenArea s
        JOIN s.ordenProduccion op
        JOIN s.areaOperativa a
        WHERE a.responsableArea.id = :userId
        AND op.estadoOrden != -1
        AND s.estado = :completedState
        """)
    long countTableroCompletadosHistoricosByResponsableUserId(
            @Param("userId") Long userId,
            @Param("completedState") int completedState
    );

    @Query("""
        SELECT s FROM SeguimientoOrdenArea s
        JOIN FETCH s.ordenProduccion op
        JOIN FETCH op.producto p
        JOIN FETCH s.areaOperativa a
        JOIN FETCH s.rutaProcesoNode n
        WHERE a.responsableArea.id = :userId
        AND op.estadoOrden != -1
        AND s.estado = :completedState
        AND s.fechaCompletado >= :periodStart
        AND s.fechaCompletado < :periodEndExclusive
        ORDER BY s.posicionSecuencia ASC, s.id ASC
        """)
    List<SeguimientoOrdenArea> findTableroCompletadosByResponsableUserIdAndFechaCompletadoBetween(
            @Param("userId") Long userId,
            @Param("completedState") int completedState,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEndExclusive") LocalDateTime periodEndExclusive
    );

    @Query("""
        SELECT s FROM SeguimientoOrdenArea s
        JOIN FETCH s.ordenProduccion op
        JOIN FETCH op.producto p
        JOIN FETCH s.areaOperativa a
        JOIN FETCH s.rutaProcesoNode n
        WHERE a.areaId = :areaId
        AND op.estadoOrden != -1
        ORDER BY s.posicionSecuencia ASC, s.id ASC
        """)
    List<SeguimientoOrdenArea> findTableroByAreaId(@Param("areaId") int areaId);

    @Query("""
        SELECT s FROM SeguimientoOrdenArea s
        JOIN FETCH s.ordenProduccion op
        JOIN FETCH op.producto p
        JOIN FETCH s.areaOperativa a
        JOIN FETCH s.rutaProcesoNode n
        WHERE op.ordenId = :ordenId
        ORDER BY s.posicionSecuencia ASC
        """)
    List<SeguimientoOrdenArea> findDetalleByOrdenId(@Param("ordenId") int ordenId);

    /**
     * Cuenta predecesores no completados de un nodo para una orden.
     * Un predecesor no completado es aquel que apunta al nodo target y no tiene estado COMPLETADO (2) ni OMITIDO (3)
     */
    @Query("""
        SELECT COUNT(s) FROM SeguimientoOrdenArea s
        JOIN s.rutaProcesoNode srcNode
        JOIN srcNode.rutaProcesoCatVersion rpcv
        JOIN rpcv.edges e
        WHERE s.ordenProduccion.ordenId = :ordenId
        AND e.sourceNode.id = srcNode.id
        AND e.targetNode.id = :targetNodeId
        AND s.estado NOT IN (2, 3)
        """)
    long countPredecesoresNoCompletados(@Param("ordenId") int ordenId, @Param("targetNodeId") Long targetNodeId);

    /**
     * Obtiene los seguimientos de nodos sucesores pendientes (estado = PENDIENTE)
     */
    @Query("""
        SELECT s FROM SeguimientoOrdenArea s
        JOIN s.rutaProcesoNode tgtNode
        JOIN tgtNode.rutaProcesoCatVersion rpcv
        JOIN rpcv.edges e
        WHERE s.ordenProduccion.ordenId = :ordenId
        AND e.targetNode.id = tgtNode.id
        AND e.sourceNode.id = :sourceNodeId
        AND s.estado = 0
        """)
    List<SeguimientoOrdenArea> findSucesoresPendientes(@Param("ordenId") int ordenId, @Param("sourceNodeId") Long sourceNodeId);

    /**
     * Verifica si existen registros de seguimiento para una orden
     */
    boolean existsByOrdenProduccion_OrdenId(int ordenId);

    /**
     * Obtiene el seguimiento de una orden para un área específica que esté VISIBLE
     */
    Optional<SeguimientoOrdenArea> findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(
            int ordenId, int areaId, int estado);

    @Query("""
        SELECT ag FROM SeguimientoOrdenArea ag
        JOIN FETCH ag.ordenProduccion op
        JOIN FETCH ag.areaOperativa a
        JOIN FETCH ag.rutaProcesoNode n
        WHERE a.areaId = :almacenGeneralAreaId
        AND ag.estado = :estadoEspera
        AND op.estadoOrden NOT IN (-1, 2)
        AND NOT EXISTS (
            SELECT 1 FROM SeguimientoOrdenArea s
            WHERE s.ordenProduccion.ordenId = op.ordenId
            AND s.areaOperativa.areaId <> :almacenGeneralAreaId
            AND s.estado <> :estadoCola
        )
        ORDER BY op.ordenId ASC
        """)
    List<SeguimientoOrdenArea> findCandidatosRetroactividadDispensacionNoBloqueante(
            @Param("almacenGeneralAreaId") int almacenGeneralAreaId,
            @Param("estadoEspera") int estadoEspera,
            @Param("estadoCola") int estadoCola
    );

    @Query("""
        SELECT s.areaOperativa.areaId AS areaId,
               COUNT(s.id) AS total
        FROM SeguimientoOrdenArea s
        WHERE s.areaOperativa.areaId IN :areaIds
        AND s.estado IN :estados
        AND s.ordenProduccion.estadoOrden NOT IN (-1, 2)
        GROUP BY s.areaOperativa.areaId
        """)
    List<CargaActivaAreaProjection> countCargaActivaByAreaIds(
            @Param("areaIds") Collection<Integer> areaIds,
            @Param("estados") Collection<Integer> estados
    );

    interface CargaActivaAreaProjection {
        Integer getAreaId();
        Long getTotal();
    }

    interface MpsIntervencionAreaProjection {
        Long getMpsItemId();
        Long getMpsLotePlanificadoId();
    }
}
