package exotic.app.planta.repo.compras;

import exotic.app.planta.model.bi.dto.ProveedorMaterialOrdenHistRowDTO;
import exotic.app.planta.model.compras.ItemOrdenCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ItemOrdenCompraRepo extends JpaRepository<ItemOrdenCompra, Integer> {
    @Query("""
            SELECT item FROM ItemOrdenCompra item
            JOIN FETCH item.ordenCompraMateriales orden
            JOIN FETCH orden.proveedor
            JOIN FETCH item.material
            WHERE orden.estado = :estado
            ORDER BY orden.fechaEmision DESC, orden.ordenCompraId DESC, item.itemOrdenId
            """)
    List<ItemOrdenCompra> findAllByOrdenEstadoForBi(@Param("estado") int estado);
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
