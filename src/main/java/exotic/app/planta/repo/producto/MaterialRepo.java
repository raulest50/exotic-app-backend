package exotic.app.planta.repo.producto;

import exotic.app.planta.model.producto.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialRepo extends JpaRepository<Material, String>, JpaSpecificationExecutor<Material> {

    List<Material> findByInventareableTrue();

    @Query(value = """
            SELECT * FROM productos p
            WHERE p.tipo_producto = 'M'
              AND similarity(p.nombre, :nombre) >= :threshold
            ORDER BY similarity(p.nombre, :nombre) DESC
            """,
            countQuery = """
            SELECT count(*) FROM productos p
            WHERE p.tipo_producto = 'M'
              AND similarity(p.nombre, :nombre) >= :threshold
            """,
            nativeQuery = true)
    Page<Material> searchByNombreFuzzy(@Param("nombre") String nombre,
                                       @Param("threshold") double threshold,
                                       Pageable pageable);
}
