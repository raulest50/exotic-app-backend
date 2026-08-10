package exotic.app.planta.repo.organigrama;

import exotic.app.planta.model.organigrama.OrganigramaEstado;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganigramaEstadoRepo extends JpaRepository<OrganigramaEstado, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT estado FROM OrganigramaEstado estado WHERE estado.id = :id")
    Optional<OrganigramaEstado> findByIdForUpdate(@Param("id") Short id);
}
