package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.dto.GuardarMpsSemanalDraftRequestDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalResponseDTO;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalDraftNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class MasterProductionScheduleDraftService {

    private final MasterProductionScheduleSemanalRepo masterProductionScheduleSemanalRepo;
    private final ObjectMapper objectMapper;

    public MpsSemanalDraftDTO saveDraft(GuardarMpsSemanalDraftRequestDTO request) {
        validateRequest(request);

        LocalDate weekStartDate = request.getWeekStartDate();
        LocalDate weekEndDate = weekStartDate.plusDays(5);

        MasterProductionScheduleSemanal entity = masterProductionScheduleSemanalRepo.findByWeekStartDate(weekStartDate)
                .orElseGet(MasterProductionScheduleSemanal::new);

        if (entity.getMpsId() != null && entity.getEstado() != EstadoMpsSemanal.BORRADOR) {
            throw new IllegalStateException("La semana ya no esta en estado BORRADOR y no puede sobrescribirse.");
        }

        PropuestaMpsSemanalResponseDTO snapshot = new PropuestaMpsSemanalResponseDTO();
        snapshot.setWeekStartDate(weekStartDate);
        snapshot.setWeekEndDate(weekEndDate);
        snapshot.setSummary(request.getSummary());
        snapshot.setItems(request.getItems());
        snapshot.setCalendar(request.getCalendar());

        entity.setWeekStartDate(weekStartDate);
        entity.setWeekEndDate(weekEndDate);
        entity.setEstado(EstadoMpsSemanal.BORRADOR);
        entity.setSnapshotJson(writeSnapshot(snapshot));

        MasterProductionScheduleSemanal saved = masterProductionScheduleSemanalRepo.save(entity);
        return MpsSemanalDraftDTO.fromEntityAndSnapshot(saved, snapshot);
    }

    @Transactional(readOnly = true)
    public MpsSemanalDraftDTO getDraftByWeekStartDate(LocalDate weekStartDate) {
        validateWeekStartDate(weekStartDate);

        MasterProductionScheduleSemanal entity = masterProductionScheduleSemanalRepo.findByWeekStartDate(weekStartDate)
                .orElseThrow(() -> new MpsSemanalDraftNotFoundException(
                        "No existe borrador MPS para la semana iniciando en " + weekStartDate + "."
                ));

        PropuestaMpsSemanalResponseDTO snapshot = readSnapshot(entity);
        return MpsSemanalDraftDTO.fromEntityAndSnapshot(entity, snapshot);
    }

    private void validateRequest(GuardarMpsSemanalDraftRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud del borrador MPS no puede ser nula.");
        }
        validateWeekStartDate(request.getWeekStartDate());
        if (request.getSummary() == null) {
            throw new IllegalArgumentException("El resumen del borrador MPS es obligatorio.");
        }
        if (request.getItems() == null) {
            throw new IllegalArgumentException("Los items del borrador MPS son obligatorios.");
        }
        if (request.getCalendar() == null) {
            throw new IllegalArgumentException("El calendario del borrador MPS es obligatorio.");
        }
    }

    private void validateWeekStartDate(LocalDate weekStartDate) {
        if (weekStartDate == null) {
            throw new IllegalArgumentException("weekStartDate es obligatorio.");
        }
        if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStartDate debe corresponder a un lunes.");
        }
    }

    private String writeSnapshot(PropuestaMpsSemanalResponseDTO snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("No se pudo serializar el snapshot del borrador MPS", e);
            throw new IllegalStateException("No se pudo persistir el snapshot del borrador MPS.");
        }
    }

    private PropuestaMpsSemanalResponseDTO readSnapshot(MasterProductionScheduleSemanal entity) {
        if (entity.getSnapshotJson() == null || entity.getSnapshotJson().isBlank()) {
            throw new IllegalStateException("El borrador MPS no tiene snapshot persistido.");
        }
        try {
            return objectMapper.readValue(entity.getSnapshotJson(), PropuestaMpsSemanalResponseDTO.class);
        } catch (JsonProcessingException e) {
            log.error("No se pudo deserializar el snapshot del borrador MPS. mpsId={}", entity.getMpsId(), e);
            throw new IllegalStateException("No se pudo leer el snapshot persistido del borrador MPS.");
        }
    }
}
