package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.EstadoMpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalItem;
import exotic.app.planta.model.produccion.MpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.dto.GenerarOdpDesdeMpsRequestDTO;
import exotic.app.planta.model.produccion.dto.GenerarOdpDesdeMpsResponseDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalOrdenProduccionListItemDTO;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO_save;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.MpsSemanalLotePlanificadoRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MasterProductionScheduleOrderGenerationService {

    private static final Pattern LOTE_PATTERN = Pattern.compile("^(.+)-(\\d+)-(\\d{2})$");

    private final MasterProductionScheduleSemanalRepo masterProductionScheduleSemanalRepo;
    private final MpsSemanalLotePlanificadoRepo mpsSemanalLotePlanificadoRepo;
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final ProduccionService produccionService;

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

        List<MpsSemanalLotePlanificado> lotesPendientes = mpsSemanalLotePlanificadoRepo
                .findPendingByMpsIdOrdered(
                        mps.getMpsId(),
                        EstadoMpsSemanalLotePlanificado.PENDIENTE_ODP
                );
        if (lotesPendientes.isEmpty()) {
            throw new IllegalStateException("No se pueden generar ODPs para una semana sin lotes planificados pendientes.");
        }

        Map<String, LoteSequenceState> lotesPorProducto = new HashMap<>();
        List<Integer> ordenesIds = new ArrayList<>();
        for (MpsSemanalLotePlanificado lotePlanificado : lotesPendientes) {
            MpsSemanalItem item = lotePlanificado.getMpsItem();
            validateLotePlanificado(lotePlanificado, item);

            String productoId = item.getTerminado().getProductoId();
            String loteBatchNumber = nextLotForProduct(productoId, lotesPorProducto);
            OrdenProduccionDTO_save dto = buildOrdenDto(item, lotePlanificado, loteBatchNumber);
            OrdenProduccion orden = produccionService.saveOrdenProduccionDesdeMps(dto, mps, lotePlanificado);

            lotePlanificado.setEstado(EstadoMpsSemanalLotePlanificado.ODP_GENERADA);
            lotePlanificado.setOrdenProduccion(orden);
            mpsSemanalLotePlanificadoRepo.save(lotePlanificado);
            ordenesIds.add(orden.getOrdenId());
        }

        mps.setFechaGeneracionOdps(LocalDateTime.now());
        mps.setGeneradoPorUsername(generatedByUsername);
        mps.setEstado(EstadoMpsSemanal.CERRADO);
        masterProductionScheduleSemanalRepo.save(mps);

        GenerarOdpDesdeMpsResponseDTO response = new GenerarOdpDesdeMpsResponseDTO();
        response.setMpsId(mps.getMpsId());
        response.setWeekStartDate(mps.getWeekStartDate());
        response.setTotalBloquesProgramados((int) lotesPendientes.stream()
                .map(lote -> lote.getMpsItem().getId())
                .distinct()
                .count());
        response.setTotalLotesProgramados(lotesPendientes.size());
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

    private void validateLotePlanificado(MpsSemanalLotePlanificado lotePlanificado, MpsSemanalItem item) {
        if (item == null || item.getTerminado() == null) {
            throw new IllegalStateException("Se encontro un lote planificado sin item o terminado asociado.");
        }
        if (item.getLoteSize() <= 0) {
            throw new IllegalStateException("El producto " + item.getTerminado().getProductoId() + " no tiene lote size valido para generar ODPs.");
        }
        if (lotePlanificado.getCantidadPlanificada() <= 0) {
            throw new IllegalStateException("El lote planificado " + lotePlanificado.getId() + " no tiene cantidad valida.");
        }
    }

    private OrdenProduccionDTO_save buildOrdenDto(
            MpsSemanalItem item,
            MpsSemanalLotePlanificado lotePlanificado,
            String loteBatchNumber
    ) {
        OrdenProduccionDTO_save dto = new OrdenProduccionDTO_save();
        dto.setProductoId(item.getTerminado().getProductoId());
        dto.setObservaciones("Generada desde MPS semanal " + item.getMpsSemanal().getWeekStartDate());
        dto.setCantidadProducir(lotePlanificado.getCantidadPlanificada());
        dto.setFechaLanzamiento(item.getFechaLanzamiento().atStartOfDay());
        dto.setFechaFinalPlanificada(item.getFechaFinalPlanificada().atStartOfDay());
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
