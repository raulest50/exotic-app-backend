package exotic.app.planta.service.produccion;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalDia;
import exotic.app.planta.model.produccion.MpsSemanalItem;
import exotic.app.planta.model.produccion.SemanaMPS;
import exotic.app.planta.model.produccion.dto.GuardarProgramacionProduccionSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.produccion.dto.ProgramacionProduccionSemanalDiaRequestDTO;
import exotic.app.planta.model.produccion.dto.ProgramacionProduccionSemanalItemRequestDTO;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.MpsSemanalDiaRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgramacionProduccionSemanalServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 1);

    @Test
    void guardarBorradorDirecto_rejectsNonMondayWeekStartDate() {
        TestContext context = buildContext();
        GuardarProgramacionProduccionSemanalRequestDTO request = buildRequest(
                LocalDate.of(2026, 6, 2),
                List.of(day(LocalDate.of(2026, 6, 2), 0, item("T-001", 1)))
        );

        assertThrows(IllegalArgumentException.class, () -> context.service.guardarBorradorDirecto(request));
    }

    @Test
    void guardarBorradorDirecto_rejectsDateOutsideMondayToSaturdayWeek() {
        TestContext context = buildContext();
        GuardarProgramacionProduccionSemanalRequestDTO request = buildRequest(
                MONDAY,
                List.of(day(MONDAY.plusDays(6), 6, item("T-001", 1)))
        );

        assertThrows(IllegalArgumentException.class, () -> context.service.guardarBorradorDirecto(request));
    }

    @Test
    void guardarBorradorDirecto_rejectsTerminadoWithoutLoteSize() {
        TestContext context = buildContext();
        when(context.terminadoRepo.findByProductoIdIn(anyCollection()))
                .thenReturn(List.of(buildTerminado("T-001", "Producto", 0, "PRD")));

        GuardarProgramacionProduccionSemanalRequestDTO request = buildRequest(
                MONDAY,
                List.of(day(MONDAY, 0, item("T-001", 1)))
        );

        assertThrows(IllegalArgumentException.class, () -> context.service.guardarBorradorDirecto(request));
    }

    @Test
    void guardarBorradorDirecto_rejectsTerminadoWithoutPrefijoLote() {
        TestContext context = buildContext();
        when(context.terminadoRepo.findByProductoIdIn(anyCollection()))
                .thenReturn(List.of(buildTerminado("T-001", "Producto", 10, "")));

        GuardarProgramacionProduccionSemanalRequestDTO request = buildRequest(
                MONDAY,
                List.of(day(MONDAY, 0, item("T-001", 1)))
        );

        assertThrows(IllegalArgumentException.class, () -> context.service.guardarBorradorDirecto(request));
    }

    @Test
    void guardarBorradorDirecto_createsSixDaysAndPlannedLotsFromDailyPayload() {
        TestContext context = buildContext();
        Terminado shampoo = buildTerminado("SH-001", "Shampoo", 10, "SHP");
        Terminado crema = buildTerminado("CR-001", "Crema", 5, "CRM");
        when(context.terminadoRepo.findByProductoIdIn(anyCollection())).thenReturn(List.of(shampoo, crema));

        GuardarProgramacionProduccionSemanalRequestDTO request = buildRequest(
                MONDAY,
                List.of(
                        day(MONDAY, 0, item("SH-001", 2), item("SH-001", 1)),
                        day(MONDAY.plusDays(2), 2, item("CR-001", 3))
                )
        );

        context.service.guardarBorradorDirecto(request);

        ArgumentCaptor<MasterProductionScheduleSemanal> captor =
                ArgumentCaptor.forClass(MasterProductionScheduleSemanal.class);
        verify(context.mpsRepo).save(captor.capture());
        MasterProductionScheduleSemanal saved = captor.getValue();

        assertEquals(MONDAY, saved.getWeekStartDate());
        assertEquals(MONDAY.plusDays(5), saved.getWeekEndDate());
        assertEquals(6, saved.getDias().size());

        MpsSemanalDia monday = saved.getDias().getFirst();
        assertEquals(MONDAY, monday.getFecha());
        assertEquals(0, monday.getDayIndex());
        assertEquals(1, monday.getItems().size());

        MpsSemanalItem shampooItem = monday.getItems().getFirst();
        assertEquals("SH-001", shampooItem.getTerminado().getProductoId());
        assertEquals(3, shampooItem.getNumeroLotes());
        assertEquals(30.0, shampooItem.getCantidadTotal());
        assertEquals(3, shampooItem.getLotesPlanificados().size());
        assertEquals(1, shampooItem.getLotesPlanificados().getFirst().getLoteOrdinal());

        MpsSemanalDia wednesday = saved.getDias().get(2);
        assertEquals(1, wednesday.getItems().size());
        MpsSemanalItem cremaItem = wednesday.getItems().getFirst();
        assertEquals("CR-001", cremaItem.getTerminado().getProductoId());
        assertEquals(3, cremaItem.getNumeroLotes());
        assertEquals(15.0, cremaItem.getCantidadTotal());
        assertEquals(3, cremaItem.getLotesPlanificados().size());
    }

    private TestContext buildContext() {
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        MasterProductionScheduleSemanalRepo mpsRepo = mock(MasterProductionScheduleSemanalRepo.class);
        MpsSemanalDiaRepo diaRepo = mock(MpsSemanalDiaRepo.class);
        SemanaMPSService semanaMPSService = mock(SemanaMPSService.class);
        MpsSemanalEditWindowService editWindowService = mock(MpsSemanalEditWindowService.class);
        MasterProductionScheduleDraftService draftService = mock(MasterProductionScheduleDraftService.class);

        SemanaMPS semana = new SemanaMPS();
        semana.setId(44L);
        semana.setCodigo("S23-2026");
        semana.setStartDate(MONDAY);
        semana.setEndDate(MONDAY.plusDays(5));
        semana.setStandard(SemanaMPS.STANDARD_ISO_8601_MONDAY_SATURDAY);

        when(semanaMPSService.getOrCreateByStartDate(MONDAY)).thenReturn(semana);
        when(mpsRepo.findBySemanaMps_Id(44L)).thenReturn(Optional.empty());
        when(mpsRepo.findByWeekStartDate(MONDAY)).thenReturn(Optional.empty());
        when(editWindowService.getEditableFromDate()).thenReturn(MONDAY);
        when(mpsRepo.save(any(MasterProductionScheduleSemanal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(draftService.getByWeekStartDate(MONDAY)).thenReturn(new MpsSemanalDraftDTO());

        ProgramacionProduccionSemanalService service = new ProgramacionProduccionSemanalService(
                terminadoRepo,
                mpsRepo,
                diaRepo,
                semanaMPSService,
                editWindowService,
                draftService
        );

        return new TestContext(service, terminadoRepo, mpsRepo);
    }

    private GuardarProgramacionProduccionSemanalRequestDTO buildRequest(
            LocalDate weekStartDate,
            List<ProgramacionProduccionSemanalDiaRequestDTO> dias
    ) {
        GuardarProgramacionProduccionSemanalRequestDTO request = new GuardarProgramacionProduccionSemanalRequestDTO();
        request.setWeekStartDate(weekStartDate);
        request.setDias(dias);
        return request;
    }

    private ProgramacionProduccionSemanalDiaRequestDTO day(
            LocalDate fecha,
            int dayIndex,
            ProgramacionProduccionSemanalItemRequestDTO... items
    ) {
        ProgramacionProduccionSemanalDiaRequestDTO day = new ProgramacionProduccionSemanalDiaRequestDTO();
        day.setFecha(fecha);
        day.setDayIndex(dayIndex);
        day.setItems(List.of(items));
        return day;
    }

    private ProgramacionProduccionSemanalItemRequestDTO item(String terminadoId, int numeroLotes) {
        ProgramacionProduccionSemanalItemRequestDTO item = new ProgramacionProduccionSemanalItemRequestDTO();
        item.setTerminadoId(terminadoId);
        item.setNumeroLotes(numeroLotes);
        return item;
    }

    private Terminado buildTerminado(String productoId, String nombre, int loteSize, String prefijoLote) {
        Categoria categoria = new Categoria();
        categoria.setCategoriaId(productoId.startsWith("SH") ? 10 : 20);
        categoria.setCategoriaNombre(productoId.startsWith("SH") ? "Shampoo" : "Cremas");
        categoria.setLoteSize(loteSize);
        categoria.setTiempoDiasFabricacion(1);

        Terminado terminado = new Terminado();
        terminado.setProductoId(productoId);
        terminado.setNombre(nombre);
        terminado.setPrefijoLote(prefijoLote);
        terminado.setCategoria(categoria);
        return terminado;
    }

    private record TestContext(
            ProgramacionProduccionSemanalService service,
            TerminadoRepo terminadoRepo,
            MasterProductionScheduleSemanalRepo mpsRepo
    ) {}
}
