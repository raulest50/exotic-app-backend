package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordProjectionTask;
import exotic.app.planta.model.produccion.batchrecord.EstadoTareaProyeccionBatchRecord;
import exotic.app.planta.model.produccion.batchrecord.ObjetivoProyeccionBatchRecord;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordProjectionTaskRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchRecordProjectionQueueServiceTest {

    @Mock private BatchRecordProjectionTaskRepo taskRepo;

    private BatchRecordProjectionQueueService service;

    @BeforeEach
    void setUp() {
        service = new BatchRecordProjectionQueueService(
                taskRepo,
                Clock.fixed(Instant.parse("2026-09-15T15:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void creationPersistsOnlyTheDocumentaryIntent() {
        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(42);
        Lote lote = new Lote();
        User actor = User.builder().id(7L).username("operador").build();
        when(taskRepo.findByOrdenProduccion_OrdenId(42)).thenReturn(Optional.empty());

        service.solicitarCreacion(orden, lote, actor);

        verify(taskRepo).save(org.mockito.ArgumentMatchers.argThat(task ->
                task.getOrdenProduccion() == orden
                        && task.getOrdenFabricacion() == null
                        && task.getLote() == lote
                        && task.getSolicitadoPor() == actor
                        && task.getObjetivo() == ObjetivoProyeccionBatchRecord.ACTIVO
                        && task.getEstado() == EstadoTareaProyeccionBatchRecord.PENDIENTE
                        && task.getSolicitudVersion() == 1));
    }

    @Test
    void closeReactivatesTheSameTaskIdempotently() {
        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(43);
        User actor = User.builder().id(8L).username("almacen").build();
        BatchRecordProjectionTask task = new BatchRecordProjectionTask();
        task.setOrdenProduccion(orden);
        task.setLote(new Lote());
        task.setSolicitadoPor(User.builder().id(1L).username("creador").build());
        task.setEstado(EstadoTareaProyeccionBatchRecord.COMPLETADA);
        task.setSolicitudVersion(4);
        when(taskRepo.findByOrdenProduccion_OrdenId(43)).thenReturn(Optional.of(task));

        service.solicitarCierre(orden, new BigDecimal("12.5000"), actor);

        assertSame(actor, task.getSolicitadoPor());
        assertEquals(ObjetivoProyeccionBatchRecord.CERRADO, task.getObjetivo());
        assertEquals(EstadoTareaProyeccionBatchRecord.PENDIENTE, task.getEstado());
        assertEquals(new BigDecimal("12.5000"), task.getCantidadObtenida());
        assertFalse(task.isLimpiarCantidadObtenida());
        assertEquals(5, task.getSolicitudVersion());
        verify(taskRepo).save(task);
    }
}
