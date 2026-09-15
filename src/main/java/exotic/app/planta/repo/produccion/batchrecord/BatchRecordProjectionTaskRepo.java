package exotic.app.planta.repo.produccion.batchrecord;

import exotic.app.planta.model.produccion.batchrecord.BatchRecordProjectionTask;
import exotic.app.planta.model.produccion.batchrecord.EstadoTareaProyeccionBatchRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BatchRecordProjectionTaskRepo
        extends JpaRepository<BatchRecordProjectionTask, Long> {

    Optional<BatchRecordProjectionTask> findByOrdenProduccion_OrdenId(int ordenId);

    Optional<BatchRecordProjectionTask> findByOrdenFabricacion_OrdenFabricacionId(Long ordenId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM BatchRecordProjectionTask t WHERE t.id = :id")
    Optional<BatchRecordProjectionTask> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT t.id
            FROM BatchRecordProjectionTask t
            WHERE (t.estado IN :reintentables
                   AND t.proximoIntentoEn <= :ahora)
               OR (t.estado = :procesando AND t.actualizadoEn < :limiteAtascado)
            ORDER BY t.proximoIntentoEn ASC, t.id ASC
            """)
    List<Long> findIdsProcesables(
            @Param("reintentables") Collection<EstadoTareaProyeccionBatchRecord> reintentables,
            @Param("procesando") EstadoTareaProyeccionBatchRecord procesando,
            @Param("ahora") LocalDateTime ahora,
            @Param("limiteAtascado") LocalDateTime limiteAtascado,
            Pageable pageable);
}
