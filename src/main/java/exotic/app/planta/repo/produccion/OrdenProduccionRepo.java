package exotic.app.planta.repo.produccion;

import jakarta.transaction.Transactional;
import exotic.app.planta.model.produccion.OrdenProduccion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrdenProduccionRepo extends JpaRepository<OrdenProduccion, Integer> {

    List<OrdenProduccion> findByEstadoOrden(int estadoOrden);

    /**
     * Counts the number of production orders for a specific product with a specific status
     * 
     * @param productoId ID of the product
     * @param estadoOrden Status of the order (0: in production, 1: finished)
     * @return Count of orders
     */
    long countByProducto_ProductoIdAndEstadoOrden(String productoId, int estadoOrden);

    /**
     * Finds all production orders related to a specific product
     * 
     * @param productoId ID of the product
     * @return List of production orders
     */
    List<OrdenProduccion> findByProducto_ProductoId(String productoId);


    /**
     * Finds OrdenProduccion within a date range and estadoOrden.
     * If estadoOrden is 2, it ignores the estadoOrden filter.
     * If productoId is provided, it filters by the given product.
     *
     * @param startDate   Start of the date range.
     * @param endDate     End of the date range.
     * @param estadoOrden Estado of the orden.
     * @param productoId  Optional product identifier to filter by.
     * @param pageable    Pagination information.
     * @return Page of OrdenProduccion matching the criteria.
     */
    @EntityGraph(attributePaths = {"ordenesSeguimiento", "producto"})
    @Query("SELECT o FROM OrdenProduccion o WHERE o.fechaCreacion BETWEEN :startDate AND :endDate " +
            "AND (:estadoOrden = 2 OR o.estadoOrden = :estadoOrden) " +
            "AND (:productoId IS NULL OR :productoId = '' OR o.producto.productoId = :productoId) " +
            "ORDER BY o.fechaCreacion")
    Page<OrdenProduccion> findByFechaCreacionBetweenAndEstadoOrden(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("estadoOrden") int estadoOrden,
            @Param("productoId") String productoId,
            Pageable pageable
    );


    @Transactional
    @Modifying
    @Query("UPDATE OrdenProduccion o SET o.estadoOrden = :estadoOrden, o.fechaFinal = CURRENT_TIMESTAMP WHERE o.ordenId = :id")
    void updateEstadoOrdenById(@Param("id") int id, @Param("estadoOrden") int estadoOrden);

    /**
     * Encuentra todas las órdenes de producción en estado abierto (0) o en curso (1)
     * 
     * @param pageable Información de paginación
     * @return Página de órdenes de producción
     */
    @EntityGraph(attributePaths = {"ordenesSeguimiento", "producto"})
    @Query("SELECT o FROM OrdenProduccion o WHERE o.estadoOrden <> 2 AND o.estadoOrden <> -1 ORDER BY o.fechaCreacion DESC")
    Page<OrdenProduccion> findByEstadoOrdenOpenOrInProgress(Pageable pageable);

    @EntityGraph(attributePaths = {"ordenesSeguimiento", "producto"})
    @Query("SELECT o FROM OrdenProduccion o WHERE o.loteAsignado LIKE %:loteAsignado%")
    Page<OrdenProduccion> findByLoteAsignadoContaining(
            @Param("loteAsignado") String loteAsignado,
            Pageable pageable
    );
}
