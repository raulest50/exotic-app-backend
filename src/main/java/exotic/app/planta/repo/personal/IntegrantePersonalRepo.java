package exotic.app.planta.repo.personal;

import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for IntegrantePersonal entity
 */
public interface IntegrantePersonalRepo extends JpaRepository<IntegrantePersonal, Long> {

    /**
     * Find integrantes by nombre containing the given text (case insensitive)
     */
    List<IntegrantePersonal> findByNombresContainingIgnoreCase(String nombres);

    /**
     * Find integrantes by apellidos containing the given text (case insensitive)
     */
    List<IntegrantePersonal> findByApellidosContainingIgnoreCase(String apellidos);

    /**
     * Find integrantes by nombre or apellidos containing the given text (case insensitive) with pagination
     */
    @Query("SELECT i FROM IntegrantePersonal i WHERE LOWER(i.nombres) LIKE LOWER(CONCAT('%', :searchText, '%')) OR LOWER(i.apellidos) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    Page<IntegrantePersonal> findByNombresOrApellidosContainingIgnoreCase(@Param("searchText") String searchText, Pageable pageable);

    /**
     * Find integrantes by departamento
     */
    List<IntegrantePersonal> findByDepartamento(IntegrantePersonal.Departamento departamento);

    /**
     * Find integrantes by estado
     */
    List<IntegrantePersonal> findByEstado(IntegrantePersonal.Estado estado);
}