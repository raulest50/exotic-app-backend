package exotic.app.planta.repo.produccion.batchrecord;

import exotic.app.planta.model.produccion.batchrecord.BatchRecordSeccionCorreccion;
import exotic.app.planta.model.produccion.batchrecord.EstadoSeccionCorreccionBatchRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BatchRecordSeccionCorreccionRepo
        extends JpaRepository<BatchRecordSeccionCorreccion, Long> {

    @EntityGraph(attributePaths = {"solicitadaPor", "atendidaPor"})
    List<BatchRecordSeccionCorreccion> findByBatchRecord_IdOrderByCicloRevisionNumeroAscIdAsc(
            Long batchRecordId);

    long countByBatchRecord_IdAndCicloRevisionNumeroAndEstado(
            Long batchRecordId,
            long cicloRevisionNumero,
            EstadoSeccionCorreccionBatchRecord estado
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BatchRecordSeccionCorreccion s WHERE s.id = :id")
    Optional<BatchRecordSeccionCorreccion> findByIdForUpdate(@Param("id") Long id);
}
