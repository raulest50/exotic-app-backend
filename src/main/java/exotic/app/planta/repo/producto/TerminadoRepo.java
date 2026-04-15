package exotic.app.planta.repo.producto;

import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;


// removed extend : , JpaSpecificationExecutor<Terminado>

@Repository
public interface TerminadoRepo extends JpaRepository<Terminado, String>, JpaSpecificationExecutor<Terminado> {

    @EntityGraph(attributePaths = {"categoria", "insumos", "insumos.producto"})
    List<Terminado> findAllByOrderByProductoIdAsc();

    @EntityGraph(attributePaths = {"insumos", "insumos.producto"})
    List<Terminado> findByInsumos_Producto(Producto producto);

    @EntityGraph(attributePaths = {"categoria"})
    List<Terminado> findByProductoIdIn(Collection<String> productoIds);

    List<Terminado> findDistinctByCasePack_InsumosEmpaque_Material_ProductoId(String productoId);

    Optional<Terminado> findByPrefijoLote(String prefijoLote);

    @Query("SELECT t.productoId FROM Terminado t ORDER BY t.productoId ASC")
    List<String> findAllProductoIdsOrderByProductoIdAsc();

    @Modifying
    @Query("UPDATE Terminado t SET t.procesoProduccionCompleto = null WHERE t.productoId = :productoId")
    void clearProcesoProduccionCompletoByProductoId(@Param("productoId") String productoId);

}
