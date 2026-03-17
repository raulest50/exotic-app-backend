package exotic.app.planta.repo.producto.procesos;

import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionCompleto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcesoProduccionCompletoRepo extends JpaRepository<ProcesoProduccionCompleto, Integer>, JpaSpecificationExecutor<ProcesoProduccionCompleto> {

    @Query("""
            SELECT DISTINCT ppc
            FROM ProcesoProduccionCompleto ppc
            LEFT JOIN FETCH ppc.procesosProduccion ppn
            WHERE ppc.producto.productoId = :productoId
            """)
    List<ProcesoProduccionCompleto> findByProducto_ProductoIdWithNodes(@Param("productoId") String productoId);
}