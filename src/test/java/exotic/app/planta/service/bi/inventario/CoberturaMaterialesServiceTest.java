package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.FuenteDemandaCobertura;
import exotic.app.planta.model.inventarios.CausaAjusteInventario;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @Test
    void keepsPriorityAtTenAndPaginatesEveryEstimableMaterial() {
        List<ProductoStockSnapshot> snapshots = new ArrayList<>();
        List<Movimiento> movements = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            Material material = material(
                    "MP-" + index,
                    "Material " + index,
                    "KG",
                    1);
            snapshots.add(new ProductoStockSnapshot(material, index));
            movements.add(dispensation(
                    material,
                    -30,
                    LocalDate.of(2026, 7, 18).atTime(8, index)));
        }
        Material withoutDemand = material(
                "MP-SIN-DEMANDA",
                "Material sin demanda",
                "KG",
                1);
        snapshots.add(new ProductoStockSnapshot(withoutDemand, 50));

        var service = serviceFor(30, snapshots, movements);
        var report = service.calculate(
                30,
                FuenteDemandaCobertura.SOLO_DISPENSACIONES,
                "TODOS",
                "TODOS",
                null,
                "AGOTAMIENTO",
                null,
                8,
                10);

        assertEquals(10, report.estimaciones().size());
        assertEquals(12, report.materialesConDemanda());
        assertEquals(12, report.pagina().totalElements());
        assertEquals(2, report.pagina().totalPages());
        assertEquals(1, report.pagina().page());
        assertEquals(2, report.pagina().items().size());
        assertEquals(List.of("MATERIA_PRIMA"), report.facetas().gruposDisponibles());
        assertEquals(List.of("KG"), report.facetas().unidadesDisponibles());
    }

    @Test
    void filtersByHorizonGroupUnitAndSearch() {
        Material exhaustedRaw = material(
                "MP-AGOTADO",
                "Extracto agotado",
                "KG",
                1);
        Material nearPackaging = material(
                "ME-ENVASE",
                "Envase cercano",
                "U",
                2);
        Material mediumPackaging = material(
                "ME-TAPA",
                "Tapa media",
                "U",
                2);
        List<ProductoStockSnapshot> snapshots = List.of(
                new ProductoStockSnapshot(exhaustedRaw, 0),
                new ProductoStockSnapshot(nearPackaging, 5),
                new ProductoStockSnapshot(mediumPackaging, 20));
        List<Movimiento> movements = List.of(
                dispensation(
                        exhaustedRaw,
                        -30,
                        LocalDate.of(2026, 7, 18).atTime(8, 0)),
                dispensation(
                        nearPackaging,
                        -30,
                        LocalDate.of(2026, 7, 18).atTime(9, 0)),
                dispensation(
                        mediumPackaging,
                        -30,
                        LocalDate.of(2026, 7, 18).atTime(10, 0)));

        var report = serviceFor(30, snapshots, movements).calculate(
                30,
                FuenteDemandaCobertura.SOLO_DISPENSACIONES,
                "HASTA_7_DIAS",
                "EMPAQUE",
                "U",
                "MAYOR_DEMANDA",
                "envase",
                0,
                10);

        assertEquals(1, report.pagina().totalElements());
        assertEquals("ME-ENVASE", report.pagina().items().get(0).productoId());
        assertEquals("EMPAQUE", report.pagina().items().get(0).grupo());
        assertTrue(report.pagina().items().get(0).confianzaBaja());
    }

    @Test
    void exportsEveryFilteredEstimateWithOneInventoryCalculation()
            throws Exception {
        LocalDate cutoffDate = LocalDate.of(2026, 7, 18);
        LocalDate startDate = cutoffDate.minusDays(29);
        List<ProductoStockSnapshot> snapshots = new ArrayList<>();
        List<Movimiento> movements = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            Material material = material(
                    "MP-" + index,
                    "Material " + index,
                    "KG",
                    1);
            snapshots.add(new ProductoStockSnapshot(material, index * 10));
            movements.add(dispensation(
                    material,
                    -30,
                    cutoffDate.atTime(8, index)));
        }

        TransaccionAlmacenRepo movementRepo =
                mock(TransaccionAlmacenRepo.class);
        InventarioStockReader stockReader = mock(InventarioStockReader.class);
        when(stockReader.readGeneralStock()).thenReturn(snapshots);
        when(movementRepo.findMovimientosBiByAlmacenAndRango(
                Movimiento.Almacen.GENERAL,
                startDate.atStartOfDay(),
                cutoffDate.atTime(LocalTime.MAX)))
                .thenReturn(movements);
        var service = new CoberturaMaterialesService(
                movementRepo,
                stockReader,
                new BootstrapDemandIntervalCalculator(),
                CLOCK);

        var export = service.exportExcel(
                30,
                FuenteDemandaCobertura.SOLO_DISPENSACIONES,
                "TODOS",
                "MATERIA_PRIMA",
                "KG",
                "AGOTAMIENTO",
                "material");

        assertEquals(
                LocalDateTime.of(2026, 7, 18, 10, 0),
                export.cutoff());
        try (var workbook = WorkbookFactory.create(
                new ByteArrayInputStream(export.content()))) {
            assertEquals(1, workbook.getNumberOfSheets());
            var sheet = workbook.getSheet("Cobertura");
            assertEquals("Cobertura de materiales",
                    sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Fecha y hora de corte (hora Colombia)",
                    sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals(CellType.NUMERIC,
                    sheet.getRow(2).getCell(3).getCellType());
            assertEquals("HORIZONTE",
                    sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("CÓDIGO",
                    sheet.getRow(3).getCell(2).getStringCellValue());
            assertEquals(12, sheet.getLastRowNum() - 3);
            assertEquals(CellType.NUMERIC,
                    sheet.getRow(4).getCell(5).getCellType());
            assertEquals(CellType.NUMERIC,
                    sheet.getRow(4).getCell(10).getCellType());
        }

        verify(stockReader, times(1)).readGeneralStock();
        verify(movementRepo, times(1))
                .findMovimientosBiByAlmacenAndRango(
                        Movimiento.Almacen.GENERAL,
                        startDate.atStartOfDay(),
                        cutoffDate.atTime(LocalTime.MAX));
    }

    @Test
    void exportsAValidHeaderOnlyWorkbookWhenNoMaterialHasDemand()
            throws Exception {
        var export = serviceFor(30, List.of(), List.of()).exportExcel(
                30,
                FuenteDemandaCobertura.SOLO_DISPENSACIONES,
                "TODOS",
                "TODOS",
                null,
                "AGOTAMIENTO",
                null);

        try (var workbook = WorkbookFactory.create(
                new ByteArrayInputStream(export.content()))) {
            var sheet = workbook.getSheet("Cobertura");
            assertEquals(3, sheet.getLastRowNum());
            assertEquals("CONFIANZA BAJA",
                    sheet.getRow(3).getCell(11).getStringCellValue());
        }
    }

    @Test
    void rejectsIncompatibleDemandOrderAndUnsupportedPageSize() {
        var service = serviceFor(30, List.of(), List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculate(
                        30,
                        FuenteDemandaCobertura.SOLO_DISPENSACIONES,
                        "TODOS",
                        "TODOS",
                        null,
                        "MAYOR_DEMANDA",
                        null,
                        0,
                        10));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculate(
                        30,
                        FuenteDemandaCobertura.SOLO_DISPENSACIONES,
                        "TODOS",
                        "TODOS",
                        null,
                        "AGOTAMIENTO",
                        null,
                        0,
                        50));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.exportExcel(
                        30,
                        FuenteDemandaCobertura.SOLO_DISPENSACIONES,
                        "TODOS",
                        "TODOS",
                        null,
                        "MAYOR_DEMANDA",
                        null));
    }

    private Material material(String id, String name, String unit) {
        return material(id, name, unit, 1);
    }

    private Material material(String id, String name, String unit, int group) {
        Material material = new Material();
        material.setProductoId(id);
        material.setNombre(name);
        material.setTipoMaterial(group);
        material.setTipoUnidades(unit);
        return material;
    }

    private CoberturaMaterialesService serviceFor(
            int windowDays,
            List<ProductoStockSnapshot> snapshots,
            List<Movimiento> movements
    ) {
        LocalDate cutoffDate = LocalDate.of(2026, 7, 18);
        LocalDate startDate = cutoffDate.minusDays(windowDays - 1L);
        TransaccionAlmacenRepo movementRepo = mock(TransaccionAlmacenRepo.class);
        InventarioStockReader stockReader = mock(InventarioStockReader.class);
        when(stockReader.readGeneralStock()).thenReturn(snapshots);
        when(movementRepo.findMovimientosBiByAlmacenAndRango(
                Movimiento.Almacen.GENERAL,
                startDate.atStartOfDay(),
                cutoffDate.atTime(LocalTime.MAX)))
                .thenReturn(movements);
        return new CoberturaMaterialesService(
                movementRepo,
                stockReader,
                new BootstrapDemandIntervalCalculator(),
                CLOCK);
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
