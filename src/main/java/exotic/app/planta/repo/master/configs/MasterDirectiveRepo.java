package exotic.app.planta.repo.master.configs;

import exotic.app.planta.model.master.configs.MasterDirective;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MasterDirectiveRepo extends JpaRepository<MasterDirective, Long> {
    // Consultas personalizadas si son necesarias
    Optional<MasterDirective> findByNombre(String nombre);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT directive FROM MasterDirective directive WHERE directive.nombre = :nombre")
    Optional<MasterDirective> findByNombreForShare(@Param("nombre") String nombre);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT directive FROM MasterDirective directive WHERE directive.id = :id")
    Optional<MasterDirective> findByIdForUpdate(@Param("id") Long id);
}
