package exotic.app.planta.repo.producto.procesos;

import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProcesoProduccionDocumentoVersionRepo
        extends JpaRepository<ProcesoProduccionDocumentoVersion, Long> {

    List<ProcesoProduccionDocumentoVersion> findAllByProcesoProcesoIdOrderByVersionDesc(Integer procesoId);

    Optional<ProcesoProduccionDocumentoVersion> findByIdAndProcesoProcesoId(Long id, Integer procesoId);

    long countByProcesoProcesoId(Integer procesoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT documento
            FROM ProcesoProduccionDocumentoVersion documento
            WHERE documento.proceso.procesoId = :procesoId
              AND documento.estado = :estado
            """)
    Optional<ProcesoProduccionDocumentoVersion> findVigenteForUpdate(
            @Param("procesoId") Integer procesoId,
            @Param("estado") ProcesoProduccionDocumentoVersion.Estado estado
    );

    @Query("""
            SELECT COALESCE(MAX(documento.version), 0)
            FROM ProcesoProduccionDocumentoVersion documento
            WHERE documento.proceso.procesoId = :procesoId
            """)
    int findMaxVersionByProcesoId(@Param("procesoId") Integer procesoId);
}
