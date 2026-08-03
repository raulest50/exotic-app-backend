package exotic.app.planta.repo.producto.procesos;

import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface ProcesoProduccionRepo extends JpaRepository<ProcesoProduccion, Integer>, JpaSpecificationExecutor<ProcesoProduccion> {
    Optional<ProcesoProduccion> findByNombre(String nombre);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT proceso FROM ProcesoProduccion proceso WHERE proceso.procesoId = :id")
    Optional<ProcesoProduccion> findByIdForUpdate(@Param("id") Integer id);
}
