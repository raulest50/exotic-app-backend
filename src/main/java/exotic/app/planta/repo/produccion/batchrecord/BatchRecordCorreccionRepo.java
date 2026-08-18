package exotic.app.planta.repo.produccion.batchrecord;

import exotic.app.planta.model.produccion.batchrecord.BatchRecordCorreccion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchRecordCorreccionRepo extends JpaRepository<BatchRecordCorreccion, Long> {

    boolean existsByEventoCorreccion_Id(Long eventoId);

    boolean existsByOrdenFabricacionEventoCorreccion_Id(Long eventoId);

    @EntityGraph(attributePaths = {"etapa", "corregidaPor", "eventoCorreccion", "eventoRevertido", "ordenFabricacionEventoCorreccion", "ordenFabricacionEventoRevertido", "revision", "firma"})
    List<BatchRecordCorreccion> findByBatchRecord_IdOrderByCorregidaEnAscIdAsc(Long batchRecordId);
}
