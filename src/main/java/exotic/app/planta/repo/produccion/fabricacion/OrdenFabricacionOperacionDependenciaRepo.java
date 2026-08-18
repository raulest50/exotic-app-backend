package exotic.app.planta.repo.produccion.fabricacion;

import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacionDependencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrdenFabricacionOperacionDependenciaRepo
        extends JpaRepository<OrdenFabricacionOperacionDependencia, Long> {

    @Query("""
            SELECT COUNT(d) FROM OrdenFabricacionOperacionDependencia d
            WHERE d.sucesora.id = :operacionId
              AND d.predecesora.estado NOT IN (2, 3)
            """)
    long countPredecesorasPendientes(@Param("operacionId") Long operacionId);

    @Query("""
            SELECT d FROM OrdenFabricacionOperacionDependencia d
            JOIN FETCH d.sucesora s
            WHERE d.predecesora.id = :operacionId
            """)
    List<OrdenFabricacionOperacionDependencia> findByPredecesoraId(
            @Param("operacionId") Long operacionId);

    List<OrdenFabricacionOperacionDependencia> findBySucesora_Id(Long operacionId);

    boolean existsByPredecesora_IdAndSucesora_Id(Long predecesoraId, Long sucesoraId);
}
