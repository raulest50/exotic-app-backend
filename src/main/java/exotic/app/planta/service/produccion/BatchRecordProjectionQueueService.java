package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.batchrecord.*;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordProjectionTaskRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

/** Registra intención documental sin construir el expediente en la transacción operativa. */
@Service
@RequiredArgsConstructor
public class BatchRecordProjectionQueueService {

    private final BatchRecordProjectionTaskRepo taskRepo;
    private final Clock applicationClock;

    @Transactional
    public void solicitarCreacion(OrdenProduccion orden, Lote lote, User actor) {
        BatchRecordProjectionTask task = taskRepo
                .findByOrdenProduccion_OrdenId(orden.getOrdenId())
                .orElseGet(BatchRecordProjectionTask::new);
        task.setOrdenProduccion(orden);
        task.setOrdenFabricacion(null);
        preparar(task, lote, actor, ObjetivoProyeccionBatchRecord.ACTIVO);
    }

    @Transactional
    public void solicitarCreacion(OrdenFabricacion orden, Lote lote, User actor) {
        BatchRecordProjectionTask task = taskRepo
                .findByOrdenFabricacion_OrdenFabricacionId(orden.getOrdenFabricacionId())
                .orElseGet(BatchRecordProjectionTask::new);
        task.setOrdenProduccion(null);
        task.setOrdenFabricacion(orden);
        preparar(task, lote, actor, ObjetivoProyeccionBatchRecord.ACTIVO);
    }

    @Transactional
    public void solicitarActualizacion(OrdenProduccion orden) {
        if (orden == null) return;
        taskRepo.findByOrdenProduccion_OrdenId(orden.getOrdenId()).ifPresent(this::reactivar);
    }

    @Transactional
    public void solicitarActualizacion(OrdenFabricacion orden) {
        if (orden == null || orden.getOrdenFabricacionId() == null) return;
        taskRepo.findByOrdenFabricacion_OrdenFabricacionId(
                orden.getOrdenFabricacionId()).ifPresent(this::reactivar);
    }

    @Transactional
    public void solicitarPreparacion(
            OrdenProduccion orden,
            BigDecimal cantidad,
            User actor
    ) {
        if (orden == null) return;
        taskRepo.findByOrdenProduccion_OrdenId(orden.getOrdenId()).ifPresent(task -> {
            task.setCantidadObtenida(cantidad);
            task.setLimpiarCantidadObtenida(false);
            if (actor != null) task.setSolicitadoPor(actor);
            reactivar(task);
        });
    }

    @Transactional
    public void solicitarPreparacion(
            OrdenFabricacion orden,
            BigDecimal cantidad,
            User actor
    ) {
        if (orden == null || orden.getOrdenFabricacionId() == null) return;
        taskRepo.findByOrdenFabricacion_OrdenFabricacionId(
                orden.getOrdenFabricacionId()).ifPresent(task -> {
            task.setCantidadObtenida(cantidad);
            task.setLimpiarCantidadObtenida(false);
            if (actor != null) task.setSolicitadoPor(actor);
            reactivar(task);
        });
    }

    @Transactional
    public void solicitarReversionPreparacion(OrdenProduccion orden, User actor) {
        if (orden == null) return;
        taskRepo.findByOrdenProduccion_OrdenId(orden.getOrdenId()).ifPresent(task -> {
            task.setCantidadObtenida(null);
            task.setLimpiarCantidadObtenida(true);
            if (actor != null) task.setSolicitadoPor(actor);
            reactivar(task);
        });
    }

    @Transactional
    public void solicitarCierre(
            OrdenProduccion orden,
            BigDecimal cantidad,
            User actor
    ) {
        if (orden == null) return;
        taskRepo.findByOrdenProduccion_OrdenId(orden.getOrdenId()).ifPresent(task -> {
            task.setObjetivo(ObjetivoProyeccionBatchRecord.CERRADO);
            task.setCantidadObtenida(cantidad);
            task.setLimpiarCantidadObtenida(false);
            if (actor != null) task.setSolicitadoPor(actor);
            reactivar(task);
        });
    }

    @Transactional
    public void solicitarCierre(
            OrdenFabricacion orden,
            BigDecimal cantidad,
            User actor
    ) {
        if (orden == null || orden.getOrdenFabricacionId() == null) return;
        taskRepo.findByOrdenFabricacion_OrdenFabricacionId(
                orden.getOrdenFabricacionId()).ifPresent(task -> {
            task.setObjetivo(ObjetivoProyeccionBatchRecord.CERRADO);
            task.setCantidadObtenida(cantidad);
            task.setLimpiarCantidadObtenida(false);
            if (actor != null) task.setSolicitadoPor(actor);
            reactivar(task);
        });
    }

    @Transactional
    public void solicitarAnulacion(OrdenProduccion orden, User actor) {
        if (orden == null) return;
        taskRepo.findByOrdenProduccion_OrdenId(orden.getOrdenId()).ifPresent(task -> {
            task.setObjetivo(ObjetivoProyeccionBatchRecord.ANULADO);
            if (actor != null) task.setSolicitadoPor(actor);
            reactivar(task);
        });
    }

    @Transactional
    public void solicitarAnulacion(OrdenFabricacion orden, User actor) {
        if (orden == null || orden.getOrdenFabricacionId() == null) return;
        taskRepo.findByOrdenFabricacion_OrdenFabricacionId(
                orden.getOrdenFabricacionId()).ifPresent(task -> {
            task.setObjetivo(ObjetivoProyeccionBatchRecord.ANULADO);
            if (actor != null) task.setSolicitadoPor(actor);
            reactivar(task);
        });
    }

    private void preparar(
            BatchRecordProjectionTask task,
            Lote lote,
            User actor,
            ObjetivoProyeccionBatchRecord objetivo
    ) {
        if (lote == null || actor == null) {
            throw new IllegalArgumentException(
                    "El lote y el usuario son obligatorios para solicitar el expediente.");
        }
        task.setLote(lote);
        task.setSolicitadoPor(actor);
        task.setObjetivo(objetivo);
        reactivar(task);
    }

    private void reactivar(BatchRecordProjectionTask task) {
        task.setEstado(EstadoTareaProyeccionBatchRecord.PENDIENTE);
        task.setProximoIntentoEn(LocalDateTime.now(applicationClock));
        task.setCompletadoEn(null);
        task.setUltimoError(null);
        task.setSolicitudVersion(task.getSolicitudVersion() + 1);
        taskRepo.save(task);
    }
}
