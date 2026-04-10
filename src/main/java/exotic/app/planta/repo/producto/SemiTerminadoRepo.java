package exotic.app.planta.repo.producto;

import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SemiTerminadoRepo extends JpaRepository<SemiTerminado, String>, JpaSpecificationExecutor<SemiTerminado> {

    @EntityGraph(attributePaths = {"insumos", "insumos.producto"})
    List<SemiTerminado> findByInsumos_Producto(Producto producto);

    @Query(value = """
            SELECT * FROM productos p
            WHERE p.tipo_producto = 'S'
              AND similarity(p.nombre, :nombre) >= :threshold
            ORDER BY similarity(p.nombre, :nombre) DESC
            """,
            countQuery = """
            SELECT count(*) FROM productos p
            WHERE p.tipo_producto = 'S'
              AND similarity(p.nombre, :nombre) >= :threshold
            """,
            nativeQuery = true)
    Page<SemiTerminado> searchByNombreFuzzy(@Param("nombre") String nombre,
                                            @Param("threshold") double threshold,
                                            Pageable pageable);

}
