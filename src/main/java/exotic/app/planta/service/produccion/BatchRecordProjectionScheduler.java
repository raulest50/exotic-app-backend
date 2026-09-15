package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.batchrecord.EstadoTareaProyeccionBatchRecord;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordProjectionTaskRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchRecordProjectionScheduler {

    private final BatchRecordProjectionTaskRepo taskRepo;
    private final BatchRecordProjectionProcessor processor;
    private final Clock applicationClock;

    @Scheduled(fixedDelayString = "${app.batch-record.projection-check-ms:5000}")
    public void reconciliar() {
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        List<Long> ids = taskRepo.findIdsProcesables(
                List.of(
                        EstadoTareaProyeccionBatchRecord.PENDIENTE,
                        EstadoTareaProyeccionBatchRecord.ERROR),
                EstadoTareaProyeccionBatchRecord.PROCESANDO,
                ahora,
                ahora.minusMinutes(10),
                PageRequest.of(0, 25));
        for (Long id : ids) {
            try {
                processor.procesar(id);
            } catch (RuntimeException error) {
                log.error("Fallo inesperado al reconciliar la tarea documental {}", id, error);
            }
        }
    }
}
