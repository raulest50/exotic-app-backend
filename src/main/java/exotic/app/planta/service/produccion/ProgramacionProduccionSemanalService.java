package exotic.app.planta.service.produccion;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.EstadoMpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalDia;
import exotic.app.planta.model.produccion.MpsSemanalItem;
import exotic.app.planta.model.produccion.MpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.SemanaMPS;
import exotic.app.planta.model.produccion.dto.GuardarProgramacionProduccionSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.produccion.dto.ProgramacionProduccionSemanalDiaRequestDTO;
import exotic.app.planta.model.produccion.dto.ProgramacionProduccionSemanalItemRequestDTO;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.MpsSemanalDiaRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ProgramacionProduccionSemanalService {

    private final TerminadoRepo terminadoRepo;
    private final MasterProductionScheduleSemanalRepo masterProductionScheduleSemanalRepo;
    private final MpsSemanalDiaRepo mpsSemanalDiaRepo;
    private final SemanaMPSService semanaMPSService;
    private final MpsSemanalEditWindowService mpsSemanalEditWindowService;
    private final MasterProductionScheduleDraftService masterProductionScheduleDraftService;

    private record EntryKey(LocalDate fecha, String terminadoId) {}

    private record LockedEntry(int numeroLotes, String observacion) {}

    private static final class ConsolidatedEntry {
        private final LocalDate fecha;
        private final int dayIndex;
        private final String terminadoId;
        private int numeroLotes;
        private String observacion;
        private final int displayOrder;

        private ConsolidatedEntry(LocalDate fecha, int dayIndex, String terminadoId, int numeroLotes, String observacion, int displayOrder) {
            this.fecha = fecha;
            this.dayIndex = dayIndex;
            this.terminadoId = terminadoId;
            this.numeroLotes = numeroLotes;
            this.observacion = observacion;
            this.displayOrder = displayOrder;
        }

        private void merge(int lotes, String nextObservacion) {
            this.numeroLotes += lotes;
            this.observacion = mergeObservacion(this.observacion, nextObservacion);
        }
    }

    public MpsSemanalDraftDTO guardarBorradorDirecto(GuardarProgramacionProduccionSemanalRequestDTO request) {
        validateRequestShell(request);

        SemanaMPS semanaMps = semanaMPSService.getOrCreateByStartDate(request.getWeekStartDate());
        LocalDate weekStartDate = semanaMps.getStartDate();
        LocalDate weekEndDate = semanaMps.getEndDate();
        List<ConsolidatedEntry> entries = consolidateEntries(request.getDias(), weekStartDate, weekEndDate);

        MasterProductionScheduleSemanal entity = masterProductionScheduleSemanalRepo.findBySemanaMps_Id(semanaMps.getId())
                .or(() -> masterProductionScheduleSemanalRepo.findByWeekStartDate(weekStartDate))
                .orElseGet(MasterProductionScheduleSemanal::new);

        if (entity.getMpsId() != null && entity.getEstado() != EstadoMpsSemanal.BORRADOR) {
            throw new IllegalStateException("La semana ya no esta en estado BORRADOR y no puede sobrescribirse.");
        }

        List<MpsSemanalDia> currentDias = entity.getMpsId() == null
                ? List.of()
                : mpsSemanalDiaRepo.findAllByMpsSemanal_MpsIdOrderByDayIndexAsc(entity.getMpsId());

        validateLockedDaysUnchanged(entries, currentDias, weekEndDate, entity.getMpsId() != null);
        int nextRevision = resolveNextRevision(entity, currentDias, entries);

        List<String> terminadoIds = entries.stream()
                .map(entry -> entry.terminadoId)
                .distinct()
                .toList();
        Map<String, Terminado> terminadosById = terminadoRepo.findByProductoIdIn(terminadoIds).stream()
                .collect(Collectors.toMap(Terminado::getProductoId, Function.identity()));

        entity.setSemanaMps(semanaMps);
        entity.setWeekStartDate(weekStartDate);
        entity.setWeekEndDate(weekEndDate);
        entity.setRevisionNumero(nextRevision);
        entity.setEstado(EstadoMpsSemanal.BORRADOR);
        entity.setFechaAprobacion(null);
        entity.setAprobadoPorUsername(null);
        entity.setFechaGeneracionOdps(null);
        entity.setGeneradoPorUsername(null);

        if (entity.getMpsId() != null) {
            entity.getDias().clear();
            mpsSemanalDiaRepo.deleteAll(currentDias);
            masterProductionScheduleSemanalRepo.saveAndFlush(entity);
        }

        Map<Integer, List<ConsolidatedEntry>> entriesByDayIndex = entries.stream()
                .collect(Collectors.groupingBy(entry -> entry.dayIndex, LinkedHashMap::new, Collectors.toList()));

        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            MpsSemanalDia dia = new MpsSemanalDia();
            dia.setMpsSemanal(entity);
            dia.setFecha(weekStartDate.plusDays(dayIndex));
            dia.setDayIndex(dayIndex);
            dia.setDisplayOrder(dayIndex);

            List<ConsolidatedEntry> dayEntries = entriesByDayIndex.getOrDefault(dayIndex, List.of()).stream()
                    .sorted(Comparator.comparingInt(entry -> entry.displayOrder))
                    .toList();
            int displayOrder = 0;
            for (ConsolidatedEntry entry : dayEntries) {
                Terminado terminado = terminadosById.get(entry.terminadoId);
                validateTerminado(entry.terminadoId, terminado);
                dia.getItems().add(buildItem(entity, dia, terminado, entry, displayOrder++));
            }
            entity.getDias().add(dia);
        }

        MasterProductionScheduleSemanal saved = masterProductionScheduleSemanalRepo.save(entity);
        return masterProductionScheduleDraftService.getByWeekStartDate(saved.getWeekStartDate());
    }

    private void validateRequestShell(GuardarProgramacionProduccionSemanalRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de programacion semanal no puede ser nula.");
        }
        if (request.getWeekStartDate() == null) {
            throw new IllegalArgumentException("weekStartDate es obligatorio.");
        }
        if (request.getWeekStartDate().getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStartDate debe corresponder a un lunes.");
        }
        if (request.getDias() == null || request.getDias().isEmpty()) {
            throw new IllegalArgumentException("Se requiere la programacion de dias del MPS semanal.");
        }
    }

    private List<ConsolidatedEntry> consolidateEntries(
            List<ProgramacionProduccionSemanalDiaRequestDTO> inputDias,
            LocalDate weekStartDate,
            LocalDate weekEndDate
    ) {
        LinkedHashMap<EntryKey, ConsolidatedEntry> grouped = new LinkedHashMap<>();
        int order = 0;
        for (ProgramacionProduccionSemanalDiaRequestDTO inputDia : inputDias) {
            if (inputDia == null) {
                continue;
            }
            LocalDate fecha = inputDia.getFecha();
            int dayIndex = inputDia.getDayIndex();
            validateDia(fecha, dayIndex, weekStartDate, weekEndDate);
            if (inputDia.getItems() == null) {
                continue;
            }
            for (ProgramacionProduccionSemanalItemRequestDTO item : inputDia.getItems()) {
                if (item == null) {
                    continue;
                }
                String terminadoId = normalizeRequiredText(item.getTerminadoId(), "terminadoId es obligatorio.");
                if (item.getNumeroLotes() <= 0) {
                    throw new IllegalArgumentException("numeroLotes debe ser mayor que cero para " + terminadoId + ".");
                }
                EntryKey key = new EntryKey(fecha, terminadoId);
                ConsolidatedEntry existing = grouped.get(key);
                if (existing == null) {
                    grouped.put(key, new ConsolidatedEntry(
                            fecha,
                            dayIndex,
                            terminadoId,
                            item.getNumeroLotes(),
                            normalizeOptionalText(item.getObservacion()),
                            order++
                    ));
                } else {
                    existing.merge(item.getNumeroLotes(), item.getObservacion());
                }
            }
        }

        if (grouped.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un item de programacion semanal.");
        }
        return new ArrayList<>(grouped.values());
    }

    private void validateDia(LocalDate fecha, int dayIndex, LocalDate weekStartDate, LocalDate weekEndDate) {
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha de cada dia MPS es obligatoria.");
        }
        if (dayIndex < 0 || dayIndex > 5) {
            throw new IllegalArgumentException("dayIndex debe estar entre 0 y 5.");
        }
        if (fecha.isBefore(weekStartDate) || fecha.isAfter(weekEndDate)) {
            throw new IllegalArgumentException("Todas las fechas del MPS deben estar dentro de la semana lunes-sabado.");
        }
        if (!fecha.equals(weekStartDate.plusDays(dayIndex))) {
            throw new IllegalArgumentException("La fecha del dia MPS no corresponde al dayIndex indicado.");
        }
    }

    private void validateTerminado(String terminadoId, Terminado terminado) {
        if (terminado == null) {
            throw new IllegalArgumentException("Producto no encontrado o no es un terminado: " + terminadoId);
        }
        if (resolveLoteSize(terminado) <= 0) {
            throw new IllegalArgumentException("El producto terminado no tiene lote size configurado: " + terminadoId);
        }
        if (terminado.getPrefijoLote() == null || terminado.getPrefijoLote().isBlank()) {
            throw new IllegalArgumentException("El producto terminado no tiene prefijo de lote definido: " + terminadoId);
        }
    }

    private MpsSemanalItem buildItem(
            MasterProductionScheduleSemanal mps,
            MpsSemanalDia dia,
            Terminado terminado,
            ConsolidatedEntry entry,
            int displayOrder
    ) {
        int loteSize = resolveLoteSize(terminado);
        int tiempoDiasFabricacion = resolveTiempoDiasFabricacion(terminado);
        Categoria categoria = terminado.getCategoria();

        MpsSemanalItem item = new MpsSemanalItem();
        item.setMpsSemanal(mps);
        item.setMpsDia(dia);
        item.setTerminado(terminado);
        item.setTerminadoNombre(terminado.getNombre() != null ? terminado.getNombre() : terminado.getProductoId());
        item.setCategoriaId(categoria != null ? categoria.getCategoriaId() : null);
        item.setCategoriaNombre(categoria != null ? categoria.getCategoriaNombre() : null);
        item.setLoteSize(loteSize);
        item.setTiempoDiasFabricacion(tiempoDiasFabricacion);
        item.setNumeroLotes(entry.numeroLotes);
        item.setCantidadTotal(entry.numeroLotes * (double) loteSize);
        item.setFechaLanzamiento(entry.fecha);
        item.setFechaFinalPlanificada(entry.fecha.plusDays(Math.max(tiempoDiasFabricacion, 0)));
        item.setObservacion(entry.observacion);
        item.setWarning(resolveWarning(item, mps.getWeekEndDate()));
        item.setDisplayOrder(displayOrder);

        for (int ordinal = 1; ordinal <= entry.numeroLotes; ordinal++) {
            MpsSemanalLotePlanificado lotePlanificado = new MpsSemanalLotePlanificado();
            lotePlanificado.setMpsItem(item);
            lotePlanificado.setLoteOrdinal(ordinal);
            lotePlanificado.setCantidadPlanificada(loteSize);
            lotePlanificado.setEstado(EstadoMpsSemanalLotePlanificado.PENDIENTE_ODP);
            item.getLotesPlanificados().add(lotePlanificado);
        }

        return item;
    }

    private String resolveWarning(MpsSemanalItem item, LocalDate weekEndDate) {
        if (item.getFechaFinalPlanificada() != null && weekEndDate != null && item.getFechaFinalPlanificada().isAfter(weekEndDate)) {
            return "La fecha final planificada desborda la semana lunes-sabado.";
        }
        return null;
    }

    private void validateLockedDaysUnchanged(
            List<ConsolidatedEntry> entries,
            List<MpsSemanalDia> currentDias,
            LocalDate weekEndDate,
            boolean existingMps
    ) {
        LocalDate editableFromDate = mpsSemanalEditWindowService.getEditableFromDate();
        if (weekEndDate.isBefore(editableFromDate)) {
            throw new IllegalStateException("La semana MPS no tiene dias editables. "
                    + "La primera fecha editable es " + editableFromDate + ".");
        }

        Map<EntryKey, LockedEntry> nextLocked = lockedFromEntries(entries, editableFromDate);
        if (!existingMps) {
            if (!nextLocked.isEmpty()) {
                throw new IllegalStateException(buildLockedDaysMessage(editableFromDate));
            }
            return;
        }

        Map<EntryKey, LockedEntry> currentLocked = lockedFromDias(currentDias, editableFromDate);
        if (!Objects.equals(currentLocked, nextLocked)) {
            throw new IllegalStateException(buildLockedDaysMessage(editableFromDate));
        }
    }

    private Map<EntryKey, LockedEntry> lockedFromEntries(List<ConsolidatedEntry> entries, LocalDate editableFromDate) {
        return entries.stream()
                .filter(entry -> entry.fecha.isBefore(editableFromDate))
                .collect(Collectors.toMap(
                        entry -> new EntryKey(entry.fecha, entry.terminadoId),
                        entry -> new LockedEntry(entry.numeroLotes, normalizeOptionalText(entry.observacion)),
                        (left, ignored) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<EntryKey, LockedEntry> lockedFromDias(List<MpsSemanalDia> dias, LocalDate editableFromDate) {
        LinkedHashMap<EntryKey, LockedEntry> locked = new LinkedHashMap<>();
        for (MpsSemanalDia dia : dias) {
            if (dia.getFecha() == null || !dia.getFecha().isBefore(editableFromDate)) {
                continue;
            }
            for (MpsSemanalItem item : dia.getItems()) {
                String terminadoId = item.getTerminado() != null ? item.getTerminado().getProductoId() : null;
                locked.put(
                        new EntryKey(dia.getFecha(), terminadoId),
                        new LockedEntry(item.getNumeroLotes(), normalizeOptionalText(item.getObservacion()))
                );
            }
        }
        return locked;
    }

    private int resolveNextRevision(
            MasterProductionScheduleSemanal entity,
            List<MpsSemanalDia> currentDias,
            List<ConsolidatedEntry> nextEntries
    ) {
        if (entity.getMpsId() == null) {
            return 1;
        }
        String currentFingerprint = fingerprintFromDias(currentDias);
        String nextFingerprint = fingerprintFromEntries(nextEntries);
        int currentRevision = entity.getRevisionNumero() != null && entity.getRevisionNumero() > 0
                ? entity.getRevisionNumero()
                : 1;
        return currentFingerprint.equals(nextFingerprint) ? currentRevision : currentRevision + 1;
    }

    private String fingerprintFromDias(List<MpsSemanalDia> dias) {
        return dias.stream()
                .flatMap(dia -> dia.getItems().stream()
                        .map(item -> dia.getFecha() + "|" + (item.getTerminado() != null ? item.getTerminado().getProductoId() : "")
                                + "|" + item.getNumeroLotes() + "|" + normalizeOptionalText(item.getObservacion())))
                .sorted()
                .collect(Collectors.joining(";"));
    }

    private String fingerprintFromEntries(List<ConsolidatedEntry> entries) {
        return entries.stream()
                .map(entry -> entry.fecha + "|" + entry.terminadoId + "|" + entry.numeroLotes + "|" + normalizeOptionalText(entry.observacion))
                .sorted()
                .collect(Collectors.joining(";"));
    }

    private static int resolveLoteSize(Terminado terminado) {
        Categoria categoria = terminado != null ? terminado.getCategoria() : null;
        return categoria != null ? categoria.getLoteSize() : 0;
    }

    private static int resolveTiempoDiasFabricacion(Terminado terminado) {
        Categoria categoria = terminado != null ? terminado.getCategoria() : null;
        return categoria != null ? categoria.getTiempoDiasFabricacion() : 0;
    }

    private static String normalizeRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String mergeObservacion(String current, String next) {
        String normalizedNext = normalizeOptionalText(next);
        if (normalizedNext == null) {
            return normalizeOptionalText(current);
        }
        String normalizedCurrent = normalizeOptionalText(current);
        if (normalizedCurrent == null) {
            return normalizedNext;
        }
        if (normalizedCurrent.contains(normalizedNext)) {
            return normalizedCurrent;
        }
        return normalizedCurrent + " | " + normalizedNext;
    }

    private String buildLockedDaysMessage(LocalDate editableFromDate) {
        return "No se pueden crear, modificar ni eliminar programaciones en dias bloqueados. "
                + "La primera fecha editable es " + editableFromDate + ".";
    }
}
