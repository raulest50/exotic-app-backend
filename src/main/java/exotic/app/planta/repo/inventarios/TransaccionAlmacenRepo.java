package exotic.app.planta.repo.inventarios;

import exotic.app.planta.model.inventarios.Movimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransaccionAlmacenRepo extends JpaRepository<Movimiento, Integer> {

    @Query("SELECT COALESCE(SUM(m.cantidad), 0) FROM Movimiento m WHERE m.producto.productoId = :productoId")
    Double findTotalCantidadByProductoId(@Param("productoId") String productoId);

    @Query("SELECT COALESCE(SUM(m.cantidad), 0) FROM Movimiento m WHERE m.producto.productoId = :productoId AND m.fechaMovimiento < :fecha")
    Double findTotalCantidadByProductoIdAndFechaMovimientoBefore(@Param("productoId") String productoId, @Param("fecha") LocalDateTime fecha);

    List<Movimiento> findMovimientosByCantidad(Double cantidad);

    // Get movimientos filtered by product ID
    List<Movimiento> findByProducto_ProductoId(String productoId);

    // New method
    Page<Movimiento> findByProducto_ProductoIdOrderByFechaMovimientoDesc(String productoId, Pageable pageable);

    List<Movimiento> findByProducto_ProductoIdAndFechaMovimientoBetweenOrderByFechaMovimientoAsc(String productoId, LocalDateTime start, LocalDateTime end);

    Page<Movimiento> findByProducto_ProductoIdAndFechaMovimientoBetweenOrderByFechaMovimientoAscMovimientoIdAsc(
            String productoId,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    List<Movimiento> findByProducto_ProductoIdAndFechaMovimientoBetweenOrderByFechaMovimientoAscMovimientoIdAsc(
            String productoId,
            LocalDateTime start,
            LocalDateTime end
    );

    /**
     * Suma cantidades dentro de un rango, antes de un cursor (fechaMovimiento, movimientoId).
     * Se usa para calcular el saldo acumulado de páginas intermedias del kardex.
     */
    @Query("""
            SELECT COALESCE(SUM(m.cantidad), 0)
            FROM Movimiento m
            WHERE m.producto.productoId = :productoId
              AND m.fechaMovimiento >= :start
              AND m.fechaMovimiento <= :end
              AND (
                   m.fechaMovimiento < :cursorFecha
                   OR (m.fechaMovimiento = :cursorFecha AND m.movimientoId < :cursorId)
              )
            """)
    Double sumCantidadInRangeBeforeCursor(
            @Param("productoId") String productoId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("cursorFecha") LocalDateTime cursorFecha,
            @Param("cursorId") int cursorId
    );

    /**
     * Encuentra lotes con stock disponible para un producto específico,
     * ordenados únicamente por fecha de vencimiento (primero los más próximos a vencer).
     *
     * @param productoId ID del producto
     * @return Lista de objetos con [Lote, cantidadDisponible]
     */
    @Query(value = "SELECT l, SUM(m.cantidad) as stock_disponible " +
                   "FROM Movimiento m " +
                   "JOIN m.lote l " +
                   "WHERE m.producto.productoId = :productoId " +
                   "AND m.lote IS NOT NULL " +
                   "GROUP BY l " +
                   "HAVING SUM(m.cantidad) > 0 " +
                   "ORDER BY l.expirationDate ASC NULLS LAST")
    List<Object[]> findLotesWithStockByProductoIdOrderByExpirationDate(@Param("productoId") String productoId);

    /**
     * Versión alternativa usando SQL nativo en caso de que la consulta JPQL presente problemas.
     * Encuentra lotes con stock disponible para un producto específico,
     * ordenados únicamente por fecha de vencimiento (primero los más próximos a vencer).
     *
     * @param productoId ID del producto
     * @return Lista de objetos con [Lote, cantidadDisponible]
     */
    @Query(value = "SELECT l.*, SUM(m.cantidad) as stock_disponible " +
                   "FROM lote l " +
                   "JOIN movimientos m ON m.lote_id = l.id " +
                   "WHERE m.producto_id = :productoId " +
                   "GROUP BY l.id, l.expiration_date, l.production_date " +
                   "HAVING SUM(m.cantidad) > 0 " +
                   "ORDER BY l.expiration_date ASC NULLS LAST", 
           nativeQuery = true)
    List<Object[]> findLotesWithStockByProductoIdNative(@Param("productoId") String productoId);

    boolean existsByProducto_ProductoId(String productoId);
}
