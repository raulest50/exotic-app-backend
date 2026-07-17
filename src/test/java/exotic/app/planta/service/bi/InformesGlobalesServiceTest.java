package exotic.app.planta.service.bi;

import exotic.app.planta.model.bi.dto.InformeGlobalAlmacenDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
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
import java.util.ArrayList;
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
    void obtenerReporteAlmacen_separaRecepcionesOcmOtrosIngresosYUnidades() {
        Material materiaPrima = material("MP-1", "Aceite", 1, " kg ", 1_000);
        Material empaqueSinCosto = material("ME-1", "Caja", 2, "U", 0);
        Movimiento recepcionOcm = movimiento(
                10,
                LocalDateTime.of(2026, 6, 10, 8, 0),
                materiaPrima,
                Movimiento.TipoMovimiento.COMPRA,
                TransaccionAlmacen.TipoEntidadCausante.OCM);
        Movimiento transferencia = movimiento(
                20,
                LocalDateTime.of(2026, 6, 11, 9, 0),
                empaqueSinCosto,
                Movimiento.TipoMovimiento.TRANSFERENCIA,
                TransaccionAlmacen.TipoEntidadCausante.OTA);
        Movimiento compraSinOcm = movimiento(
                5,
                LocalDateTime.of(2026, 6, 11, 9, 30),
                empaqueSinCosto,
                Movimiento.TipoMovimiento.COMPRA,
                TransaccionAlmacen.TipoEntidadCausante.OP);
        Movimiento dispensacion = movimiento(
                -4,
                LocalDateTime.of(2026, 6, 11, 10, 0),
                materiaPrima,
                Movimiento.TipoMovimiento.DISPENSACION,
                TransaccionAlmacen.TipoEntidadCausante.OP);

        when(transaccionAlmacenRepo.findIngresosMaterialPorDia(
                any(LocalDateTime.class), any(LocalDateTime.class), anyCollection()))
                .thenReturn(List.of(recepcionOcm, transferencia, compraSinOcm));
        when(transaccionAlmacenRepo.findDispensacionesMaterialPorDia(
                any(LocalDateTime.class), any(LocalDateTime.class), eq(Movimiento.TipoMovimiento.DISPENSACION)))
                .thenReturn(List.of(dispensacion));

        InformeGlobalAlmacenDTO reporte = service.obtenerReporteAlmacen(
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11));

        assertEquals(4_000, reporte.getResumen().getValorDispensacionesEstimado(), 0.000001);
        assertEquals(10_000, reporte.getResumen().getValorRecepcionesCompraEstimado(), 0.000001);
        assertEquals(0, reporte.getResumen().getValorOtrosIngresosEstimado(), 0.000001);
        assertEquals(1, reporte.getResumen().getMovimientosDispensacion());
        assertEquals(1, reporte.getResumen().getMovimientosRecepcionCompra());
        assertEquals(2, reporte.getResumen().getMovimientosOtrosIngresos());
        assertEquals(1, reporte.getResumen().getMaterialesDispensados());
        assertEquals(1, reporte.getResumen().getMaterialesRecibidosCompra());
        assertEquals(1, reporte.getResumen().getMaterialesOtrosIngresos());
        assertEquals(1, reporte.getResumen().getMaterialesConCosto());
        assertEquals(1, reporte.getResumen().getMaterialesSinCosto());
        assertEquals(50, reporte.getResumen().getCoberturaCostosPct(), 0.000001);

        InformeGlobalAlmacenDTO.ResumenUnidadDTO kilogramos = resumenPorUnidad(reporte, "KG");
        assertEquals(4, kilogramos.getCantidadDispensada(), 0.000001);
        assertEquals(10, kilogramos.getCantidadRecibidaCompra(), 0.000001);
        assertEquals(0, kilogramos.getCantidadOtrosIngresos(), 0.000001);
        assertEquals(25, resumenPorUnidad(reporte, "U").getCantidadOtrosIngresos(), 0.000001);

        assertEquals(4, reporte.getSerieFisicaDiaria().size());
        assertEquals(10, serie(reporte, LocalDate.of(2026, 6, 10), "KG").getCantidadRecibidaCompra(), 0.000001);
        assertEquals(0, serie(reporte, LocalDate.of(2026, 6, 10), "KG").getCantidadDispensada(), 0.000001);
        assertEquals(4, serie(reporte, LocalDate.of(2026, 6, 11), "KG").getCantidadDispensada(), 0.000001);
        assertEquals(0, serie(reporte, LocalDate.of(2026, 6, 11), "U").getCantidadRecibidaCompra(), 0.000001);

        InformeGlobalAlmacenDTO.RankingUnidadDTO rankingKg = ranking(reporte, "KG");
        assertEquals(4, rankingKg.getCantidadTotal(), 0.000001);
        assertEquals(1, rankingKg.getMaterialesTotales());
        assertEquals("MP-1", rankingKg.getMateriales().get(0).getProductoId());
        assertEquals(100, rankingKg.getMateriales().get(0).getParticipacionPct(), 0.000001);
        assertEquals(1, rankingKg.getMateriales().get(0).getMovimientos());

        assertTrue(reporte.getNotas().stream().anyMatch((nota) -> nota.getMensaje().contains("sin costo valido")));
        assertTrue(reporte.getNotas().stream().anyMatch((nota) -> nota.getMensaje().contains("compras sin causa OCM")));
        assertTrue(reporte.getNotas().stream().anyMatch((nota) -> nota.getMensaje().contains("unidad de medida")));
        assertTrue(reporte.getNotas().stream().anyMatch((nota) -> nota.getMensaje().contains("costo maestro actual")));
    }

    @Test
    void obtenerReporteAlmacen_limitaRankingYConsolidaOtros() {
        List<Movimiento> dispensaciones = new ArrayList<>();
        for (int indice = 1; indice <= 12; indice++) {
            Material material = material(
                    "MP-%02d".formatted(indice),
                    "Material %02d".formatted(indice),
                    1,
                    "KG",
                    100);
            dispensaciones.add(movimiento(
                    -(13 - indice),
                    LocalDateTime.of(2026, 6, 10, 8, indice),
                    material,
                    Movimiento.TipoMovimiento.DISPENSACION,
                    TransaccionAlmacen.TipoEntidadCausante.OP));
        }

        when(transaccionAlmacenRepo.findIngresosMaterialPorDia(
                any(LocalDateTime.class), any(LocalDateTime.class), anyCollection()))
                .thenReturn(List.of());
        when(transaccionAlmacenRepo.findDispensacionesMaterialPorDia(
                any(LocalDateTime.class), any(LocalDateTime.class), eq(Movimiento.TipoMovimiento.DISPENSACION)))
                .thenReturn(dispensaciones);

        InformeGlobalAlmacenDTO reporte = service.obtenerReporteAlmacen(
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10));

        InformeGlobalAlmacenDTO.RankingUnidadDTO ranking = ranking(reporte, "KG");
        assertEquals(78, ranking.getCantidadTotal(), 0.000001);
        assertEquals(12, ranking.getMaterialesTotales());
        assertEquals(10, ranking.getMateriales().size());
        assertEquals("MP-01", ranking.getMateriales().get(0).getProductoId());
        assertEquals("MP-10", ranking.getMateriales().get(9).getProductoId());
        assertEquals(3, ranking.getCantidadOtros(), 0.000001);
        assertEquals(2, ranking.getMaterialesOtros());
        assertEquals(12d * 100d / 78d, ranking.getMateriales().get(0).getParticipacionPct(), 0.000001);
    }

    @Test
    void obtenerReporteAlmacen_sinMovimientosDevuelveColeccionesVacias() {
        when(transaccionAlmacenRepo.findIngresosMaterialPorDia(
                any(LocalDateTime.class), any(LocalDateTime.class), anyCollection()))
                .thenReturn(List.of());
        when(transaccionAlmacenRepo.findDispensacionesMaterialPorDia(
                any(LocalDateTime.class), any(LocalDateTime.class), eq(Movimiento.TipoMovimiento.DISPENSACION)))
                .thenReturn(List.of());

        InformeGlobalAlmacenDTO reporte = service.obtenerReporteAlmacen(
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 12));

        assertTrue(reporte.getResumenPorUnidad().isEmpty());
        assertTrue(reporte.getRankingDispensacion().isEmpty());
        assertTrue(reporte.getSerieFisicaDiaria().isEmpty());
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
            Movimiento.TipoMovimiento tipoMovimiento,
            TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante) {
        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setTipoEntidadCausante(tipoEntidadCausante);
        Movimiento movimiento = new Movimiento();
        movimiento.setCantidad(cantidad);
        movimiento.setFechaMovimiento(fecha);
        movimiento.setProducto(material);
        movimiento.setTipoMovimiento(tipoMovimiento);
        movimiento.setTransaccionAlmacen(transaccion);
        movimiento.setAlmacen(Movimiento.Almacen.GENERAL);
        return movimiento;
    }

    private static InformeGlobalAlmacenDTO.ResumenUnidadDTO resumenPorUnidad(
            InformeGlobalAlmacenDTO reporte,
            String unidad) {
        return reporte.getResumenPorUnidad().stream()
                .filter((cantidad) -> unidad.equals(cantidad.getUnidadMedida()))
                .findFirst()
                .orElseThrow();
    }

    private static InformeGlobalAlmacenDTO.RankingUnidadDTO ranking(
            InformeGlobalAlmacenDTO reporte,
            String unidad) {
        return reporte.getRankingDispensacion().stream()
                .filter((item) -> unidad.equals(item.getUnidadMedida()))
                .findFirst()
                .orElseThrow();
    }

    private static InformeGlobalAlmacenDTO.SerieFisicaDiariaDTO serie(
            InformeGlobalAlmacenDTO reporte,
            LocalDate fecha,
            String unidad) {
        return reporte.getSerieFisicaDiaria().stream()
                .filter((item) -> fecha.equals(item.getFecha()) && unidad.equals(item.getUnidadMedida()))
                .findFirst()
                .orElseThrow();
    }
}
