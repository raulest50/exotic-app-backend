package exotic.app.planta.repo.organigrama;

import exotic.app.planta.model.organigrama.MisionVisionVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MisionVisionVersionRepo extends JpaRepository<MisionVisionVersion, Long> {

    Optional<MisionVisionVersion> findFirstByEstadoOrderByVersionDesc(MisionVisionVersion.Estado estado);

    List<MisionVisionVersion> findAllByOrderByVersionDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT version
            FROM MisionVisionVersion version
            WHERE version.estado = :estado
            """)
    Optional<MisionVisionVersion> findByEstadoForUpdate(
            @Param("estado") MisionVisionVersion.Estado estado
    );

    @Query("SELECT COALESCE(MAX(version.version), 0) FROM MisionVisionVersion version")
    int findMaxVersion();
}
