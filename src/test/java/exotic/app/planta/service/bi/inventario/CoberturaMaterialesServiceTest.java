package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.FuenteDemandaCobertura;
import exotic.app.planta.model.inventarios.CausaAjusteInventario;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoberturaMaterialesServiceTest {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T15:00:00Z"),
            BOGOTA);

    @Test
    void estimatesCoverageAndMarksSparseShortHistoryAsLowConfidence() {
        LocalDate cutoffDate = LocalDate.of(2026, 7, 18);
        LocalDate startDate = cutoffDate.minusDays(6);
        Material material = material("MP-1", "Aceite", "KG");
        Movimiento dispensation = dispensation(
                material,
                -7,
                cutoffDate.atTime(8, 0));

        TransaccionAlmacenRepo movementRepo = mock(TransaccionAlmacenRepo.class);
        InventarioStockReader stockReader = mock(InventarioStockReader.class);
        when(stockReader.readGeneralStock()).thenReturn(List.of(
                new ProductoStockSnapshot(material, 14)));
        when(movementRepo.findMovimientosBiByAlmacenAndRango(
                Movimiento.Almacen.GENERAL,
                startDate.atStartOfDay(),
                cutoffDate.atTime(LocalTime.MAX)))
                .thenReturn(List.of(dispensation));

        var service = new CoberturaMaterialesService(
                movementRepo,
                stockReader,
                new BootstrapDemandIntervalCalculator(),
                CLOCK);
        var report = service.calculate(7);

        assertEquals("MP-1", report.materialCriticoId());
        assertEquals(cutoffDate.plusDays(14), report.fechaPrimerAgotamiento());
        assertTrue(report.confianzaBaja());
        assertTrue(report.motivosConfianzaBaja().stream()
                .anyMatch(reason -> reason.contains("menos de 30")));
    }

    @Test
    void expandedSourceIncludesOnlyClassifiedProductionContingencies() {
        LocalDate cutoffDate = LocalDate.of(2026, 7, 18);
        LocalDate startDate = cutoffDate.minusDays(29);
        Material material = material("MP-2", "Extracto", "KG");
        Movimiento dispensation = movement(
                material,
                -30,
                cutoffDate.minusDays(3).atTime(8, 0),
                Movimiento.TipoMovimiento.DISPENSACION,
                null);
        Movimiento contingency = movement(
                material,
                -30,
                cutoffDate.minusDays(2).atTime(8, 0),
                Movimiento.TipoMovimiento.AJUSTE_NEGATIVO,
                CausaAjusteInventario.PRODUCCION_CONTINGENCIA);
        Movimiento physicalCount = movement(
                material,
                -30,
                cutoffDate.minusDays(1).atTime(8, 0),
                Movimiento.TipoMovimiento.AJUSTE_NEGATIVO,
                CausaAjusteInventario.DIFERENCIA_CONTEO);
        Movimiento unclassified = movement(
                material,
                -30,
                cutoffDate.atTime(8, 0),
                Movimiento.TipoMovimiento.AJUSTE_NEGATIVO,
                null);

        TransaccionAlmacenRepo movementRepo = mock(TransaccionAlmacenRepo.class);
        InventarioStockReader stockReader = mock(InventarioStockReader.class);
        when(stockReader.readGeneralStock()).thenReturn(List.of(
                new ProductoStockSnapshot(material, 80)));
        when(movementRepo.findMovimientosBiByAlmacenAndRango(
                Movimiento.Almacen.GENERAL,
                startDate.atStartOfDay(),
                cutoffDate.atTime(LocalTime.MAX)))
                .thenReturn(List.of(
                        dispensation,
                        contingency,
                        physicalCount,
                        unclassified));

        var service = new CoberturaMaterialesService(
                movementRepo,
                stockReader,
                new BootstrapDemandIntervalCalculator(),
                CLOCK);

        var operative = service.calculate(
                30,
                FuenteDemandaCobertura.SOLO_DISPENSACIONES);
        var expanded = service.calculate(
                30,
                FuenteDemandaCobertura.DISPENSACIONES_MAS_CONTINGENCIAS);

        assertEquals(1.0, operative.estimaciones().get(0).demandaMediaDiaria(), 0.0001);
        assertEquals(80.0, operative.estimaciones().get(0).diasHastaAgotamiento(), 0.0001);
        assertEquals(2.0, expanded.estimaciones().get(0).demandaMediaDiaria(), 0.0001);
        assertEquals(40.0, expanded.estimaciones().get(0).diasHastaAgotamiento(), 0.0001);
        assertEquals(1, expanded.resumenFuentesDemanda().ajustesContingenciaIncluidos());
        assertEquals(1, expanded.resumenFuentesDemanda()
                .ajustesNegativosSinClasificarExcluidos());
        assertTrue(expanded.escenarioExploratorio());
    }

    private Material material(String id, String name, String unit) {
        Material material = new Material();
        material.setProductoId(id);
        material.setNombre(name);
        material.setTipoMaterial(1);
        material.setTipoUnidades(unit);
        return material;
    }

    private Movimiento dispensation(
            Material material,
            double quantity,
            LocalDateTime date
    ) {
        return movement(
                material,
                quantity,
                date,
                Movimiento.TipoMovimiento.DISPENSACION,
                null);
    }

    private Movimiento movement(
            Material material,
            double quantity,
            LocalDateTime date,
            Movimiento.TipoMovimiento movementType,
            CausaAjusteInventario adjustmentCause
    ) {
        Movimiento movement = new Movimiento();
        movement.setProducto(material);
        movement.setCantidad(quantity);
        movement.setFechaMovimiento(date);
        movement.setTipoMovimiento(movementType);
        movement.setAlmacen(Movimiento.Almacen.GENERAL);
        if (movementType == Movimiento.TipoMovimiento.AJUSTE_NEGATIVO) {
            TransaccionAlmacen transaction = new TransaccionAlmacen();
            transaction.setTipoEntidadCausante(
                    TransaccionAlmacen.TipoEntidadCausante.OAA);
            transaction.setCausaAjuste(adjustmentCause);
            movement.setTransaccionAlmacen(transaction);
        }
        return movement;
    }
}
