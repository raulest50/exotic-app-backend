package exotic.app.planta.repo.produccion.ruprocatdesigner;

import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RutaProcesoCatVersionRepo extends JpaRepository<RutaProcesoCatVersion, Long> {

    @Query("""
            SELECT DISTINCT version
            FROM RutaProcesoCatVersion version
            WHERE version.rutaProcesoCat.categoria.categoriaId = :categoriaId
              AND version.estado = :estado
            """)
    Optional<RutaProcesoCatVersion> findByCategoriaIdAndEstado(
            @Param("categoriaId") int categoriaId,
            @Param("estado") RutaProcesoCatVersion.Estado estado
    );

    @Query("""
            SELECT DISTINCT version
            FROM RutaProcesoCatVersion version
            WHERE version.rutaProcesoCat.categoria.categoriaId = :categoriaId
            ORDER BY version.versionNumber DESC
            """)
    List<RutaProcesoCatVersion> findAllByCategoriaIdOrderByVersionDesc(@Param("categoriaId") int categoriaId);

    @Query("""
            SELECT DISTINCT version
            FROM RutaProcesoCatVersion version
            WHERE version.id = :versionId
              AND version.rutaProcesoCat.categoria.categoriaId = :categoriaId
            """)
    Optional<RutaProcesoCatVersion> findByCategoriaIdAndVersionId(
            @Param("categoriaId") int categoriaId,
            @Param("versionId") Long versionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT version
            FROM RutaProcesoCatVersion version
            WHERE version.rutaProcesoCat.categoria.categoriaId = :categoriaId
              AND version.estado = :estado
            """)
    Optional<RutaProcesoCatVersion> findByCategoriaIdAndEstadoForUpdate(
            @Param("categoriaId") int categoriaId,
            @Param("estado") RutaProcesoCatVersion.Estado estado
    );

    @Query("""
            SELECT COALESCE(MAX(version.versionNumber), 0)
            FROM RutaProcesoCatVersion version
            WHERE version.rutaProcesoCat.categoria.categoriaId = :categoriaId
            """)
    int findMaxVersionNumberByCategoriaId(@Param("categoriaId") int categoriaId);

    boolean existsByRutaProcesoCat_Categoria_CategoriaIdAndEstado(
            int categoriaId,
            RutaProcesoCatVersion.Estado estado
    );
}
