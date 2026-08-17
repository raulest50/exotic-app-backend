package exotic.app.planta.repo.produccion.batchrecord;

import exotic.app.planta.model.produccion.batchrecord.BatchRecordRevision;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BatchRecordRevisionRepo extends JpaRepository<BatchRecordRevision, Long> {

    @EntityGraph(attributePaths = "creadaPor")
    List<BatchRecordRevision> findByBatchRecord_IdOrderByNumeroAsc(Long batchRecordId);

    Optional<BatchRecordRevision> findByBatchRecord_IdAndNumero(Long batchRecordId, int numero);

    Optional<BatchRecordRevision> findTopByBatchRecord_IdOrderByNumeroDesc(Long batchRecordId);
}
