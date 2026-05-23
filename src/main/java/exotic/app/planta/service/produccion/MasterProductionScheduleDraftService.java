package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.dto.MpsSemanalListItemDTO;
import exotic.app.planta.model.produccion.dto.AprobarMpsSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.GuardarMpsSemanalDraftRequestDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalResponseDTO;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalDraftNotFoundException;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class MasterProductionScheduleDraftService {

    private final MasterProductionScheduleSemanalRepo masterProductionScheduleSemanalRepo;
    private final OrdenProduccionRepo ordenProduccionRepo;
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
        entity.setFechaAprobacion(null);
        entity.setAprobadoPorUsername(null);
        entity.setFechaGeneracionOdps(null);
        entity.setGeneradoPorUsername(null);
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

        if (entity.getEstado() != EstadoMpsSemanal.BORRADOR) {
            throw new MpsSemanalDraftNotFoundException(
                    "La semana " + weekStartDate + " existe, pero no se encuentra en estado BORRADOR."
            );
        }

        PropuestaMpsSemanalResponseDTO snapshot = readSnapshot(entity);
        return MpsSemanalDraftDTO.fromEntityAndSnapshot(entity, snapshot);
    }

    @Transactional(readOnly = true)
    public MpsSemanalDraftDTO getByWeekStartDate(LocalDate weekStartDate) {
        validateWeekStartDate(weekStartDate);

        MasterProductionScheduleSemanal entity = masterProductionScheduleSemanalRepo.findByWeekStartDate(weekStartDate)
                .orElseThrow(() -> new MpsSemanalNotFoundException(
                        "No existe MPS semanal para la semana iniciando en " + weekStartDate + "."
                ));

        PropuestaMpsSemanalResponseDTO snapshot = readSnapshot(entity);
        return MpsSemanalDraftDTO.fromEntityAndSnapshot(entity, snapshot);
    }

    @Transactional(readOnly = true)
    public List<MpsSemanalListItemDTO> listByEstado(EstadoMpsSemanal estado) {
        List<MasterProductionScheduleSemanal> entities = estado == null
                ? masterProductionScheduleSemanalRepo.findAllByOrderByWeekStartDateDesc()
                : masterProductionScheduleSemanalRepo.findAllByEstadoOrderByWeekStartDateDesc(estado);

        return entities.stream()
                .map(entity -> {
                    PropuestaMpsSemanalResponseDTO snapshot = readSnapshot(entity);
                    int totalOrdenesEsperadas = countExpectedOrders(snapshot);
                    long totalOrdenesGeneradas = ordenProduccionRepo.countByMpsSemanal_MpsId(entity.getMpsId());
                    NoProgramadosMetrics noProgramadosMetrics = countUnscheduledMetrics(snapshot);
                    return MpsSemanalListItemDTO.fromEntityAndSnapshot(
                            entity,
                            snapshot,
                            totalOrdenesEsperadas,
                            totalOrdenesGeneradas,
                            noProgramadosMetrics.totalBloquesNoProgramados(),
                            noProgramadosMetrics.totalLotesNoProgramados(),
                            noProgramadosMetrics.totalUnidadesNoProgramadas()
                    );
                })
                .toList();
    }

    public MpsSemanalDraftDTO approveWeek(AprobarMpsSemanalRequestDTO request, String approvedByUsername) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de aprobacion MPS no puede ser nula.");
        }
        validateWeekStartDate(request.getWeekStartDate());
        validateApprovedByUsername(approvedByUsername);

        MasterProductionScheduleSemanal entity = masterProductionScheduleSemanalRepo.findByWeekStartDate(request.getWeekStartDate())
                .orElseThrow(() -> new MpsSemanalNotFoundException(
                        "No existe MPS semanal para la semana iniciando en " + request.getWeekStartDate() + "."
                ));

        if (entity.getEstado() != EstadoMpsSemanal.BORRADOR) {
            throw new IllegalStateException("Solo se pueden aprobar semanas en estado BORRADOR.");
        }

        entity.setEstado(EstadoMpsSemanal.APROBADO);
        entity.setFechaAprobacion(LocalDateTime.now());
        entity.setAprobadoPorUsername(approvedByUsername);

        MasterProductionScheduleSemanal saved = masterProductionScheduleSemanalRepo.save(entity);
        PropuestaMpsSemanalResponseDTO snapshot = readSnapshot(saved);
        return MpsSemanalDraftDTO.fromEntityAndSnapshot(saved, snapshot);
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

    private void validateApprovedByUsername(String approvedByUsername) {
        if (approvedByUsername == null || approvedByUsername.isBlank()) {
            throw new IllegalArgumentException("No se pudo determinar el usuario aprobador.");
        }
    }

    private int countExpectedOrders(PropuestaMpsSemanalResponseDTO snapshot) {
        if (snapshot.getCalendar() == null || snapshot.getCalendar().getRows() == null) {
            return 0;
        }

        return snapshot.getCalendar().getRows().stream()
                .flatMap(row -> row.getDays().stream())
                .flatMap(day -> day.getBlocks().stream())
                .mapToInt(block -> Math.max(block.getLotesAsignados(), 0))
                .sum();
    }

    private NoProgramadosMetrics countUnscheduledMetrics(PropuestaMpsSemanalResponseDTO snapshot) {
        if (snapshot.getCalendar() == null || snapshot.getCalendar().getUnscheduled() == null) {
            return new NoProgramadosMetrics(0, 0, 0.0);
        }

        int totalBloques = snapshot.getCalendar().getUnscheduled().size();
        int totalLotes = snapshot.getCalendar().getUnscheduled().stream()
                .mapToInt(block -> Math.max(block.getLotesAsignados(), 0))
                .sum();
        double totalUnidades = snapshot.getCalendar().getUnscheduled().stream()
                .mapToDouble(block -> Math.max(block.getCantidadAsignada(), 0.0))
                .sum();

        return new NoProgramadosMetrics(totalBloques, totalLotes, totalUnidades);
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
            throw new IllegalStateException("El MPS semanal no tiene snapshot persistido.");
        }
        try {
            return objectMapper.readValue(entity.getSnapshotJson(), PropuestaMpsSemanalResponseDTO.class);
        } catch (JsonProcessingException e) {
            log.error("No se pudo deserializar el snapshot del borrador MPS. mpsId={}", entity.getMpsId(), e);
            throw new IllegalStateException("No se pudo leer el snapshot persistido del MPS semanal.");
        }
    }

    private record NoProgramadosMetrics(
            int totalBloquesNoProgramados,
            int totalLotesNoProgramados,
            double totalUnidadesNoProgramadas
    ) {
    }
}
