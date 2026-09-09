package exotic.app.planta.repo.producto.fichatecnica;

import exotic.app.planta.model.producto.fichatecnica.MaterialFichaTecnicaVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaterialFichaTecnicaVersionRepo
        extends JpaRepository<MaterialFichaTecnicaVersion, Long> {

    List<MaterialFichaTecnicaVersion> findAllByMaterialProductoIdOrderByVersionDesc(String productoId);

    Optional<MaterialFichaTecnicaVersion> findByIdAndMaterialProductoId(Long id, String productoId);

    Optional<MaterialFichaTecnicaVersion> findByMaterialProductoIdAndEstado(
            String productoId,
            MaterialFichaTecnicaVersion.Estado estado
    );

    @Query("""
            SELECT COALESCE(MAX(version.version), 0)
            FROM MaterialFichaTecnicaVersion version
            WHERE version.material.productoId = :productoId
            """)
    int findMaxVersionByMaterialId(@Param("productoId") String productoId);

    long countByMaterialProductoId(String productoId);

    @Query("""
            SELECT version.storageKey
            FROM MaterialFichaTecnicaVersion version
            WHERE version.material.productoId = :productoId
            """)
    List<String> findStorageKeysByMaterialId(@Param("productoId") String productoId);
}
