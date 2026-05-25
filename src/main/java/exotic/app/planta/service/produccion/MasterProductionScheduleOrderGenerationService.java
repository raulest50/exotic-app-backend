package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.dto.GenerarOdpDesdeMpsRequestDTO;
import exotic.app.planta.model.produccion.dto.GenerarOdpDesdeMpsResponseDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalOrdenProduccionListItemDTO;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO_save;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarBlockDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarCellDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalItemDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalResponseDTO;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class MasterProductionScheduleOrderGenerationService {

    private static final Pattern LOTE_PATTERN = Pattern.compile("^(.+)-(\\d+)-(\\d{2})$");

    private final MasterProductionScheduleSemanalRepo masterProductionScheduleSemanalRepo;
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final ProduccionService produccionService;
    private final ObjectMapper objectMapper;

    public GenerarOdpDesdeMpsResponseDTO generarOrdenesDesdeSemanaAprobada(
            GenerarOdpDesdeMpsRequestDTO request,
            String generatedByUsername
    ) {
        if (request == null || request.getWeekStartDate() == null) {
            throw new IllegalArgumentException("weekStartDate es obligatorio.");
        }
        validateGeneratedByUsername(generatedByUsername);

        MasterProductionScheduleSemanal mps = masterProductionScheduleSemanalRepo.findByWeekStartDate(request.getWeekStartDate())
                .orElseThrow(() -> new MpsSemanalNotFoundException(
                        "No existe MPS semanal para la semana iniciando en " + request.getWeekStartDate() + "."
                ));

        if (mps.getEstado() != EstadoMpsSemanal.APROBADO) {
            throw new IllegalStateException("Solo se pueden generar ODPs desde semanas en estado APROBADO.");
        }

        if (ordenProduccionRepo.existsByMpsSemanal_MpsId(mps.getMpsId())) {
            throw new IllegalStateException("La semana ya tiene ODPs generadas y no admite una nueva generacion.");
        }

        PropuestaMpsSemanalResponseDTO snapshot = readSnapshot(mps);
        MpsSemanalSnapshotMetrics metrics = MpsSemanalSnapshotMetrics.fromSnapshot(snapshot);
        if (!metrics.hasExpectedOrders()) {
            throw new IllegalStateException("No se pueden generar ODPs para una semana sin ODPs esperadas.");
        }

        Map<String, PropuestaMpsSemanalItemDTO> itemsByProductoId = buildItemsByProductoId(snapshot);
        Map<String, LoteSequenceState> lotesPorProducto = new HashMap<>();
        List<Integer> ordenesIds = new ArrayList<>();
        int totalBloquesProgramados = 0;
        int totalLotesProgramados = 0;

        if (snapshot.getCalendar() != null && snapshot.getCalendar().getRows() != null) {
            for (var row : snapshot.getCalendar().getRows()) {
                if (row.getDays() == null) {
                    continue;
                }
                for (PropuestaMpsCalendarCellDTO cell : row.getDays()) {
                    if (cell.getBlocks() == null || cell.getBlocks().isEmpty()) {
                        continue;
                    }
                    for (PropuestaMpsCalendarBlockDTO block : cell.getBlocks()) {
                        int lotesAsignados = Math.max(block.getLotesAsignados(), 0);
                        if (lotesAsignados == 0) {
                            continue;
                        }

                        totalBloquesProgramados++;
                        totalLotesProgramados += lotesAsignados;

                        PropuestaMpsSemanalItemDTO item = itemsByProductoId.get(block.getProductoId());
                        if (item == null) {
                            throw new IllegalStateException("No se encontro el item MPS asociado al producto " + block.getProductoId() + ".");
                        }

                        if (block.getBlockId() == null || block.getBlockId().isBlank()) {
                            throw new IllegalStateException("Se encontro un bloque calendarizado sin blockId para el producto " + block.getProductoId() + ".");
                        }
                        if (cell.getDate() == null) {
                            throw new IllegalStateException("Se encontro una celda calendarizada sin fecha para el producto " + block.getProductoId() + ".");
                        }

                        for (int loteOrdinal = 1; loteOrdinal <= lotesAsignados; loteOrdinal++) {
                            String loteBatchNumber = nextLotForProduct(block.getProductoId(), lotesPorProducto);
                            OrdenProduccionDTO_save dto = buildOrdenDto(snapshot.getWeekStartDate(), cell.getDate(), block, item, loteBatchNumber);
                            OrdenProduccion orden = produccionService.saveOrdenProduccionDesdeMps(
                                    dto,
                                    mps,
                                    block.getBlockId(),
                                    loteOrdinal
                            );
                            ordenesIds.add(orden.getOrdenId());
                        }
                    }
                }
            }
        }

        mps.setFechaGeneracionOdps(LocalDateTime.now());
        mps.setGeneradoPorUsername(generatedByUsername);
        masterProductionScheduleSemanalRepo.save(mps);

        GenerarOdpDesdeMpsResponseDTO response = new GenerarOdpDesdeMpsResponseDTO();
        response.setMpsId(mps.getMpsId());
        response.setWeekStartDate(mps.getWeekStartDate());
        response.setTotalBloquesProgramados(totalBloquesProgramados);
        response.setTotalLotesProgramados(totalLotesProgramados);
        response.setTotalOrdenesCreadas(ordenesIds.size());
        response.setOrdenesIds(ordenesIds);
        return response;
    }

    @Transactional(readOnly = true)
    public List<MpsSemanalOrdenProduccionListItemDTO> getOrdenesGeneradasPorSemana(LocalDate weekStartDate) {
        if (weekStartDate == null) {
            throw new IllegalArgumentException("weekStartDate es obligatorio.");
        }

        MasterProductionScheduleSemanal mps = masterProductionScheduleSemanalRepo.findByWeekStartDate(weekStartDate)
                .orElseThrow(() -> new MpsSemanalNotFoundException(
                        "No existe MPS semanal para la semana iniciando en " + weekStartDate + "."
                ));

        return ordenProduccionRepo.findByMpsSemanal_MpsIdOrderByOrdenIdAsc(mps.getMpsId()).stream()
                .map(MpsSemanalOrdenProduccionListItemDTO::fromEntity)
                .toList();
    }

    private PropuestaMpsSemanalResponseDTO readSnapshot(MasterProductionScheduleSemanal mps) {
        if (mps.getSnapshotJson() == null || mps.getSnapshotJson().isBlank()) {
            throw new IllegalStateException("El MPS semanal no tiene snapshot persistido.");
        }
        try {
            return objectMapper.readValue(mps.getSnapshotJson(), PropuestaMpsSemanalResponseDTO.class);
        } catch (JsonProcessingException e) {
            log.error("No se pudo deserializar el snapshot MPS para generar ODPs. mpsId={}", mps.getMpsId(), e);
            throw new IllegalStateException("No se pudo leer el snapshot persistido del MPS semanal.");
        }
    }

    private Map<String, PropuestaMpsSemanalItemDTO> buildItemsByProductoId(PropuestaMpsSemanalResponseDTO snapshot) {
        Map<String, PropuestaMpsSemanalItemDTO> itemsByProductoId = new LinkedHashMap<>();
        if (snapshot.getItems() != null) {
            for (PropuestaMpsSemanalItemDTO item : snapshot.getItems()) {
                itemsByProductoId.put(item.getProductoId(), item);
            }
        }
        return itemsByProductoId;
    }

    private OrdenProduccionDTO_save buildOrdenDto(
            LocalDate weekStartDate,
            LocalDate blockDate,
            PropuestaMpsCalendarBlockDTO block,
            PropuestaMpsSemanalItemDTO item,
            String loteBatchNumber
    ) {
        LocalDateTime fechaLanzamiento = blockDate.atStartOfDay();
        LocalDateTime fechaFinalPlanificada = fechaLanzamiento.plusDays(Math.max(item.getTiempoDiasFabricacion(), 0));

        OrdenProduccionDTO_save dto = new OrdenProduccionDTO_save();
        dto.setProductoId(block.getProductoId());
        dto.setObservaciones("Generada desde MPS semanal " + weekStartDate);
        dto.setCantidadProducir(block.getLoteSize());
        dto.setFechaLanzamiento(fechaLanzamiento);
        dto.setFechaFinalPlanificada(fechaFinalPlanificada);
        dto.setNumeroPedidoComercial(null);
        dto.setAreaOperativa("Producción");
        dto.setDepartamentoOperativo("Dirección de Operaciones");
        dto.setVendedorResponsableId(null);
        dto.setLoteBatchNumber(loteBatchNumber);
        return dto;
    }

    private String nextLotForProduct(String productoId, Map<String, LoteSequenceState> lotesPorProducto) {
        LoteSequenceState state = lotesPorProducto.get(productoId);
        if (state == null) {
            state = buildInitialLoteState(produccionService.obtenerSiguienteNumeroLote(productoId));
            lotesPorProducto.put(productoId, state);
        }

        String nextLot = state.formatCurrent();
        state.increment();
        return nextLot;
    }

    private LoteSequenceState buildInitialLoteState(String nextLot) {
        Matcher matcher = LOTE_PATTERN.matcher(nextLot);
        if (!matcher.matches()) {
            throw new IllegalStateException("No se pudo interpretar el numero de lote generado: " + nextLot);
        }

        String prefix = matcher.group(1);
        int sequence = Integer.parseInt(matcher.group(2));
        int padding = matcher.group(2).length();
        String yearSuffix = matcher.group(3);
        return new LoteSequenceState(prefix, sequence, padding, yearSuffix);
    }

    private void validateGeneratedByUsername(String generatedByUsername) {
        if (generatedByUsername == null || generatedByUsername.isBlank()) {
            throw new IllegalArgumentException("No se pudo determinar el usuario que genera las ODPs.");
        }
    }

    private static final class LoteSequenceState {
        private final String prefix;
        private final int padding;
        private final String yearSuffix;
        private int sequence;

        private LoteSequenceState(String prefix, int sequence, int padding, String yearSuffix) {
            this.prefix = prefix;
            this.sequence = sequence;
            this.padding = padding;
            this.yearSuffix = yearSuffix;
        }

        private String formatCurrent() {
            return prefix + "-" + String.format("%0" + padding + "d", sequence) + "-" + yearSuffix;
        }

        private void increment() {
            sequence++;
        }
    }
}
