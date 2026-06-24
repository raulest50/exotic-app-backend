package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarBlockDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarCellDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarRowDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalCalendarDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalResponseDTO;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MpsSemanalEditWindowServiceTest {

    private final MasterDirectiveService masterDirectiveService = mock(MasterDirectiveService.class);
    private final MpsSemanalEditWindowService service = new MpsSemanalEditWindowService(
            Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneId.of("America/Bogota")),
            masterDirectiveService
    );

    @BeforeEach
    void setUp() {
        when(masterDirectiveService.getMpsSemanalDiasBloqueoEdicion()).thenReturn(2);
    }

    @Test
    void editableFromDateUsesDefaultTwoLockedDays() {
        assertEquals(LocalDate.of(2026, 6, 3), service.getEditableFromDate());
    }

    @Test
    void editableFromDateAllowsCurrentDayWhenDirectiveIsZero() {
        when(masterDirectiveService.getMpsSemanalDiasBloqueoEdicion()).thenReturn(0);

        assertEquals(LocalDate.of(2026, 6, 1), service.getEditableFromDate());
    }

    @Test
    void editableFromDateBlocksOnlyCurrentDayWhenDirectiveIsOne() {
        when(masterDirectiveService.getMpsSemanalDiasBloqueoEdicion()).thenReturn(1);

        assertEquals(LocalDate.of(2026, 6, 2), service.getEditableFromDate());
    }

    @Test
    void rejectsNewSnapshotWithBlocksBeforeEditableDate() {
        PropuestaMpsSemanalResponseDTO next = snapshot(
                LocalDate.of(2026, 6, 1),
                block(0, "401001", 220, 1, 220)
        );

        assertThrows(IllegalStateException.class, () -> service.validateLockedDaysUnchanged(next, null));
    }

    @Test
    void allowsFutureChangesWhenLockedBlocksRemainUnchanged() {
        PropuestaMpsSemanalResponseDTO current = snapshot(
                LocalDate.of(2026, 6, 1),
                block(0, "401001", 220, 1, 220),
                block(2, "401002", 220, 1, 220)
        );
        PropuestaMpsSemanalResponseDTO next = snapshot(
                LocalDate.of(2026, 6, 1),
                block(0, "401001", 220, 1, 220),
                block(2, "401002", 440, 2, 220)
        );

        assertDoesNotThrow(() -> service.validateLockedDaysUnchanged(next, current));
    }

    @Test
    void rejectsChangesInLockedBlocks() {
        PropuestaMpsSemanalResponseDTO current = snapshot(
                LocalDate.of(2026, 6, 1),
                block(0, "401001", 220, 1, 220)
        );
        PropuestaMpsSemanalResponseDTO next = snapshot(
                LocalDate.of(2026, 6, 1),
                block(0, "401001", 440, 2, 220)
        );

        assertThrows(IllegalStateException.class, () -> service.validateLockedDaysUnchanged(next, current));
    }

    @Test
    void rejectsWeeksWithoutEditableDays() {
        PropuestaMpsSemanalResponseDTO current = snapshot(
                LocalDate.of(2026, 5, 18),
                block(0, "401001", 220, 1, 220)
        );
        PropuestaMpsSemanalResponseDTO next = snapshot(
                LocalDate.of(2026, 5, 18),
                block(0, "401001", 220, 1, 220)
        );

        assertThrows(IllegalStateException.class, () -> service.validateLockedDaysUnchanged(next, current));
    }

    private PropuestaMpsSemanalResponseDTO snapshot(LocalDate weekStartDate, TestBlock... blocks) {
        PropuestaMpsSemanalResponseDTO snapshot = new PropuestaMpsSemanalResponseDTO();
        snapshot.setWeekStartDate(weekStartDate);
        snapshot.setWeekEndDate(weekStartDate.plusDays(5));

        PropuestaMpsCalendarRowDTO row = new PropuestaMpsCalendarRowDTO();
        row.setRowKey("categoria::1");

        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            PropuestaMpsCalendarCellDTO cell = new PropuestaMpsCalendarCellDTO();
            cell.setDayIndex(dayIndex);
            cell.setDate(weekStartDate.plusDays(dayIndex));
            row.getDays().add(cell);
        }

        for (TestBlock testBlock : blocks) {
            PropuestaMpsCalendarBlockDTO block = new PropuestaMpsCalendarBlockDTO();
            block.setProductoId(testBlock.productoId());
            block.setCantidadAsignada(testBlock.cantidadAsignada());
            block.setLotesAsignados(testBlock.lotesAsignados());
            block.setLoteSize(testBlock.loteSize());
            row.getDays().get(testBlock.dayIndex()).getBlocks().add(block);
        }

        PropuestaMpsSemanalCalendarDTO calendar = new PropuestaMpsSemanalCalendarDTO();
        calendar.getRows().add(row);
        snapshot.setCalendar(calendar);
        return snapshot;
    }

    private TestBlock block(int dayIndex, String productoId, double cantidadAsignada, int lotesAsignados, int loteSize) {
        return new TestBlock(dayIndex, productoId, cantidadAsignada, lotesAsignados, loteSize);
    }

    private record TestBlock(
            int dayIndex,
            String productoId,
            double cantidadAsignada,
            int lotesAsignados,
            int loteSize
    ) {}
}
