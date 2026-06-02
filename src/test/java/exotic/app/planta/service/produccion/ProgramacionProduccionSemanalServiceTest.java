package exotic.app.planta.service.produccion;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.PoolCapacidad;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.dto.GuardarMpsSemanalDraftRequestDTO;
import exotic.app.planta.model.produccion.dto.GuardarProgramacionProduccionSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.produccion.dto.ProgramacionProduccionSemanalItemRequestDTO;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgramacionProduccionSemanalServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 1);

    @Test
    void guardarBorradorDirecto_rejectsNonMondayWeekStartDate() {
        ProgramacionProduccionSemanalService service = buildService();
        GuardarProgramacionProduccionSemanalRequestDTO request = buildRequest(
                LocalDate.of(2026, 6, 2),
                List.of(entry(MONDAY, "T-001", 20))
        );

        assertThrows(IllegalArgumentException.class, () -> service.guardarBorradorDirecto(request));
    }

    @Test
    void guardarBorradorDirecto_rejectsDateOutsideMondayToSaturdayWeek() {
        ProgramacionProduccionSemanalService service = buildService();
        GuardarProgramacionProduccionSemanalRequestDTO request = buildRequest(
                MONDAY,
                List.of(entry(MONDAY.plusDays(6), "T-001", 20))
        );

        assertThrows(IllegalArgumentException.class, () -> service.guardarBorradorDirecto(request));
    }

    @Test
    void guardarBorradorDirecto_rejectsUnitsThatDoNotDivideExactlyByLoteSize() {
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        TransaccionAlmacenRepo stockRepo = mock(TransaccionAlmacenRepo.class);
        MasterProductionScheduleDraftService draftService = mock(MasterProductionScheduleDraftService.class);
        ProgramacionProduccionSemanalService service = new ProgramacionProduccionSemanalService(terminadoRepo, stockRepo, draftService);

        when(terminadoRepo.findByProductoIdIn(anyCollection())).thenReturn(List.of(buildTerminado("T-001", "Producto", 12, "PRD")));

        GuardarProgramacionProduccionSemanalRequestDTO request = buildRequest(
                MONDAY,
                List.of(entry(MONDAY, "T-001", 25))
        );

        assertThrows(IllegalArgumentException.class, () -> service.guardarBorradorDirecto(request));
    }

    @Test
    void guardarBorradorDirecto_rejectsTerminadoWithoutLoteSize() {
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        TransaccionAlmacenRepo stockRepo = mock(TransaccionAlmacenRepo.class);
        MasterProductionScheduleDraftService draftService = mock(MasterProductionScheduleDraftService.class);
        ProgramacionProduccionSemanalService service = new ProgramacionProduccionSemanalService(terminadoRepo, stockRepo, draftService);

        when(terminadoRepo.findByProductoIdIn(anyCollection())).thenReturn(List.of(buildTerminado("T-001", "Producto", 0, "PRD")));

        GuardarProgramacionProduccionSemanalRequestDTO request = buildRequest(
                MONDAY,
                List.of(entry(MONDAY, "T-001", 20))
        );

        assertThrows(IllegalArgumentException.class, () -> service.guardarBorradorDirecto(request));
    }

    @Test
    void guardarBorradorDirecto_rejectsTerminadoWithoutPrefijoLote() {
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        TransaccionAlmacenRepo stockRepo = mock(TransaccionAlmacenRepo.class);
        MasterProductionScheduleDraftService draftService = mock(MasterProductionScheduleDraftService.class);
        ProgramacionProduccionSemanalService service = new ProgramacionProduccionSemanalService(terminadoRepo, stockRepo, draftService);

        when(terminadoRepo.findByProductoIdIn(anyCollection())).thenReturn(List.of(buildTerminado("T-001", "Producto", 10, "")));

        GuardarProgramacionProduccionSemanalRequestDTO request = buildRequest(
                MONDAY,
                List.of(entry(MONDAY, "T-001", 20))
        );

        assertThrows(IllegalArgumentException.class, () -> service.guardarBorradorDirecto(request));
    }

    @Test
    void guardarBorradorDirecto_buildsMpsSnapshotForMultipleProductsAndDays() {
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        TransaccionAlmacenRepo stockRepo = mock(TransaccionAlmacenRepo.class);
        MasterProductionScheduleDraftService draftService = mock(MasterProductionScheduleDraftService.class);
        ProgramacionProduccionSemanalService service = new ProgramacionProduccionSemanalService(terminadoRepo, stockRepo, draftService);

        Terminado shampoo = buildTerminado("SH-001", "Shampoo", 10, "SHP");
        Terminado crema = buildTerminado("CR-001", "Crema", 5, "CRM");
        when(terminadoRepo.findByProductoIdIn(anyCollection())).thenReturn(List.of(shampoo, crema));
        when(stockRepo.findTotalCantidadByProductoId(anyString())).thenReturn(0.0);
        when(draftService.saveDraft(any(GuardarMpsSemanalDraftRequestDTO.class))).thenReturn(new MpsSemanalDraftDTO());

        GuardarProgramacionProduccionSemanalRequestDTO request = buildRequest(
                MONDAY,
                List.of(
                        entry(MONDAY, "SH-001", 20),
                        entry(MONDAY.plusDays(1), "SH-001", 10),
                        entry(MONDAY.plusDays(2), "CR-001", 15)
                )
        );

        service.guardarBorradorDirecto(request);

        ArgumentCaptor<GuardarMpsSemanalDraftRequestDTO> captor = ArgumentCaptor.forClass(GuardarMpsSemanalDraftRequestDTO.class);
        verify(draftService).saveDraft(captor.capture());
        GuardarMpsSemanalDraftRequestDTO draft = captor.getValue();

        assertEquals(MONDAY, draft.getWeekStartDate());
        assertEquals(2, draft.getSummary().getTotalTerminadosEvaluados());
        assertEquals(6, draft.getSummary().getTotalLotesPropuestos());
        assertEquals(45.0, draft.getSummary().getTotalUnidadesPropuestas());
        assertEquals(2, draft.getItems().size());
        assertEquals(2, draft.getCalendar().getRows().size());

        int totalExpectedOrders = draft.getCalendar().getRows().stream()
                .flatMap(row -> row.getDays().stream())
                .flatMap(day -> day.getBlocks().stream())
                .mapToInt(block -> Math.max(block.getLotesAsignados(), 0))
                .sum();
        assertEquals(6, totalExpectedOrders);
    }

    private ProgramacionProduccionSemanalService buildService() {
        return new ProgramacionProduccionSemanalService(
                mock(TerminadoRepo.class),
                mock(TransaccionAlmacenRepo.class),
                mock(MasterProductionScheduleDraftService.class)
        );
    }

    private GuardarProgramacionProduccionSemanalRequestDTO buildRequest(
            LocalDate weekStartDate,
            List<ProgramacionProduccionSemanalItemRequestDTO> entradas
    ) {
        GuardarProgramacionProduccionSemanalRequestDTO request = new GuardarProgramacionProduccionSemanalRequestDTO();
        request.setWeekStartDate(weekStartDate);
        request.setEntradas(entradas);
        return request;
    }

    private ProgramacionProduccionSemanalItemRequestDTO entry(LocalDate date, String productoId, double unidades) {
        ProgramacionProduccionSemanalItemRequestDTO entry = new ProgramacionProduccionSemanalItemRequestDTO();
        entry.setDate(date);
        entry.setProductoId(productoId);
        entry.setUnidades(unidades);
        return entry;
    }

    private Terminado buildTerminado(String productoId, String nombre, int loteSize, String prefijoLote) {
        PoolCapacidad pool = new PoolCapacidad();
        pool.setId(productoId.startsWith("SH") ? 1 : 2);
        pool.setNombre(productoId.startsWith("SH") ? "Pool Shampoo" : "Pool Cremas");
        pool.setCapacidadDiaria(100);

        Categoria categoria = new Categoria();
        categoria.setCategoriaId(productoId.startsWith("SH") ? 10 : 20);
        categoria.setCategoriaNombre(productoId.startsWith("SH") ? "Shampoo" : "Cremas");
        categoria.setLoteSize(loteSize);
        categoria.setTiempoDiasFabricacion(1);
        categoria.setCapacidadProductivaDiaria(100);
        categoria.setPoolCapacidad(pool);

        Terminado terminado = new Terminado();
        terminado.setProductoId(productoId);
        terminado.setNombre(nombre);
        terminado.setPrefijoLote(prefijoLote);
        terminado.setCategoria(categoria);
        return terminado;
    }
}
