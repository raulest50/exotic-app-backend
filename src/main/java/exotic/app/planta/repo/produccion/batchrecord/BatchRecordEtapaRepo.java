package exotic.app.planta.repo.produccion.batchrecord;

import exotic.app.planta.model.produccion.batchrecord.BatchRecordEtapa;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BatchRecordEtapaRepo extends JpaRepository<BatchRecordEtapa, Long> {

    @EntityGraph(attributePaths = {
            "areaOperativa", "controlProcesoPlantilla", "seguimientoOrdenArea",
            "seguimientoEventoOrigen", "reportadaPor"
    })
    List<BatchRecordEtapa> findByBatchRecord_IdOrderBySecuenciaAscIdAsc(Long batchRecordId);

    Optional<BatchRecordEtapa> findBySeguimientoOrdenArea_Id(Long seguimientoId);

    Optional<BatchRecordEtapa> findByIdAndBatchRecord_Id(Long id, Long batchRecordId);
}
