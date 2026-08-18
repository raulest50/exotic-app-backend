package exotic.app.planta.repo.produccion.batchrecord;

import exotic.app.planta.model.produccion.batchrecord.BatchRecordFirma;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchRecordFirmaRepo extends JpaRepository<BatchRecordFirma, Long> {

    boolean existsBySeguimientoEvento_Id(Long eventoId);

    boolean existsByOrdenFabricacionEvento_Id(Long eventoId);

    @EntityGraph(attributePaths = {"firmante", "etapa", "seguimientoEvento", "ordenFabricacionEvento", "revision", "firmaVisualVersion"})
    List<BatchRecordFirma> findByBatchRecord_IdOrderByFirmadoEnAscIdAsc(Long batchRecordId);

    @EntityGraph(attributePaths = {"firmante", "revision", "firmaVisualVersion"})
    List<BatchRecordFirma> findByRevision_IdOrderByFirmadoEnAscIdAsc(Long revisionId);
}
