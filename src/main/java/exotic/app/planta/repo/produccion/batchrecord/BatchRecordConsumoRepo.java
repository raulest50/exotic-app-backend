package exotic.app.planta.repo.produccion.batchrecord;

import exotic.app.planta.model.produccion.batchrecord.BatchRecordConsumo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchRecordConsumoRepo extends JpaRepository<BatchRecordConsumo, Long> {

    boolean existsByMovimiento_MovimientoId(int movimientoId);

    @EntityGraph(attributePaths = {"producto", "loteOrigen", "movimiento", "registradoPor"})
    List<BatchRecordConsumo> findByBatchRecord_IdOrderByRegistradoEnAscIdAsc(Long batchRecordId);
}
