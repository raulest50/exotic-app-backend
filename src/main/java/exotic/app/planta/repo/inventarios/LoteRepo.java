package exotic.app.planta.repo.inventarios;

import exotic.app.planta.model.inventarios.Lote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio para la entidad Lote.
 * Proporciona operaciones CRUD básicas para la entidad Lote.
 */
public interface LoteRepo extends JpaRepository<Lote, Long> {

    /**
     * Busca un lote por su número de batch.
     *
     * @param batchNumber El número de batch a buscar
     * @return El lote encontrado, o null si no existe
     */
    Lote findByBatchNumber(String batchNumber);

    /**
     * Busca todos los lotes asociados a una orden de compra por su ID.
     *
     * @param ordenCompraId ID de la orden de compra
     * @return Lista de lotes que referencian esa orden
     */
    List<Lote> findByOrdenCompraMateriales_OrdenCompraId(int ordenCompraId);
}