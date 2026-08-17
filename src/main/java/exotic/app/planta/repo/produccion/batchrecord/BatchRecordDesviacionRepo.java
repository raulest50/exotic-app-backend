package exotic.app.planta.repo.produccion.batchrecord;

import exotic.app.planta.model.produccion.batchrecord.BatchRecordDesviacion;
import exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecordDesviacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BatchRecordDesviacionRepo extends JpaRepository<BatchRecordDesviacion, Long> {

    long countByBatchRecord_IdAndEstadoIn(Long batchRecordId, Collection<EstadoBatchRecordDesviacion> estados);

    @EntityGraph(attributePaths = {"etapa", "detectadaPor", "resueltaPor"})
    List<BatchRecordDesviacion> findByBatchRecord_IdOrderByDetectadaEnAscIdAsc(Long batchRecordId);
}
