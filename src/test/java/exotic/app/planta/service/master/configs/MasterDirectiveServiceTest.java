package exotic.app.planta.service.master.configs;

import exotic.app.planta.model.master.configs.MasterDirective;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.model.master.configs.dto.DTO_MasterD_Update;
import exotic.app.planta.repo.master.configs.MasterDirectiveRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterDirectiveServiceTest {

    @Test
    void getMpsSemanalDiasBloqueoEdicion_allowsZero() {
        MasterDirectiveRepo repo = mock(MasterDirectiveRepo.class);
        when(repo.findByNombre(MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION))
                .thenReturn(Optional.of(directive("0")));

        MasterDirectiveService service = service(repo);

        assertEquals(0, service.getMpsSemanalDiasBloqueoEdicion());
    }

    @Test
    void getMpsSemanalDiasBloqueoEdicion_returnsConfiguredValueInRange() {
        MasterDirectiveRepo repo = mock(MasterDirectiveRepo.class);
        when(repo.findByNombre(MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION))
                .thenReturn(Optional.of(directive("7")));

        MasterDirectiveService service = service(repo);

        assertEquals(7, service.getMpsSemanalDiasBloqueoEdicion());
    }

    @Test
    void getMpsSemanalDiasBloqueoEdicion_fallsBackWhenOutOfRange() {
        MasterDirectiveRepo repo = mock(MasterDirectiveRepo.class);
        when(repo.findByNombre(MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION))
                .thenReturn(Optional.of(directive("8")));

        MasterDirectiveService service = service(repo);

        assertEquals(
                MasterDirectiveKeys.DEFAULT_MPS_SEMANAL_DIAS_BLOQUEO_EDICION,
                service.getMpsSemanalDiasBloqueoEdicion()
        );
    }

    @Test
    void getMpsSemanalDiasBloqueoEdicion_fallsBackWhenMissing() {
        MasterDirectiveRepo repo = mock(MasterDirectiveRepo.class);
        when(repo.findByNombre(MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION))
                .thenReturn(Optional.empty());

        MasterDirectiveService service = service(repo);

        assertEquals(
                MasterDirectiveKeys.DEFAULT_MPS_SEMANAL_DIAS_BLOQUEO_EDICION,
                service.getMpsSemanalDiasBloqueoEdicion()
        );
    }

    @Test
    void isBatchRecordWorkflowEnabled_fallsBackToFalseWhenMissing() {
        MasterDirectiveRepo repo = mock(MasterDirectiveRepo.class);
        when(repo.findByNombre(MasterDirectiveKeys.BATCH_RECORD_WORKFLOW_ENABLED))
                .thenReturn(Optional.empty());

        assertFalse(service(repo).isBatchRecordWorkflowEnabled());
    }

    @Test
    void lockBatchRecordWorkflowForNewOrder_fallsBackToFalseWhenMissing() {
        MasterDirectiveRepo repo = mock(MasterDirectiveRepo.class);
        when(repo.findByNombreForShare(MasterDirectiveKeys.BATCH_RECORD_WORKFLOW_ENABLED))
                .thenReturn(Optional.empty());

        assertFalse(service(repo).lockBatchRecordWorkflowForNewOrder());
    }

    @Test
    void updateMasterDirective_rejectsBatchRecordDeactivationWithActiveRecords() {
        MasterDirectiveRepo repo = mock(MasterDirectiveRepo.class);
        BatchRecordRepo batchRecordRepo = mock(BatchRecordRepo.class);
        MasterDirective persisted = batchDirective(1L, "true");
        MasterDirective requested = batchDirective(1L, "false");
        when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(persisted));
        when(batchRecordRepo.countByEstadoNotIn(anyCollection())).thenReturn(2L);

        MasterDirectiveService service = new MasterDirectiveService(repo, batchRecordRepo);

        MasterDirectiveService.BatchRecordWorkflowTransitionException exception = assertThrows(
                MasterDirectiveService.BatchRecordWorkflowTransitionException.class,
                () -> service.updateMasterDirective(
                        new DTO_MasterD_Update(persisted, requested))
        );
        assertEquals(2L, exception.getExpedientesActivos());
    }

    @Test
    void updateMasterDirective_allowsBatchRecordDeactivationWithoutActiveRecords() {
        MasterDirectiveRepo repo = mock(MasterDirectiveRepo.class);
        BatchRecordRepo batchRecordRepo = mock(BatchRecordRepo.class);
        MasterDirective persisted = batchDirective(1L, "true");
        MasterDirective requested = batchDirective(1L, "false");
        when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(persisted));
        when(batchRecordRepo.countByEstadoNotIn(anyCollection())).thenReturn(0L);

        new MasterDirectiveService(repo, batchRecordRepo).updateMasterDirective(
                new DTO_MasterD_Update(persisted, requested));

        assertEquals("false", persisted.getValor());
    }

    private MasterDirectiveService service(MasterDirectiveRepo repo) {
        return new MasterDirectiveService(repo, mock(BatchRecordRepo.class));
    }

    private MasterDirective directive(String valor) {
        MasterDirective directive = new MasterDirective();
        directive.setNombre(MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION);
        directive.setTipoDato(MasterDirective.TipoDato.NUMERO);
        directive.setValor(valor);
        return directive;
    }

    private MasterDirective batchDirective(Long id, String valor) {
        MasterDirective directive = new MasterDirective();
        directive.setId(id);
        directive.setNombre(MasterDirectiveKeys.BATCH_RECORD_WORKFLOW_ENABLED);
        directive.setTipoDato(MasterDirective.TipoDato.BOOLEANO);
        directive.setValor(valor);
        return directive;
    }
}
