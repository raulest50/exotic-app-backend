package exotic.app.planta.repo.personal;

import exotic.app.planta.model.organizacion.personal.permiso.EventoPermisoPersonal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventoPermisoPersonalRepo extends JpaRepository<EventoPermisoPersonal, Long> {
    List<EventoPermisoPersonal> findBySolicitudIdOrderByIdAsc(Long solicitudId);
}
