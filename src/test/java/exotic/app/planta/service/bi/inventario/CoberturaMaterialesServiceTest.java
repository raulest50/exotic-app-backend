package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.inventarios.Movimiento;
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
        Movimiento movement = new Movimiento();
        movement.setProducto(material);
        movement.setCantidad(quantity);
        movement.setFechaMovimiento(date);
        movement.setTipoMovimiento(Movimiento.TipoMovimiento.DISPENSACION);
        movement.setAlmacen(Movimiento.Almacen.GENERAL);
        return movement;
    }
}
