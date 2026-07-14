package exotic.app.planta.service.bi;

import exotic.app.planta.model.bi.dto.InformeGlobalAlmacenDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.MpsSemanalDiaRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class InformesGlobalesServiceTest {

    private TransaccionAlmacenRepo transaccionAlmacenRepo;
    private InformesGlobalesService service;

    @BeforeEach
    void setUp() {
        transaccionAlmacenRepo = Mockito.mock(TransaccionAlmacenRepo.class);
        service = new InformesGlobalesService(
                transaccionAlmacenRepo,
                Mockito.mock(MasterProductionScheduleSemanalRepo.class),
                Mockito.mock(MpsSemanalDiaRepo.class),
                Mockito.mock(CategoriaRepo.class));
    }

    @Test
    void obtenerReporteAlmacen_agregaValoresSinMezclarUnidadesYReportaCobertura() {
        Material materiaPrima = material("MP-1", "Aceite", 1, "KG", 1_000);
        Material empaqueSinCosto = material("ME-1", "Caja", 2, "U", 0);
        Movimiento ingresoMateriaPrima = movimiento(
                10, LocalDateTime.of(2026, 6, 10, 8, 0), materiaPrima, Movimiento.TipoMovimiento.COMPRA);
        Movimiento ingresoTransferencia = movimiento(
                20, LocalDateTime.of(2026, 6, 11, 9, 0), empaqueSinCosto, Movimiento.TipoMovimiento.TRANSFERENCIA);
        Movimiento dispensacion = movimiento(
                -4, LocalDateTime.of(2026, 6, 11, 10, 0), materiaPrima, Movimiento.TipoMovimiento.DISPENSACION);

        when(transaccionAlmacenRepo.findIngresosMaterialPorDia(
                any(LocalDateTime.class), any(LocalDateTime.class), anyCollection()))
                .thenReturn(List.of(ingresoMateriaPrima, ingresoTransferencia));
        when(transaccionAlmacenRepo.findDispensacionesMaterialPorDia(
                any(LocalDateTime.class), any(LocalDateTime.class), eq(Movimiento.TipoMovimiento.DISPENSACION)))
                .thenReturn(List.of(dispensacion));

        InformeGlobalAlmacenDTO reporte = service.obtenerReporteAlmacen(
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11));

        assertEquals(10_000, reporte.getResumen().getValorIngresosEstimado(), 0.000001);
        assertEquals(4_000, reporte.getResumen().getValorDispensacionesEstimado(), 0.000001);
        assertEquals(6_000, reporte.getResumen().getBalanceValorEstimado(), 0.000001);
        assertEquals(2, reporte.getResumen().getMovimientosIngreso());
        assertEquals(1, reporte.getResumen().getMovimientosDispensacion());
        assertEquals(2, reporte.getResumen().getMaterialesIngresados());
        assertEquals(1, reporte.getResumen().getMaterialesDispensados());
        assertEquals(1, reporte.getResumen().getMaterialesConCosto());
        assertEquals(1, reporte.getResumen().getMaterialesSinCosto());
        assertEquals(50, reporte.getResumen().getCoberturaCostosPct(), 0.000001);

        InformeGlobalAlmacenDTO.CantidadUnidadDTO kilogramos = cantidadPorUnidad(reporte, "KG");
        assertEquals(10, kilogramos.getCantidadIngresada(), 0.000001);
        assertEquals(4, kilogramos.getCantidadDispensada(), 0.000001);
        assertEquals(6, kilogramos.getBalanceNeto(), 0.000001);
        assertEquals(20, cantidadPorUnidad(reporte, "U").getCantidadIngresada(), 0.000001);

        assertEquals(2, reporte.getSerieDiaria().size());
        assertEquals(10_000, reporte.getSerieDiaria().get(0).getValorIngresosEstimado(), 0.000001);
        assertEquals(4_000, reporte.getSerieDiaria().get(1).getValorDispensacionesEstimado(), 0.000001);
        assertEquals(2, reporte.getConsolidadoTipoMaterial().size());
        assertEquals("MP-1", reporte.getTopMateriales().get(0).getProductoId());
        assertTrue(reporte.getNotas().stream().anyMatch((nota) -> nota.getMensaje().contains("sin costo valido")));
        assertTrue(reporte.getNotas().stream().anyMatch((nota) -> nota.getMensaje().contains("transferencias positivas")));
        assertTrue(reporte.getNotas().stream().anyMatch((nota) -> nota.getMensaje().contains("unidad de medida")));
    }

    @Test
    void obtenerReporteAlmacen_sinMovimientosConservaTodosLosDiasDeLaSerie() {
        when(transaccionAlmacenRepo.findIngresosMaterialPorDia(
                any(LocalDateTime.class), any(LocalDateTime.class), anyCollection()))
                .thenReturn(List.of());
        when(transaccionAlmacenRepo.findDispensacionesMaterialPorDia(
                any(LocalDateTime.class), any(LocalDateTime.class), eq(Movimiento.TipoMovimiento.DISPENSACION)))
                .thenReturn(List.of());

        InformeGlobalAlmacenDTO reporte = service.obtenerReporteAlmacen(
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 12));

        assertEquals(3, reporte.getSerieDiaria().size());
        assertEquals(LocalDate.of(2026, 6, 10), reporte.getSerieDiaria().get(0).getFecha());
        assertEquals(LocalDate.of(2026, 6, 12), reporte.getSerieDiaria().get(2).getFecha());
        assertTrue(reporte.getNotas().stream().anyMatch((nota) -> nota.getMensaje().contains("No se registraron")));
    }

    @Test
    void obtenerReporteAlmacen_rechazaRangoInvertido() {
        assertThrows(IllegalArgumentException.class, () -> service.obtenerReporteAlmacen(
                LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 10)));
    }

    private static Material material(String id, String nombre, int tipo, String unidad, double costo) {
        Material material = new Material();
        material.setProductoId(id);
        material.setNombre(nombre);
        material.setTipoMaterial(tipo);
        material.setTipoUnidades(unidad);
        material.setCosto(costo);
        return material;
    }

    private static Movimiento movimiento(
            double cantidad,
            LocalDateTime fecha,
            Material material,
            Movimiento.TipoMovimiento tipoMovimiento) {
        Movimiento movimiento = new Movimiento();
        movimiento.setCantidad(cantidad);
        movimiento.setFechaMovimiento(fecha);
        movimiento.setProducto(material);
        movimiento.setTipoMovimiento(tipoMovimiento);
        movimiento.setAlmacen(Movimiento.Almacen.GENERAL);
        return movimiento;
    }

    private static InformeGlobalAlmacenDTO.CantidadUnidadDTO cantidadPorUnidad(
            InformeGlobalAlmacenDTO reporte,
            String unidad) {
        return reporte.getCantidadesPorUnidad().stream()
                .filter((cantidad) -> unidad.equals(cantidad.getUnidadMedida()))
                .findFirst()
                .orElseThrow();
    }
}
