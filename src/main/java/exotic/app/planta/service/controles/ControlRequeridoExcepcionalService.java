package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.ControlRequerido;
import exotic.app.planta.model.controles.dto.ControlDTOs.AdicionExcepcionalWriteRequest;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordFirma;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.ControlRequeridoRepo;
import exotic.app.planta.service.produccion.BatchRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ControlRequeridoExcepcionalService {
    private final ControlWorkflowService controlWorkflowService;
    private final BatchRecordService batchRecordService;
    private final ControlRequeridoRepo requeridoRepo;

    @Transactional
    public ControlRequerido agregarFirmado(
            AmbitoControl ambito, User actor, AdicionExcepcionalWriteRequest request,
            String ipOrigen, String userAgent) {
        ControlRequerido requisito = controlWorkflowService.agregarExcepcional(
                ambito, actor, request.batchRecordId(), request.planId(),
                request.batchRecordEtapaId(), request.motivo());
        BatchRecordFirma firma = batchRecordService.registrarAdicionExcepcionalControl(
                requisito, actor, request.motivo(), ipOrigen, userAgent);
        if (firma.getRevision() == null) {
            throw new IllegalStateException(
                    "La firma de adicion excepcional no genero una revision auditable.");
        }
        requisito.setRevisionAdicion(firma.getRevision());
        requisito.setFirmaAdicion(firma);
        return requeridoRepo.saveAndFlush(requisito);
    }
}
