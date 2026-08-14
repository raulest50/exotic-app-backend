package exotic.app.planta.repo.usuarios;

import exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion;
import exotic.app.planta.model.users.firma.dto.FirmaVisualUsuarioMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FirmaVisualUsuarioVersionRepo extends JpaRepository<FirmaVisualUsuarioVersion, Long> {

    Optional<FirmaVisualUsuarioVersion> findFirstByTitularIdAndEstadoOrderByVersionDesc(
            Long usuarioId,
            FirmaVisualUsuarioVersion.Estado estado
    );

    Optional<FirmaVisualUsuarioVersion> findByIdAndTitularId(Long id, Long usuarioId);

    @Query("""
            SELECT
                firma.id AS id,
                firma.titular.id AS usuarioId,
                firma.version AS version,
                firma.estado AS estado,
                firma.nombreArchivoOriginal AS nombreArchivoOriginal,
                firma.contentType AS contentType,
                firma.tamanoBytes AS tamanoBytes,
                firma.anchoPx AS anchoPx,
                firma.altoPx AS altoPx,
                firma.sha256 AS sha256,
                firma.vigenteDesde AS vigenteDesde,
                firma.vigenteHasta AS vigenteHasta,
                firma.creadoEn AS creadoEn,
                firma.configuradaPorUsername AS configuradaPorUsername,
                firma.configuradaPorNombre AS configuradaPorNombre,
                firma.motivoCambio AS motivoCambio,
                firma.retiradaPorUsername AS retiradaPorUsername,
                firma.retiradaPorNombre AS retiradaPorNombre,
                firma.motivoRetiro AS motivoRetiro
            FROM FirmaVisualUsuarioVersion firma
            WHERE firma.titular.id = :usuarioId
              AND firma.estado = :estado
            ORDER BY firma.version DESC
            """)
    List<FirmaVisualUsuarioMetadata> findMetadataByTitularIdAndEstadoOrderByVersionDesc(
            @Param("usuarioId") Long usuarioId,
            @Param("estado") FirmaVisualUsuarioVersion.Estado estado
    );

    @Query("""
            SELECT
                firma.id AS id,
                firma.titular.id AS usuarioId,
                firma.version AS version,
                firma.estado AS estado,
                firma.nombreArchivoOriginal AS nombreArchivoOriginal,
                firma.contentType AS contentType,
                firma.tamanoBytes AS tamanoBytes,
                firma.anchoPx AS anchoPx,
                firma.altoPx AS altoPx,
                firma.sha256 AS sha256,
                firma.vigenteDesde AS vigenteDesde,
                firma.vigenteHasta AS vigenteHasta,
                firma.creadoEn AS creadoEn,
                firma.configuradaPorUsername AS configuradaPorUsername,
                firma.configuradaPorNombre AS configuradaPorNombre,
                firma.motivoCambio AS motivoCambio,
                firma.retiradaPorUsername AS retiradaPorUsername,
                firma.retiradaPorNombre AS retiradaPorNombre,
                firma.motivoRetiro AS motivoRetiro
            FROM FirmaVisualUsuarioVersion firma
            WHERE firma.titular.id = :usuarioId
            ORDER BY firma.version DESC
            """)
    List<FirmaVisualUsuarioMetadata> findAllMetadataByTitularIdOrderByVersionDesc(
            @Param("usuarioId") Long usuarioId
    );

    @Query("""
            SELECT COALESCE(MAX(firma.version), 0)
            FROM FirmaVisualUsuarioVersion firma
            WHERE firma.titular.id = :usuarioId
            """)
    int findMaxVersionByTitularId(@Param("usuarioId") Long usuarioId);
}
