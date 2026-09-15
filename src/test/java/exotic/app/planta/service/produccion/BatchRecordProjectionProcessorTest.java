package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordProjectionTask;
import exotic.app.planta.model.produccion.batchrecord.EstadoSincronizacionBatchRecord;
import exotic.app.planta.model.produccion.batchrecord.EstadoTareaProyeccionBatchRecord;
import exotic.app.planta.model.produccion.batchrecord.ObjetivoProyeccionBatchRecord;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordProjectionTaskRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchRecordProjectionProcessorTest {

    @Mock private BatchRecordProjectionTaskRepo taskRepo;
    @Mock private OrdenProduccionRepo ordenProduccionRepo;
    @Mock private OrdenFabricacionRepo ordenFabricacionRepo;
    @Mock private LoteRepo loteRepo;
    @Mock private UserRepository userRepo;
    @Mock private BatchRecordService batchRecordService;
    @Mock private PlatformTransactionManager transactionManager;

    private BatchRecordProjectionProcessor processor;
    private BatchRecordProjectionTask task;
    private OrdenProduccion orden;
    private Lote lote;
    private User actor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-15T15:00:00Z"), ZoneOffset.UTC);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        processor = new BatchRecordProjectionProcessor(
                taskRepo, ordenProduccionRepo, ordenFabricacionRepo, loteRepo,
                userRepo, batchRecordService, clock, transactionManager);

        orden = new OrdenProduccion();
        orden.setOrdenId(31);
        lote = new Lote();
        lote.setId(71L);
        actor = User.builder().id(9L).username("operador").build();
        task = new BatchRecordProjectionTask();
        task.setId(11L);
        task.setOrdenProduccion(orden);
        task.setLote(lote);
        task.setSolicitadoPor(actor);
        task.setObjetivo(ObjetivoProyeccionBatchRecord.ACTIVO);
        task.setEstado(EstadoTareaProyeccionBatchRecord.PENDIENTE);
        task.setProximoIntentoEn(LocalDateTime.of(2026, 9, 15, 14, 59));
        task.setSolicitudVersion(1);

        when(taskRepo.findByIdForUpdate(11L)).thenReturn(Optional.of(task));
        when(loteRepo.findById(71L)).thenReturn(Optional.of(lote));
        when(userRepo.findById(9L)).thenReturn(Optional.of(actor));
        when(ordenProduccionRepo.findById(31)).thenReturn(Optional.of(orden));
        BatchRecord record = new BatchRecord();
        record.setId(501L);
        when(batchRecordService.crearParaOrdenProduccion(orden, lote, actor))
                .thenReturn(record);
    }

    @Test
    void aSectionFailureDoesNotStopTheOtherSectionsAndLeavesAReprocessableTask() {
        doThrow(new IllegalStateException("receta incompleta"))
                .when(batchRecordService).sincronizarMaterialesDocumentales(501L);

        processor.procesar(11L);

        verify(batchRecordService).sincronizarEstructuraDocumental(501L);
        verify(batchRecordService).sincronizarConsumosDocumentales(501L);
        verify(batchRecordService).materializarRequisitosDocumentales(501L);
        verify(batchRecordService).actualizarEstadoSincronizacion(
                eq(501L), eq(EstadoSincronizacionBatchRecord.INCOMPLETO),
                eq(List.of("snapshot de materiales: receta incompleta")),
                eq("snapshot de materiales: receta incompleta"));
        assertEquals(EstadoTareaProyeccionBatchRecord.ERROR, task.getEstado());
        assertEquals("snapshot de materiales: receta incompleta", task.getUltimoError());
    }

    @Test
    void aConcurrentNotificationIsNotLostWhenTheCurrentProjectionFinishes() {
        doAnswer(invocation -> {
            task.setSolicitudVersion(2);
            return null;
        }).when(batchRecordService).sincronizarConsumosDocumentales(501L);

        processor.procesar(11L);

        assertEquals(EstadoTareaProyeccionBatchRecord.PENDIENTE, task.getEstado());
        assertNull(task.getCompletadoEn());
        verify(batchRecordService).actualizarEstadoSincronizacion(
                501L, EstadoSincronizacionBatchRecord.PENDIENTE, List.of(), null);
    }
}
