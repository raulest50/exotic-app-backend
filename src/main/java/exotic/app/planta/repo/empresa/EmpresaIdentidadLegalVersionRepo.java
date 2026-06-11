package exotic.app.planta.repo.empresa;

import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmpresaIdentidadLegalVersionRepo extends JpaRepository<EmpresaIdentidadLegalVersion, Long> {

    Optional<EmpresaIdentidadLegalVersion> findFirstByEstadoOrderByVersionDesc(EmpresaIdentidadLegalVersion.Estado estado);

    List<EmpresaIdentidadLegalVersion> findAllByOrderByVersionDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT identidad
            FROM EmpresaIdentidadLegalVersion identidad
            WHERE identidad.estado = :estado
            """)
    Optional<EmpresaIdentidadLegalVersion> findByEstadoForUpdate(
            @Param("estado") EmpresaIdentidadLegalVersion.Estado estado
    );

    @Query("SELECT COALESCE(MAX(identidad.version), 0) FROM EmpresaIdentidadLegalVersion identidad")
    int findMaxVersion();
}
