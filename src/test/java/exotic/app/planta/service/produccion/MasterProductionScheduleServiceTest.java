package exotic.app.planta.service.produccion;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.PoolCapacidad;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalItemRequestDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalResponseDTO;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterProductionScheduleServiceTest {

    @Test
    void calcularPropuestaSemanal_sharedPool_groupsIntoSingleCapacityRow() {
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        TransaccionAlmacenRepo stockRepo = mock(TransaccionAlmacenRepo.class);
        MasterProductionScheduleService service = new MasterProductionScheduleService(terminadoRepo, stockRepo);

        PoolCapacidad pool = new PoolCapacidad(7, "Pool Neutro", 120, null, true);
        Categoria shampoo = buildCategoria(11, "Shampoo", 10, 1, 50, pool);
        Categoria tratamiento = buildCategoria(12, "Tratamiento", 10, 1, 40, pool);
        Terminado t1 = buildTerminado("SH-001", "Shampoo Coco", shampoo);
        Terminado t2 = buildTerminado("TR-001", "Tratamiento Coco", tratamiento);

        when(terminadoRepo.findByProductoIdIn(List.of("SH-001", "TR-001"))).thenReturn(List.of(t1, t2));
        when(stockRepo.findTotalCantidadByProductoId("SH-001")).thenReturn(0.0);
        when(stockRepo.findTotalCantidadByProductoId("TR-001")).thenReturn(0.0);

        PropuestaMpsSemanalResponseDTO response = service.calcularPropuestaSemanal(buildRequest(List.of(
                buildRequestItem("SH-001", 100),
                buildRequestItem("TR-001", 50)
        )));

        assertEquals(1, response.getCalendar().getRows().size());
        var row = response.getCalendar().getRows().getFirst();
        assertEquals("pool::7", row.getRowKey());
        assertNull(row.getCategoriaId());
        assertNull(row.getCategoriaNombre());
        assertEquals(7, row.getPoolCapacidadId());
        assertEquals("Pool Neutro", row.getPoolCapacidadNombre());
        assertEquals(120, row.getCapacidadDiaria());
        assertEquals(120.0, row.getDays().getFirst().getTotalAsignado());
        assertEquals(30.0, row.getDays().get(1).getTotalAsignado());
        assertEquals(150.0, row.getTotalAsignadoSemana());
        assertEquals(2, row.getDays().getFirst().getBlocks().size());
        assertNotNull(row.getDays().getFirst().getBlocks().stream()
                .filter(block -> "SH-001".equals(block.getProductoId()))
                .filter(block -> Integer.valueOf(11).equals(block.getCategoriaId()))
                .filter(block -> "Shampoo".equals(block.getCategoriaNombre()))
                .filter(block -> Integer.valueOf(7).equals(block.getPoolCapacidadId()))
                .findFirst()
                .orElse(null));
    }

    @Test
    void calcularPropuestaSemanal_withoutPool_usesLegacyCategoryCapacity() {
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        TransaccionAlmacenRepo stockRepo = mock(TransaccionAlmacenRepo.class);
        MasterProductionScheduleService service = new MasterProductionScheduleService(terminadoRepo, stockRepo);

        Categoria categoria = buildCategoria(21, "Ampollas", 10, 1, 30, null);
        Terminado terminado = buildTerminado("AM-001", "Ampollas Capilar", categoria);

        when(terminadoRepo.findByProductoIdIn(List.of("AM-001"))).thenReturn(List.of(terminado));
        when(stockRepo.findTotalCantidadByProductoId("AM-001")).thenReturn(0.0);

        PropuestaMpsSemanalResponseDTO response = service.calcularPropuestaSemanal(buildRequest(List.of(
                buildRequestItem("AM-001", 40)
        )));

        assertEquals(1, response.getCalendar().getRows().size());
        var row = response.getCalendar().getRows().getFirst();
        assertEquals("categoria::21", row.getRowKey());
        assertEquals(21, row.getCategoriaId());
        assertEquals("Ampollas", row.getCategoriaNombre());
        assertNull(row.getPoolCapacidadId());
        assertEquals(30, row.getCapacidadDiaria());
    }

    @Test
    void calcularPropuestaSemanal_mixedPoolsAndLegacy_createsSeparateRows() {
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        TransaccionAlmacenRepo stockRepo = mock(TransaccionAlmacenRepo.class);
        MasterProductionScheduleService service = new MasterProductionScheduleService(terminadoRepo, stockRepo);

        PoolCapacidad pool = new PoolCapacidad(8, "Pool Mezcla", 100, null, true);
        Categoria conPool = buildCategoria(31, "Shampoo", 10, 1, 0, pool);
        Categoria legacy = buildCategoria(32, "Capsulas", 10, 1, 50, null);
        Terminado t1 = buildTerminado("SH-100", "Shampoo", conPool);
        Terminado t2 = buildTerminado("CA-100", "Capsulas", legacy);

        when(terminadoRepo.findByProductoIdIn(List.of("SH-100", "CA-100"))).thenReturn(List.of(t1, t2));
        when(stockRepo.findTotalCantidadByProductoId("SH-100")).thenReturn(0.0);
        when(stockRepo.findTotalCantidadByProductoId("CA-100")).thenReturn(0.0);

        PropuestaMpsSemanalResponseDTO response = service.calcularPropuestaSemanal(buildRequest(List.of(
                buildRequestItem("SH-100", 20),
                buildRequestItem("CA-100", 20)
        )));

        assertEquals(2, response.getCalendar().getRows().size());
        assertNotNull(response.getCalendar().getRows().stream().filter(row -> row.getPoolCapacidadId() != null).findFirst().orElse(null));
        assertNotNull(response.getCalendar().getRows().stream().filter(row -> row.getPoolCapacidadId() == null).findFirst().orElse(null));
        assertNotNull(response.getCalendar().getRows().stream().filter(row -> "pool::8".equals(row.getRowKey())).findFirst().orElse(null));
        assertNotNull(response.getCalendar().getRows().stream().filter(row -> "categoria::32".equals(row.getRowKey())).findFirst().orElse(null));
    }

    @Test
    void calcularPropuestaSemanal_poolCapacityZero_marksAsUnscheduled() {
        TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
        TransaccionAlmacenRepo stockRepo = mock(TransaccionAlmacenRepo.class);
        MasterProductionScheduleService service = new MasterProductionScheduleService(terminadoRepo, stockRepo);

        PoolCapacidad pool = new PoolCapacidad(9, "Pool Sin Capacidad", 0, null, true);
        Categoria categoria = buildCategoria(41, "Tratamiento", 10, 1, 99, pool);
        Terminado terminado = buildTerminado("TR-900", "Tratamiento Base", categoria);

        when(terminadoRepo.findByProductoIdIn(List.of("TR-900"))).thenReturn(List.of(terminado));
        when(stockRepo.findTotalCantidadByProductoId("TR-900")).thenReturn(0.0);

        PropuestaMpsSemanalResponseDTO response = service.calcularPropuestaSemanal(buildRequest(List.of(
                buildRequestItem("TR-900", 20)
        )));

        assertEquals(1, response.getCalendar().getRows().size());
        assertEquals("sin_configurar", response.getCalendar().getRows().getFirst().getEstadoSemana());
        assertEquals(1, response.getCalendar().getUnscheduled().size());
        assertEquals("Sin capacidad configurada", response.getCalendar().getUnscheduled().getFirst().getReason());
        assertEquals(41, response.getCalendar().getUnscheduled().getFirst().getCategoriaId());
        assertEquals("Tratamiento", response.getCalendar().getUnscheduled().getFirst().getCategoriaNombre());
        assertEquals(9, response.getCalendar().getUnscheduled().getFirst().getPoolCapacidadId());
    }

    private PropuestaMpsSemanalRequestDTO buildRequest(List<PropuestaMpsSemanalItemRequestDTO> items) {
        PropuestaMpsSemanalRequestDTO request = new PropuestaMpsSemanalRequestDTO();
        request.setWeekStartDate(LocalDate.of(2026, 5, 4));
        request.setItems(items);
        return request;
    }

    private PropuestaMpsSemanalItemRequestDTO buildRequestItem(String productoId, double necesidadManual) {
        PropuestaMpsSemanalItemRequestDTO item = new PropuestaMpsSemanalItemRequestDTO();
        item.setProductoId(productoId);
        item.setNecesidadManual(necesidadManual);
        item.setCantidadVendida(necesidadManual);
        item.setValorTotal(necesidadManual * 1000);
        item.setPorcentajeParticipacion(50);
        return item;
    }

    private Categoria buildCategoria(int id, String nombre, int loteSize, int tiempoDiasFabricacion, int capacidad, PoolCapacidad poolCapacidad) {
        Categoria categoria = new Categoria();
        categoria.setCategoriaId(id);
        categoria.setCategoriaNombre(nombre);
        categoria.setLoteSize(loteSize);
        categoria.setTiempoDiasFabricacion(tiempoDiasFabricacion);
        categoria.setCapacidadProductivaDiaria(capacidad);
        categoria.setPoolCapacidad(poolCapacidad);
        return categoria;
    }

    private Terminado buildTerminado(String productoId, String nombre, Categoria categoria) {
        Terminado terminado = new Terminado();
        terminado.setProductoId(productoId);
        terminado.setNombre(nombre);
        terminado.setCategoria(categoria);
        return terminado;
    }
}
