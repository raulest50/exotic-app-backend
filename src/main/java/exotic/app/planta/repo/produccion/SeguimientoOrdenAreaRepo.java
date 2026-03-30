package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    /**
     * Cuenta predecesores no completados de un nodo para una orden.
     * Un predecesor no completado es aquel que apunta al nodo target y no tiene estado COMPLETADO (2) ni OMITIDO (3)
     */
    @Query("""
        SELECT COUNT(s) FROM SeguimientoOrdenArea s
        JOIN s.rutaProcesoNode srcNode
        JOIN srcNode.rutaProcesoCat rpc
        JOIN rpc.edges e
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
        JOIN tgtNode.rutaProcesoCat rpc
        JOIN rpc.edges e
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
}
