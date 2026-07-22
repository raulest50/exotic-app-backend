package exotic.app.planta.repo.compras;

import exotic.app.planta.model.bi.dto.ProveedorMaterialOrdenHistRowDTO;
import exotic.app.planta.model.compras.ItemOrdenCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

public interface ItemOrdenCompraRepo extends JpaRepository<ItemOrdenCompra, Integer> {
    interface PendingPurchaseItemProjection {
        int getItemId();
        int getOcmId();
        LocalDateTime getFechaEmision();
        String getProveedor();
        String getProductoId();
        String getProductoNombre();
        String getUnidadMedida();
        double getCantidad();
        double getPrecioUnitario();
    }

    @Query("""
            SELECT item FROM ItemOrdenCompra item
            JOIN FETCH item.ordenCompraMateriales orden
            JOIN FETCH orden.proveedor
            JOIN FETCH item.material
            WHERE orden.estado = :estado
            ORDER BY orden.fechaEmision DESC, orden.ordenCompraId DESC, item.itemOrdenId
            """)
    List<ItemOrdenCompra> findAllByOrdenEstadoForBi(@Param("estado") int estado);

    @Query("""
            SELECT item.itemOrdenId AS itemId,
                   orden.ordenCompraId AS ocmId,
                   orden.fechaEmision AS fechaEmision,
                   proveedor.nombre AS proveedor,
                   material.productoId AS productoId,
                   material.nombre AS productoNombre,
                   material.tipoUnidades AS unidadMedida,
                   item.cantidad AS cantidad,
                   item.precioUnitario AS precioUnitario
            FROM ItemOrdenCompra item
            JOIN item.ordenCompraMateriales orden
            JOIN orden.proveedor proveedor
            JOIN item.material material
            WHERE orden.estado = :estado
            ORDER BY orden.fechaEmision ASC, orden.ordenCompraId ASC, item.itemOrdenId ASC
            """)
    List<PendingPurchaseItemProjection> findPendingRowsForBi(@Param("estado") int estado);

    @Query("""
            SELECT item.itemOrdenId AS itemId,
                   orden.ordenCompraId AS ocmId,
                   orden.fechaEmision AS fechaEmision,
                   proveedor.nombre AS proveedor,
                   material.productoId AS productoId,
                   material.nombre AS productoNombre,
                   material.tipoUnidades AS unidadMedida,
                   item.cantidad AS cantidad,
                   item.precioUnitario AS precioUnitario
            FROM ItemOrdenCompra item
            JOIN item.ordenCompraMateriales orden
            JOIN orden.proveedor proveedor
            JOIN item.material material
            WHERE orden.estado = :estado
              AND orden.ordenCompraId IN :ocmIds
            ORDER BY orden.fechaEmision ASC, orden.ordenCompraId ASC, item.itemOrdenId ASC
            """)
    List<PendingPurchaseItemProjection> findPendingRowsForBiByOrderIds(
            @Param("estado") int estado,
            @Param("ocmIds") Collection<Integer> ocmIds);

    @Query(
            value = """
                    SELECT orden.ordenCompraId
                    FROM OrdenCompraMateriales orden
                    WHERE orden.estado = :estado
                      AND EXISTS (
                          SELECT item.itemOrdenId
                          FROM ItemOrdenCompra item
                          WHERE item.ordenCompraMateriales = orden
                            AND (SELECT COALESCE(SUM(item2.cantidad), 0)
                                 FROM ItemOrdenCompra item2
                                 WHERE item2.ordenCompraMateriales = orden
                                   AND item2.material = item.material)
                                >
                                (SELECT COALESCE(SUM(movimiento.cantidad), 0)
                                 FROM Movimiento movimiento
                                 JOIN movimiento.transaccionAlmacen transaccion
                                 WHERE movimiento.almacen = :almacen
                                   AND movimiento.tipoMovimiento = :tipoMovimiento
                                   AND movimiento.cantidad > 0
                                   AND transaccion.tipoEntidadCausante = :causante
                                   AND transaccion.idEntidadCausante = orden.ordenCompraId
                                   AND movimiento.producto = item.material)
                      )
                    ORDER BY orden.fechaEmision ASC, orden.ordenCompraId ASC
                    """,
            countQuery = """
                    SELECT COUNT(orden.ordenCompraId)
                    FROM OrdenCompraMateriales orden
                    WHERE orden.estado = :estado
                      AND EXISTS (
                          SELECT item.itemOrdenId
                          FROM ItemOrdenCompra item
                          WHERE item.ordenCompraMateriales = orden
                            AND (SELECT COALESCE(SUM(item2.cantidad), 0)
                                 FROM ItemOrdenCompra item2
                                 WHERE item2.ordenCompraMateriales = orden
                                   AND item2.material = item.material)
                                >
                                (SELECT COALESCE(SUM(movimiento.cantidad), 0)
                                 FROM Movimiento movimiento
                                 JOIN movimiento.transaccionAlmacen transaccion
                                 WHERE movimiento.almacen = :almacen
                                   AND movimiento.tipoMovimiento = :tipoMovimiento
                                   AND movimiento.cantidad > 0
                                   AND transaccion.tipoEntidadCausante = :causante
                                   AND transaccion.idEntidadCausante = orden.ordenCompraId
                                   AND movimiento.producto = item.material)
                      )
                    """)
    Page<Integer> findPendingOrderIdsForBi(
            @Param("estado") int estado,
            @Param("almacen") exotic.app.planta.model.inventarios.Movimiento.Almacen almacen,
            @Param("tipoMovimiento") exotic.app.planta.model.inventarios.Movimiento.TipoMovimiento tipoMovimiento,
            @Param("causante") exotic.app.planta.model.inventarios.TransaccionAlmacen.TipoEntidadCausante causante,
            Pageable pageable);
    boolean existsByMaterial_ProductoId(String productoId);

    @Query("""
            SELECT item
            FROM ItemOrdenCompra item
            JOIN FETCH item.ordenCompraMateriales orden
            LEFT JOIN FETCH orden.proveedor proveedor
            JOIN FETCH item.material material
            WHERE material.productoId = :productoId
            """)
    List<ItemOrdenCompra> findByMaterial_ProductoId(@Param("productoId") String productoId);

    /**
     * Busca todos los items de orden de compra asociados a una orden por su ID.
     *
     * @param ordenCompraId ID de la orden de compra
     * @return Lista de items de esa orden
     */
    List<ItemOrdenCompra> findByOrdenCompraMateriales_OrdenCompraId(int ordenCompraId);

    long countByOrdenCompraMateriales_OrdenCompraId(int ordenCompraId);

    @Query("""
            SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END
            FROM ItemOrdenCompra item
            JOIN item.ordenCompraMateriales orden
            JOIN item.material material
            WHERE material.productoId = :productoId
              AND orden.estado IN :estados
            """)
    boolean existsByMaterialProductoIdAndOrdenCompraEstadoIn(
            @Param("productoId") String productoId,
            @Param("estados") Collection<Integer> estados);

    @Query("""
            SELECT item
            FROM ItemOrdenCompra item
            JOIN FETCH item.ordenCompraMateriales orden
            JOIN FETCH item.material material
            WHERE orden.estado = :estado
              AND material.productoId IN :productoIds
            """)
    List<ItemOrdenCompra> findPendientesIngresoByMaterialProductoIds(
            @Param("productoIds") Collection<String> productoIds,
            @Param("estado") int estado);

    @Query("""
            SELECT new exotic.app.planta.model.bi.dto.ProveedorMaterialOrdenHistRowDTO(
                orden.ordenCompraId,
                proveedor.id,
                proveedor.nombre,
                material.productoId,
                material.nombre,
                orden.fechaEmision,
                orden.fechaEnvioProveedor,
                item.cantidad
            )
            FROM ItemOrdenCompra item
            JOIN item.ordenCompraMateriales orden
            JOIN orden.proveedor proveedor
            JOIN item.material material
            WHERE material.productoId = :materialId
              AND COALESCE(orden.fechaEnvioProveedor, orden.fechaEmision) >= :start
              AND COALESCE(orden.fechaEnvioProveedor, orden.fechaEmision) <= :end
              AND (:proveedorId IS NULL OR proveedor.id = :proveedorId)
            ORDER BY COALESCE(orden.fechaEnvioProveedor, orden.fechaEmision) ASC, orden.ordenCompraId ASC
            """)
    List<ProveedorMaterialOrdenHistRowDTO> findLeadTimeOrderHistory(
            @Param("materialId") String materialId,
            @Param("proveedorId") String proveedorId,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );

    @Query("""
            SELECT new exotic.app.planta.model.bi.dto.ProveedorMaterialOrdenHistRowDTO(
                orden.ordenCompraId,
                proveedor.id,
                proveedor.nombre,
                material.productoId,
                material.nombre,
                orden.fechaEmision,
                orden.fechaEnvioProveedor,
                item.cantidad
            )
            FROM ItemOrdenCompra item
            JOIN item.ordenCompraMateriales orden
            JOIN orden.proveedor proveedor
            JOIN item.material material
            WHERE proveedor.id = :proveedorId
              AND COALESCE(orden.fechaEnvioProveedor, orden.fechaEmision) >= :start
              AND COALESCE(orden.fechaEnvioProveedor, orden.fechaEmision) <= :end
            ORDER BY COALESCE(orden.fechaEnvioProveedor, orden.fechaEmision) ASC, orden.ordenCompraId ASC, material.productoId ASC
            """)
    List<ProveedorMaterialOrdenHistRowDTO> findLeadTimeOrderHistoryByProveedor(
            @Param("proveedorId") String proveedorId,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );
}
