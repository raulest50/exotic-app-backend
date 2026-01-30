package exotic.app.planta.repo.compras;

import exotic.app.planta.model.compras.ItemOrdenCompra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemOrdenCompraRepo extends JpaRepository<ItemOrdenCompra, Integer> {
    boolean existsByMaterial_ProductoId(String productoId);

    /**
     * Busca todos los ítems de orden de compra asociados a una orden por su ID.
     *
     * @param ordenCompraId ID de la orden de compra
     * @return Lista de ítems de esa orden
     */
    List<ItemOrdenCompra> findByOrdenCompraMateriales_OrdenCompraId(int ordenCompraId);
}

