package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.SemanaMPS;
import exotic.app.planta.model.produccion.dto.SemanaMPSDTO;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.SemanaMPSRepo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanaMPSServiceTest {

    private static final String STANDARD = SemanaMPS.STANDARD_ISO_8601_MONDAY_SATURDAY;

    @Test
    void getOrCreateByStartDate_createsIsoWeekUsingMondayToSaturdayRange() {
        SemanaMPSRepo semanaRepo = mock(SemanaMPSRepo.class);
        MasterProductionScheduleSemanalRepo mpsRepo = mock(MasterProductionScheduleSemanalRepo.class);
        SemanaMPSService service = new SemanaMPSService(semanaRepo, mpsRepo);
        LocalDate monday = LocalDate.of(2026, 6, 1);

        when(semanaRepo.findByStandardAndStartDate(STANDARD, monday)).thenReturn(Optional.empty());
        when(semanaRepo.save(any(SemanaMPS.class))).thenAnswer(invocation -> {
            SemanaMPS semana = invocation.getArgument(0);
            semana.setId(10L);
            return semana;
        });

        SemanaMPS semana = service.getOrCreateByStartDate(monday);

        assertEquals(10L, semana.getId());
        assertEquals("S23-2026", semana.getCodigo());
        assertEquals(2026, semana.getAnioSemana());
        assertEquals(23, semana.getNumeroSemana());
        assertEquals(monday, semana.getStartDate());
        assertEquals(LocalDate.of(2026, 6, 6), semana.getEndDate());
        assertEquals(STANDARD, semana.getStandard());
    }

    @Test
    void getOrCreateByStartDate_reusesExistingWeek() {
        SemanaMPSRepo semanaRepo = mock(SemanaMPSRepo.class);
        MasterProductionScheduleSemanalRepo mpsRepo = mock(MasterProductionScheduleSemanalRepo.class);
        SemanaMPSService service = new SemanaMPSService(semanaRepo, mpsRepo);
        LocalDate monday = LocalDate.of(2026, 6, 1);
        SemanaMPS existing = new SemanaMPS();
        existing.setId(22L);
        existing.setCodigo("S23-2026");
        existing.setAnioSemana(2026);
        existing.setNumeroSemana(23);
        existing.setStartDate(monday);
        existing.setEndDate(monday.plusDays(5));
        existing.setStandard(STANDARD);

        when(semanaRepo.findByStandardAndStartDate(STANDARD, monday)).thenReturn(Optional.of(existing));

        SemanaMPS semana = service.getOrCreateByStartDate(monday);

        assertEquals(22L, semana.getId());
        verify(semanaRepo, never()).save(any(SemanaMPS.class));
    }

    @Test
    void getOrCreateByStartDate_rejectsNonMonday() {
        SemanaMPSService service = new SemanaMPSService(
                mock(SemanaMPSRepo.class),
                mock(MasterProductionScheduleSemanalRepo.class)
        );

        assertThrows(IllegalArgumentException.class, () -> service.getOrCreateByStartDate(LocalDate.of(2026, 6, 2)));
    }

    @Test
    void listIsoWeeksForYear_includesIsoWeek53WhenYearHasIt() {
        SemanaMPSRepo semanaRepo = mock(SemanaMPSRepo.class);
        MasterProductionScheduleSemanalRepo mpsRepo = mock(MasterProductionScheduleSemanalRepo.class);
        SemanaMPSService service = new SemanaMPSService(semanaRepo, mpsRepo);

        when(semanaRepo.findAllByStandardAndAnioSemanaOrderByNumeroSemanaAsc(STANDARD, 2026)).thenReturn(List.of());
        when(mpsRepo.findAllByWeekStartDateBetween(
                eq(LocalDate.of(2025, 12, 29)),
                eq(LocalDate.of(2026, 12, 28))
        )).thenReturn(List.of());

        List<SemanaMPSDTO> semanas = service.listIsoWeeksForYear(2026);

        assertEquals(53, semanas.size());
        assertEquals("S01-2026", semanas.getFirst().getCodigo());
        assertEquals(LocalDate.of(2025, 12, 29), semanas.getFirst().getStartDate());
        assertEquals("S53-2026", semanas.getLast().getCodigo());
        assertEquals(LocalDate.of(2026, 12, 28), semanas.getLast().getStartDate());
        assertNull(semanas.getFirst().getMpsId());
    }

    @Test
    void listIsoWeeksForYear_marksWeekWithPersistedMps() {
        SemanaMPSRepo semanaRepo = mock(SemanaMPSRepo.class);
        MasterProductionScheduleSemanalRepo mpsRepo = mock(MasterProductionScheduleSemanalRepo.class);
        SemanaMPSService service = new SemanaMPSService(semanaRepo, mpsRepo);
        LocalDate monday = LocalDate.of(2026, 6, 1);

        MasterProductionScheduleSemanal mps = new MasterProductionScheduleSemanal();
        mps.setMpsId(77);
        mps.setWeekStartDate(monday);
        mps.setWeekEndDate(monday.plusDays(5));
        mps.setEstado(EstadoMpsSemanal.BORRADOR);

        when(semanaRepo.findAllByStandardAndAnioSemanaOrderByNumeroSemanaAsc(STANDARD, 2026)).thenReturn(List.of());
        when(mpsRepo.findAllByWeekStartDateBetween(
                eq(LocalDate.of(2025, 12, 29)),
                eq(LocalDate.of(2026, 12, 28))
        )).thenReturn(List.of(mps));

        List<SemanaMPSDTO> semanas = service.listIsoWeeksForYear(2026);

        Optional<SemanaMPSDTO> target = semanas.stream()
                .filter(semana -> monday.equals(semana.getStartDate()))
                .findFirst();
        assertTrue(target.isPresent());
        assertEquals(77, target.get().getMpsId());
        assertEquals(EstadoMpsSemanal.BORRADOR, target.get().getEstado());
    }
}
