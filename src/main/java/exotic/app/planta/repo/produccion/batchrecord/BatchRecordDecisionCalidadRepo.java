package exotic.app.planta.repo.produccion.batchrecord;

import exotic.app.planta.model.produccion.batchrecord.BatchRecordDecisionCalidad;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchRecordDecisionCalidadRepo extends JpaRepository<BatchRecordDecisionCalidad, Long> {

    @EntityGraph(attributePaths = {"decididaPor", "revision", "firma", "cicloRevision"})
    List<BatchRecordDecisionCalidad> findByBatchRecord_IdOrderByDecididaEnAscIdAsc(Long batchRecordId);
}
