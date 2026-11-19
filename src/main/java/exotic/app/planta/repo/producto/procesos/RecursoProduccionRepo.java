package exotic.app.planta.repo.producto.procesos;

import exotic.app.planta.model.producto.manufacturing.procesos.RecursoProduccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Repository
public interface RecursoProduccionRepo extends JpaRepository<RecursoProduccion, Long>, JpaSpecificationExecutor<RecursoProduccion> {
    interface CatalogoAreaProjection {
        Long getId();
        String getNombre();
        String getDescripcion();
    }

    @Query("""
            SELECT r.id AS id, r.nombre AS nombre, r.descripcion AS descripcion
            FROM RecursoProduccion r
            WHERE LOWER(r.nombre) LIKE LOWER(CONCAT('%', :search, '%'))
               OR STR(r.id) LIKE CONCAT('%', :search, '%')
            """)
    Page<CatalogoAreaProjection> buscarCatalogoArea(@Param("search") String search, Pageable pageable);

    Optional<RecursoProduccion> findByNombre(String nombre);
}
