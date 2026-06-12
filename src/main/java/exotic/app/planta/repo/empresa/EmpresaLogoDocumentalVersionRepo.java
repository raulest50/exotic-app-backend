package exotic.app.planta.repo.empresa;

import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmpresaLogoDocumentalVersionRepo extends JpaRepository<EmpresaLogoDocumentalVersion, Long> {

    Optional<EmpresaLogoDocumentalVersion> findFirstByEstadoOrderByVersionDesc(EmpresaLogoDocumentalVersion.Estado estado);

    List<EmpresaLogoDocumentalVersion> findAllByOrderByVersionDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT logo
            FROM EmpresaLogoDocumentalVersion logo
            WHERE logo.estado = :estado
            """)
    Optional<EmpresaLogoDocumentalVersion> findByEstadoForUpdate(
            @Param("estado") EmpresaLogoDocumentalVersion.Estado estado
    );

    @Query("SELECT COALESCE(MAX(logo.version), 0) FROM EmpresaLogoDocumentalVersion logo")
    int findMaxVersion();
}
