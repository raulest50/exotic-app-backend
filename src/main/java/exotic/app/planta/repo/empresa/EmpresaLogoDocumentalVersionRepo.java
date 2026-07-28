package exotic.app.planta.repo.empresa;

import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.model.empresa.dto.EmpresaLogoDocumentalMetadata;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmpresaLogoDocumentalVersionRepo extends JpaRepository<EmpresaLogoDocumentalVersion, Long> {

    Optional<EmpresaLogoDocumentalVersion> findFirstByEstadoOrderByVersionDesc(EmpresaLogoDocumentalVersion.Estado estado);

    @Query("""
            SELECT
                logo.id AS id,
                logo.version AS version,
                logo.estado AS estado,
                logo.nombreArchivoOriginal AS nombreArchivoOriginal,
                logo.contentType AS contentType,
                logo.tamanoBytes AS tamanoBytes,
                logo.anchoPx AS anchoPx,
                logo.altoPx AS altoPx,
                logo.sha256 AS sha256,
                logo.vigenteDesde AS vigenteDesde,
                logo.vigenteHasta AS vigenteHasta,
                logo.creadoEn AS creadoEn,
                logo.creadoPor AS creadoPor,
                logo.motivoCambio AS motivoCambio
            FROM EmpresaLogoDocumentalVersion logo
            WHERE logo.estado = :estado
            ORDER BY logo.version DESC
            """)
    List<EmpresaLogoDocumentalMetadata> findMetadataByEstadoOrderByVersionDesc(
            @Param("estado") EmpresaLogoDocumentalVersion.Estado estado
    );

    @Query("""
            SELECT
                logo.id AS id,
                logo.version AS version,
                logo.estado AS estado,
                logo.nombreArchivoOriginal AS nombreArchivoOriginal,
                logo.contentType AS contentType,
                logo.tamanoBytes AS tamanoBytes,
                logo.anchoPx AS anchoPx,
                logo.altoPx AS altoPx,
                logo.sha256 AS sha256,
                logo.vigenteDesde AS vigenteDesde,
                logo.vigenteHasta AS vigenteHasta,
                logo.creadoEn AS creadoEn,
                logo.creadoPor AS creadoPor,
                logo.motivoCambio AS motivoCambio
            FROM EmpresaLogoDocumentalVersion logo
            ORDER BY logo.version DESC
            """)
    List<EmpresaLogoDocumentalMetadata> findAllMetadataByOrderByVersionDesc();

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
