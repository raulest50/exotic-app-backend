package exotic.app.planta.repo.inventarios;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Material;
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

    /**
     * Todos los materiales con stock agregado (suma de movimientos). Una fila por material.
     */
    @Query("""
            SELECT m, COALESCE(SUM(mov.cantidad), 0.0)
            FROM Material m
            LEFT JOIN Movimiento mov ON mov.producto = m
            GROUP BY m
            """)
    List<Object[]> findAllMaterialsWithStock();

    @Query("SELECT COALESCE(SUM(m.cantidad), 0) FROM Movimiento m WHERE m.producto.productoId = :productoId AND m.fechaMovimiento < :fecha")
    Double findTotalCantidadByProductoIdAndFechaMovimientoBefore(@Param("productoId") String productoId, @Param("fecha") LocalDateTime fecha);

    @Query("SELECT COALESCE(SUM(m.cantidad), 0) FROM Movimiento m WHERE m.producto.productoId = :productoId AND m.almacen = :almacen AND m.fechaMovimiento < :fecha")
    Double findTotalCantidadByProductoIdAndAlmacenAndFechaMovimientoBefore(@Param("productoId") String productoId, @Param("almacen") Movimiento.Almacen almacen, @Param("fecha") LocalDateTime fecha);

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

    Page<Movimiento> findByProducto_ProductoIdAndAlmacenAndFechaMovimientoBetweenOrderByFechaMovimientoAscMovimientoIdAsc(
            String productoId,
            Movimiento.Almacen almacen,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    List<Movimiento> findByProducto_ProductoIdAndFechaMovimientoBetweenOrderByFechaMovimientoAscMovimientoIdAsc(
            String productoId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<Movimiento> findByProducto_ProductoIdAndAlmacenAndFechaMovimientoBetweenOrderByFechaMovimientoAscMovimientoIdAsc(
            String productoId,
            Movimiento.Almacen almacen,
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

    @Query("""
            SELECT COALESCE(SUM(m.cantidad), 0)
            FROM Movimiento m
            WHERE m.producto.productoId = :productoId
              AND m.almacen = :almacen
              AND m.fechaMovimiento >= :start
              AND m.fechaMovimiento <= :end
              AND (
                   m.fechaMovimiento < :cursorFecha
                   OR (m.fechaMovimiento = :cursorFecha AND m.movimientoId < :cursorId)
              )
            """)
    Double sumCantidadInRangeBeforeCursorAndAlmacen(
            @Param("productoId") String productoId,
            @Param("almacen") Movimiento.Almacen almacen,
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

    long countByLote_Id(Long loteId);

    @Query("SELECT m.producto.productoId, m.producto.nombre, m.producto.tipoUnidades, " +
           "m.lote.id, m.lote.batchNumber, SUM(m.cantidad) " +
           "FROM Movimiento m " +
           "WHERE TYPE(m.producto) = Material " +
           "AND m.lote IS NOT NULL " +
           "AND m.almacen = :almacen " +
           "AND LOWER(m.lote.batchNumber) LIKE LOWER(CONCAT('%', :batchNumber, '%')) " +
           "GROUP BY m.producto.productoId, m.producto.nombre, m.producto.tipoUnidades, " +
           "m.lote.id, m.lote.batchNumber " +
           "HAVING SUM(m.cantidad) > 0 " +
           "ORDER BY m.lote.batchNumber ASC")
    List<Object[]> findMaterialesWithStockByBatchNumberAndAlmacen(
            @Param("batchNumber") String batchNumber,
            @Param("almacen") Movimiento.Almacen almacen);
}
