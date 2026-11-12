package exotic.app.planta.repo.personal;

import exotic.app.planta.model.organizacion.personal.permiso.FirmaPermisoPersonal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FirmaPermisoPersonalRepo extends JpaRepository<FirmaPermisoPersonal, Long> {
    List<FirmaPermisoPersonal> findBySolicitudIdOrderByIdAsc(Long solicitudId);
}
