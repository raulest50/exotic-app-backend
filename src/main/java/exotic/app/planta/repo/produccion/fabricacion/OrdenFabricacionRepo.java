package exotic.app.planta.repo.produccion.fabricacion;

import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenFabricacionRepo extends JpaRepository<OrdenFabricacion, Long> {

    @EntityGraph(attributePaths = {"semiTerminado", "manufacturingVersion", "creadaPor", "responsable"})
    @Query("""
            SELECT o
            FROM OrdenFabricacion o
            WHERE (:search IS NULL OR :search = ''
                   OR LOWER(o.semiTerminado.productoId) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(o.semiTerminado.nombre) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR CAST(o.ordenFabricacionId AS string) LIKE CONCAT('%', :search, '%'))
            """)
    Page<OrdenFabricacion> buscar(@Param("search") String search, Pageable pageable);
}
