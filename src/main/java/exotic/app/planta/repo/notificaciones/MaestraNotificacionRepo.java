package exotic.app.planta.repo.notificaciones;

import exotic.app.planta.model.notificaciones.MaestraNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MaestraNotificacionRepo extends JpaRepository<MaestraNotificacion, Integer> {

    @Query("SELECT DISTINCT n FROM MaestraNotificacion n LEFT JOIN FETCH n.usersGroup WHERE n.id = :id")
    Optional<MaestraNotificacion> findByIdWithUsersGroup(@Param("id") int id);
}
