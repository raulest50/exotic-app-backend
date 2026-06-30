package exotic.app.planta.repo.empresa;

import exotic.app.planta.model.empresa.JornadaLaboralVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JornadaLaboralVersionRepo extends JpaRepository<JornadaLaboralVersion, Long> {

    @EntityGraph(attributePaths = "bloques")
    @Query("""
            SELECT DISTINCT jornada
            FROM JornadaLaboralVersion jornada
            WHERE jornada.estado = :estado
            ORDER BY jornada.version DESC
            """)
    Optional<JornadaLaboralVersion> findFirstByEstadoOrderByVersionDesc(
            @Param("estado") JornadaLaboralVersion.Estado estado
    );

    @EntityGraph(attributePaths = "bloques")
    @Query("""
            SELECT DISTINCT jornada
            FROM JornadaLaboralVersion jornada
            ORDER BY jornada.version DESC
            """)
    List<JornadaLaboralVersion> findAllByOrderByVersionDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT jornada
            FROM JornadaLaboralVersion jornada
            WHERE jornada.estado = :estado
            """)
    Optional<JornadaLaboralVersion> findByEstadoForUpdate(
            @Param("estado") JornadaLaboralVersion.Estado estado
    );

    @Query("SELECT COALESCE(MAX(jornada.version), 0) FROM JornadaLaboralVersion jornada")
    int findMaxVersion();
}
