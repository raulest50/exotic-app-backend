package exotic.app.planta.repo.produccion.batchrecord;

import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface BatchRecordRepo extends JpaRepository<BatchRecord, Long> {

    Optional<BatchRecord> findByOrdenProduccion_OrdenId(int ordenId);

    Optional<BatchRecord> findByOrdenFabricacion_OrdenFabricacionId(Long ordenFabricacionId);

    Optional<BatchRecord> findByLoteResultado_Id(Long loteId);

    Optional<BatchRecord> findByLoteResultado_BatchNumberIgnoreCase(String lote);

    long countByEstadoNotIn(Collection<EstadoBatchRecord> estadosTerminales);

    @EntityGraph(attributePaths = {
            "ordenProduccion", "ordenFabricacion", "loteResultado",
            "productoResultado", "manufacturingVersion", "creadoPor"
    })
    @Query("""
            SELECT b
            FROM BatchRecord b
            LEFT JOIN b.ordenProduccion op
            WHERE (:ordenProduccionId IS NULL
                   OR op.ordenId = :ordenProduccionId)
              AND (:lote IS NULL OR :lote = ''
                   OR LOWER(b.loteResultado.batchNumber)
                        LIKE LOWER(CONCAT('%', :lote, '%'))
                   OR EXISTS (
                       SELECT c.id FROM BatchRecordConsumo c
                       WHERE c.batchRecord = b
                         AND c.loteOrigen IS NOT NULL
                         AND LOWER(c.loteOrigen.batchNumber)
                             LIKE LOWER(CONCAT('%', :lote, '%'))
                   ))
            """)
    Page<BatchRecord> buscar(
            @Param("ordenProduccionId") Integer ordenProduccionId,
            @Param("lote") String lote,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "ordenProduccion", "ordenFabricacion", "loteResultado",
            "productoResultado", "manufacturingVersion", "creadoPor"
    })
    @Query("""
            SELECT b
            FROM BatchRecord b
            LEFT JOIN b.ordenProduccion op
            LEFT JOIN b.ordenFabricacion ofab
            WHERE b.estado IN :estados
              AND (:soloEnviados = FALSE OR b.enviadoRevisionEn IS NOT NULL)
              AND (:search IS NULL OR :search = ''
                   OR LOWER(b.codigo) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(b.loteResultado.batchNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(b.productoResultado.productoId) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(b.productoResultado.nombre) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR EXISTS (
                       SELECT c.id FROM BatchRecordConsumo c
                       WHERE c.batchRecord = b
                         AND c.loteOrigen IS NOT NULL
                         AND LOWER(c.loteOrigen.batchNumber)
                             LIKE LOWER(CONCAT('%', :search, '%'))
                   )
                   OR (:ordenProduccionId IS NOT NULL
                       AND op.ordenId = :ordenProduccionId)
                   OR (:ordenFabricacionId IS NOT NULL
                       AND ofab.ordenFabricacionId = :ordenFabricacionId))
            """)
    Page<BatchRecord> buscarPorEstados(
            @Param("estados") Collection<EstadoBatchRecord> estados,
            @Param("search") String search,
            @Param("ordenProduccionId") Integer ordenProduccionId,
            @Param("ordenFabricacionId") Long ordenFabricacionId,
            @Param("soloEnviados") boolean soloEnviados,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BatchRecord b WHERE b.id = :id")
    Optional<BatchRecord> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BatchRecord b WHERE b.loteResultado.id = :loteId")
    Optional<BatchRecord> findByLoteResultadoIdForUpdate(@Param("loteId") Long loteId);
}
