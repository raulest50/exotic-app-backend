package exotic.app.planta.repo.produccion.batchrecord;

import exotic.app.planta.model.produccion.batchrecord.CicloRevisionBatchRecord;
import exotic.app.planta.model.produccion.batchrecord.EstadoCicloRevisionBatchRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CicloRevisionBatchRecordRepo extends JpaRepository<CicloRevisionBatchRecord, Long> {

    @EntityGraph(attributePaths = {"enviadoPor", "cerradoPor", "revisionEnvio"})
    List<CicloRevisionBatchRecord> findByBatchRecord_IdOrderByNumeroAsc(Long batchRecordId);

    Optional<CicloRevisionBatchRecord> findTopByBatchRecord_IdOrderByNumeroDesc(Long batchRecordId);

    Optional<CicloRevisionBatchRecord> findByBatchRecord_IdAndNumero(Long batchRecordId, long numero);

    Optional<CicloRevisionBatchRecord> findByBatchRecord_IdAndEstado(
            Long batchRecordId,
            EstadoCicloRevisionBatchRecord estado
    );
}
