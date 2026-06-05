package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarBlockDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarCellDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarRowDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MpsSemanalEditWindowService {

    private static final double EPSILON = 0.000001;
    private static final int LOCKED_DAYS_AHEAD = 2;

    private final Clock applicationClock;

    private record LockedBlockKey(String productoId, int loteSize) {}

    private record LockedBlockAmount(double cantidadAsignada, int lotesAsignados) {
        private LockedBlockAmount add(PropuestaMpsCalendarBlockDTO block) {
            return new LockedBlockAmount(
                    cantidadAsignada + block.getCantidadAsignada(),
                    lotesAsignados + block.getLotesAsignados()
            );
        }
    }

    public LocalDate getEditableFromDate() {
        return LocalDate.now(applicationClock).plusDays(LOCKED_DAYS_AHEAD);
    }

    public boolean isEditable(LocalDate date) {
        return date != null && !date.isBefore(getEditableFromDate());
    }

    public void validateLockedDaysUnchanged(
            PropuestaMpsSemanalResponseDTO nextSnapshot,
            PropuestaMpsSemanalResponseDTO currentSnapshot
    ) {
        LocalDate editableFromDate = getEditableFromDate();
        if (!hasEditableDate(nextSnapshot, editableFromDate)) {
            throw new IllegalStateException("La semana MPS no tiene dias editables. "
                    + "La primera fecha editable es " + editableFromDate + ".");
        }

        Map<LocalDate, Map<LockedBlockKey, LockedBlockAmount>> nextLockedSchedule =
                extractLockedSchedule(nextSnapshot, editableFromDate);

        if (currentSnapshot == null) {
            if (hasScheduledBlocks(nextLockedSchedule)) {
                throw new IllegalStateException(buildLockedDaysMessage(editableFromDate));
            }
            return;
        }

        Map<LocalDate, Map<LockedBlockKey, LockedBlockAmount>> currentLockedSchedule =
                extractLockedSchedule(currentSnapshot, editableFromDate);

        if (!sameLockedSchedule(currentLockedSchedule, nextLockedSchedule)) {
            throw new IllegalStateException(buildLockedDaysMessage(editableFromDate));
        }
    }

    private Map<LocalDate, Map<LockedBlockKey, LockedBlockAmount>> extractLockedSchedule(
            PropuestaMpsSemanalResponseDTO snapshot,
            LocalDate editableFromDate
    ) {
        Map<LocalDate, Map<LockedBlockKey, LockedBlockAmount>> lockedSchedule = new HashMap<>();
        if (snapshot == null || snapshot.getCalendar() == null || snapshot.getCalendar().getRows() == null) {
            return lockedSchedule;
        }

        for (PropuestaMpsCalendarRowDTO row : snapshot.getCalendar().getRows()) {
            if (row == null || row.getDays() == null) {
                continue;
            }
            for (PropuestaMpsCalendarCellDTO cell : row.getDays()) {
                if (cell == null || cell.getBlocks() == null || cell.getBlocks().isEmpty()) {
                    continue;
                }

                LocalDate cellDate = resolveCellDate(snapshot, cell);
                if (cellDate == null) {
                    throw new IllegalArgumentException("Cada celda programada del MPS debe tener una fecha valida.");
                }
                if (!cellDate.isBefore(editableFromDate)) {
                    continue;
                }

                Map<LockedBlockKey, LockedBlockAmount> blocksByProduct =
                        lockedSchedule.computeIfAbsent(cellDate, ignored -> new HashMap<>());
                for (PropuestaMpsCalendarBlockDTO block : cell.getBlocks()) {
                    if (isEmptyBlock(block)) {
                        continue;
                    }
                    LockedBlockKey key = new LockedBlockKey(normalizeProductoId(block.getProductoId()), block.getLoteSize());
                    LockedBlockAmount currentAmount =
                            blocksByProduct.getOrDefault(key, new LockedBlockAmount(0, 0));
                    blocksByProduct.put(key, currentAmount.add(block));
                }
            }
        }

        return lockedSchedule;
    }

    private boolean hasEditableDate(PropuestaMpsSemanalResponseDTO snapshot, LocalDate editableFromDate) {
        if (snapshot == null) {
            return false;
        }
        if (snapshot.getWeekEndDate() != null) {
            return !snapshot.getWeekEndDate().isBefore(editableFromDate);
        }
        if (snapshot.getWeekStartDate() != null) {
            return !snapshot.getWeekStartDate().plusDays(5).isBefore(editableFromDate);
        }
        return false;
    }

    private LocalDate resolveCellDate(PropuestaMpsSemanalResponseDTO snapshot, PropuestaMpsCalendarCellDTO cell) {
        if (cell.getDate() != null) {
            return cell.getDate();
        }
        if (snapshot.getWeekStartDate() != null && cell.getDayIndex() >= 0) {
            return snapshot.getWeekStartDate().plusDays(cell.getDayIndex());
        }
        return null;
    }

    private boolean isEmptyBlock(PropuestaMpsCalendarBlockDTO block) {
        return block == null
                || (Math.abs(block.getCantidadAsignada()) <= EPSILON && block.getLotesAsignados() == 0);
    }

    private String normalizeProductoId(String productoId) {
        return productoId == null ? "" : productoId.trim();
    }

    private boolean hasScheduledBlocks(Map<LocalDate, Map<LockedBlockKey, LockedBlockAmount>> schedule) {
        return schedule.values().stream().anyMatch(blocks -> blocks != null && !blocks.isEmpty());
    }

    private boolean sameLockedSchedule(
            Map<LocalDate, Map<LockedBlockKey, LockedBlockAmount>> currentSchedule,
            Map<LocalDate, Map<LockedBlockKey, LockedBlockAmount>> nextSchedule
    ) {
        Set<LocalDate> allDates = new HashSet<>();
        allDates.addAll(currentSchedule.keySet());
        allDates.addAll(nextSchedule.keySet());

        for (LocalDate date : allDates) {
            Map<LockedBlockKey, LockedBlockAmount> currentBlocks = currentSchedule.getOrDefault(date, Map.of());
            Map<LockedBlockKey, LockedBlockAmount> nextBlocks = nextSchedule.getOrDefault(date, Map.of());
            if (!sameLockedBlocks(currentBlocks, nextBlocks)) {
                return false;
            }
        }
        return true;
    }

    private boolean sameLockedBlocks(
            Map<LockedBlockKey, LockedBlockAmount> currentBlocks,
            Map<LockedBlockKey, LockedBlockAmount> nextBlocks
    ) {
        Set<LockedBlockKey> allKeys = new HashSet<>();
        allKeys.addAll(currentBlocks.keySet());
        allKeys.addAll(nextBlocks.keySet());

        for (LockedBlockKey key : allKeys) {
            LockedBlockAmount currentAmount = currentBlocks.getOrDefault(key, new LockedBlockAmount(0, 0));
            LockedBlockAmount nextAmount = nextBlocks.getOrDefault(key, new LockedBlockAmount(0, 0));
            if (currentAmount.lotesAsignados() != nextAmount.lotesAsignados()) {
                return false;
            }
            if (Math.abs(currentAmount.cantidadAsignada() - nextAmount.cantidadAsignada()) > EPSILON) {
                return false;
            }
        }
        return true;
    }

    private String buildLockedDaysMessage(LocalDate editableFromDate) {
        return "No se pueden crear, modificar ni eliminar programaciones en dias bloqueados. "
                + "La primera fecha editable es " + editableFromDate + ".";
    }
}
