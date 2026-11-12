package exotic.app.planta.repo.personal;

import exotic.app.planta.model.organizacion.personal.permiso.SolicitudPermisoPersonal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SolicitudPermisoPersonalRepo extends JpaRepository<SolicitudPermisoPersonal, Long>,
        JpaSpecificationExecutor<SolicitudPermisoPersonal> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudPermisoPersonal s where s.id = :id")
    Optional<SolicitudPermisoPersonal> findByIdForUpdate(@Param("id") Long id);
}
