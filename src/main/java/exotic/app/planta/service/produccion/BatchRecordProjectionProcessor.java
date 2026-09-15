package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.batchrecord.*;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordProjectionTaskRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@Slf4j
public class BatchRecordProjectionProcessor {

    private final BatchRecordProjectionTaskRepo taskRepo;
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final OrdenFabricacionRepo ordenFabricacionRepo;
    private final LoteRepo loteRepo;
    private final UserRepository userRepo;
    private final BatchRecordService batchRecordService;
    private final Clock applicationClock;
    private final TransactionTemplate transactions;

    public BatchRecordProjectionProcessor(
            BatchRecordProjectionTaskRepo taskRepo,
            OrdenProduccionRepo ordenProduccionRepo,
            OrdenFabricacionRepo ordenFabricacionRepo,
            LoteRepo loteRepo,
            UserRepository userRepo,
            BatchRecordService batchRecordService,
            Clock applicationClock,
            PlatformTransactionManager transactionManager
    ) {
        this.taskRepo = taskRepo;
        this.ordenProduccionRepo = ordenProduccionRepo;
        this.ordenFabricacionRepo = ordenFabricacionRepo;
        this.loteRepo = loteRepo;
        this.userRepo = userRepo;
        this.batchRecordService = batchRecordService;
        this.applicationClock = applicationClock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void procesar(Long taskId) {
        TaskSnapshot task = transactions.execute(status -> reclamar(taskId));
        if (task == null) return;

        Long batchRecordId;
        try {
            batchRecordId = transactions.execute(status -> asegurarExpediente(task));
        } catch (RuntimeException error) {
            finalizar(task, null, List.of(mensaje("creación mínima", error)));
            return;
        }
        if (batchRecordId == null) {
            finalizar(task, null, List.of("creación mínima: no se obtuvo expediente"));
            return;
        }

        transactions.executeWithoutResult(status -> batchRecordService.actualizarEstadoSincronizacion(
                batchRecordId, EstadoSincronizacionBatchRecord.SINCRONIZANDO, List.of(), null));

        List<String> advertencias = new ArrayList<>();
        ejecutarPaso("snapshot de materiales", advertencias,
                id -> batchRecordService.sincronizarMaterialesDocumentales(id), batchRecordId);
        ejecutarPaso("estructura y eventos", advertencias,
                id -> batchRecordService.sincronizarEstructuraDocumental(id), batchRecordId);
        ejecutarPaso("consumos", advertencias,
                id -> batchRecordService.sincronizarConsumosDocumentales(id), batchRecordId);
        ejecutarPaso("controles", advertencias,
                id -> batchRecordService.materializarRequisitosDocumentales(id), batchRecordId);

        if (task.limpiarCantidad()) {
            ejecutarPaso("reversión de cantidad", advertencias,
                    id -> batchRecordService.limpiarCantidadObtenidaDocumental(id), batchRecordId);
        } else if (task.cantidadObtenida() != null) {
            ejecutarPaso("cantidad obtenida", advertencias,
                    id -> batchRecordService.registrarCantidadObtenidaDocumental(
                            id, task.cantidadObtenida()), batchRecordId);
        }

        if (task.objetivo() == ObjetivoProyeccionBatchRecord.ANULADO) {
            ejecutarPaso("anulación documental", advertencias,
                    id -> batchRecordService.anularDocumentalmente(id, cargarActor(task.actorId())),
                    batchRecordId);
        } else if (task.objetivo() == ObjetivoProyeccionBatchRecord.CERRADO) {
            ejecutarPaso("cierre documental", advertencias,
                    id -> batchRecordService.cerrarDocumentalmente(id, cargarActor(task.actorId())),
                    batchRecordId);
        }
        finalizar(task, batchRecordId, advertencias);
    }

    private TaskSnapshot reclamar(Long taskId) {
        BatchRecordProjectionTask task = taskRepo.findByIdForUpdate(taskId).orElse(null);
        if (task == null) return null;
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        boolean atascada = task.getEstado() == EstadoTareaProyeccionBatchRecord.PROCESANDO
                && task.getActualizadoEn() != null
                && task.getActualizadoEn().isBefore(ahora.minusMinutes(10));
        if (task.getEstado() != EstadoTareaProyeccionBatchRecord.PENDIENTE
                && task.getEstado() != EstadoTareaProyeccionBatchRecord.ERROR
                && !atascada) {
            return null;
        }
        if (!atascada && task.getProximoIntentoEn().isAfter(ahora)) return null;
        task.setEstado(EstadoTareaProyeccionBatchRecord.PROCESANDO);
        task.setIntentos(task.getIntentos() + 1);
        taskRepo.saveAndFlush(task);
        return new TaskSnapshot(
                task.getId(),
                task.getOrdenProduccion() == null ? null
                        : task.getOrdenProduccion().getOrdenId(),
                task.getOrdenFabricacion() == null ? null
                        : task.getOrdenFabricacion().getOrdenFabricacionId(),
                task.getLote().getId(),
                task.getSolicitadoPor().getId(),
                task.getObjetivo(),
                task.getCantidadObtenida(),
                task.isLimpiarCantidadObtenida(),
                task.getSolicitudVersion(),
                task.getIntentos());
    }

    private Long asegurarExpediente(TaskSnapshot task) {
        Lote lote = loteRepo.findById(task.loteId())
                .orElseThrow(() -> new IllegalStateException("El lote documental ya no existe."));
        User actor = cargarActor(task.actorId());
        if (task.ordenProduccionId() != null) {
            OrdenProduccion orden = ordenProduccionRepo.findById(task.ordenProduccionId())
                    .orElseThrow(() -> new IllegalStateException("La OP documental ya no existe."));
            return batchRecordService.crearParaOrdenProduccion(orden, lote, actor).getId();
        }
        OrdenFabricacion orden = ordenFabricacionRepo.findById(task.ordenFabricacionId())
                .orElseThrow(() -> new IllegalStateException("La OF documental ya no existe."));
        return batchRecordService.crearParaOrdenFabricacion(orden, lote, actor).getId();
    }

    private User cargarActor(Long actorId) {
        return userRepo.findById(actorId)
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario de la solicitud documental ya no existe."));
    }

    private void ejecutarPaso(
            String nombre,
            List<String> advertencias,
            Consumer<Long> accion,
            Long batchRecordId
    ) {
        try {
            transactions.executeWithoutResult(status -> accion.accept(batchRecordId));
        } catch (RuntimeException error) {
            String mensaje = mensaje(nombre, error);
            advertencias.add(mensaje);
            log.warn("Batch Record {} incompleto en {}: {}",
                    batchRecordId, nombre, error.getMessage());
        }
    }

    private void finalizar(
            TaskSnapshot snapshot,
            Long batchRecordId,
            List<String> advertencias
    ) {
        transactions.executeWithoutResult(status -> {
            BatchRecordProjectionTask task = taskRepo.findByIdForUpdate(snapshot.id())
                    .orElseThrow(() -> new IllegalStateException(
                            "La tarea documental desapareció durante su ejecución."));
            LocalDateTime ahora = LocalDateTime.now(applicationClock);
            if (task.getSolicitudVersion() != snapshot.solicitudVersion()) {
                task.setEstado(EstadoTareaProyeccionBatchRecord.PENDIENTE);
                task.setProximoIntentoEn(ahora);
                taskRepo.save(task);
                if (batchRecordId != null) {
                    batchRecordService.actualizarEstadoSincronizacion(
                            batchRecordId, EstadoSincronizacionBatchRecord.PENDIENTE,
                            advertencias, advertencias.isEmpty() ? null : advertencias.get(0));
                }
                return;
            }
            if (advertencias.isEmpty()) {
                task.setEstado(EstadoTareaProyeccionBatchRecord.COMPLETADA);
                task.setCompletadoEn(ahora);
                task.setUltimoError(null);
                if (batchRecordId != null) {
                    batchRecordService.actualizarEstadoSincronizacion(
                            batchRecordId, EstadoSincronizacionBatchRecord.ACTUALIZADO,
                            List.of(), null);
                }
            } else {
                task.setEstado(EstadoTareaProyeccionBatchRecord.ERROR);
                task.setCompletadoEn(null);
                task.setUltimoError(String.join("\n", advertencias));
                long esperaSegundos = Math.min(300L,
                        5L * (1L << Math.min(snapshot.intentos(), 6)));
                task.setProximoIntentoEn(ahora.plusSeconds(esperaSegundos));
                if (batchRecordId != null) {
                    batchRecordService.actualizarEstadoSincronizacion(
                            batchRecordId, EstadoSincronizacionBatchRecord.INCOMPLETO,
                            advertencias, advertencias.get(0));
                }
            }
            taskRepo.save(task);
        });
    }

    private String mensaje(String paso, RuntimeException error) {
        String detalle = error.getMessage();
        if (detalle == null || detalle.isBlank()) detalle = error.getClass().getSimpleName();
        if (detalle.length() > 1000) detalle = detalle.substring(0, 1000);
        return paso + ": " + detalle;
    }

    private record TaskSnapshot(
            Long id,
            Integer ordenProduccionId,
            Long ordenFabricacionId,
            Long loteId,
            Long actorId,
            ObjetivoProyeccionBatchRecord objetivo,
            BigDecimal cantidadObtenida,
            boolean limpiarCantidad,
            long solicitudVersion,
            int intentos
    ) {
    }
}
