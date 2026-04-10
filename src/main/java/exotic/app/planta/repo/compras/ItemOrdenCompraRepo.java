package exotic.app.planta.repo.compras;

import exotic.app.planta.model.compras.ItemOrdenCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ItemOrdenCompraRepo extends JpaRepository<ItemOrdenCompra, Integer> {
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
}
