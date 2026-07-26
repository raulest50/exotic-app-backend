package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AjustesInventarioDetalleServiceTest {
    private final TransaccionAlmacenRepo movementRepo =
            mock(TransaccionAlmacenRepo.class);
    private final AjustesInventarioDetalleService service =
            new AjustesInventarioDetalleService(
                    movementRepo,
                    new AjustesInventarioAssembler());

    @Test
    void filtersByGroupDirectionAndSearchBeforePaging() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        Material rawOne = material("MP-1", "Alcohol", 1);
        Material rawTwo = material("MP-2", "Alcohol dos", 1);
        Material packaging = material("EMP-1", "Envase", 2);
        List<Movimiento> movements = List.of(
                adjustment(1, 10, rawOne, date, 8),
                adjustment(2, -5, rawTwo, date, 9),
                adjustment(3, -4, packaging, date, 10));
        when(movementRepo.findAjustesMaterialesBiByAlmacenAndRango(
                eq(Movimiento.Almacen.GENERAL),
                anyCollection(),
                eq(date.atStartOfDay()),
                eq(date.atTime(java.time.LocalTime.MAX))))
                .thenReturn(movements);

        var result = service.getMaterials(
                date,
                date,
                "MATERIA_PRIMA",
                "NEGATIVO",
                "IMPACTO",
                "DOS",
                0,
                5);

        assertEquals(1, result.totalElements());
        assertEquals("MP-2", result.items().get(0).productoId());
        assertEquals(500, result.items().get(0).impactoEstimado(), 0.000001);
        verify(movementRepo).findAjustesMaterialesBiByAlmacenAndRango(
                eq(Movimiento.Almacen.GENERAL),
                anyCollection(),
                eq(date.atStartOfDay()),
                eq(date.atTime(java.time.LocalTime.MAX)));
    }

    @Test
    void rejectsUnsupportedGroupAndPageSize() {
        LocalDate date = LocalDate.of(2026, 7, 20);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getMaterials(
                        date,
                        date,
                        "OTROS",
                        "TODOS",
                        "IMPACTO",
                        "",
                        0,
                        5));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getMaterials(
                        date,
                        date,
                        "EMPAQUE",
                        "TODOS",
                        "IMPACTO",
                        "",
                        0,
                        20));
    }

    private Material material(
            String id,
            String name,
            int materialType
    ) {
        Material material = new Material();
        material.setProductoId(id);
        material.setNombre(name);
        material.setTipoMaterial(materialType);
        material.setTipoUnidades("KG");
        material.asignarCostoInicial(new BigDecimal("100"));
        return material;
    }

    private Movimiento adjustment(
            int transactionId,
            double quantity,
            Material material,
            LocalDate date,
            int hour
    ) {
        TransaccionAlmacen transaction = new TransaccionAlmacen();
        transaction.setTransaccionId(transactionId);
        transaction.setTipoEntidadCausante(
                TransaccionAlmacen.TipoEntidadCausante.OAA);

        Movimiento movement = new Movimiento();
        movement.setCantidad(quantity);
        movement.setProducto(material);
        movement.setAlmacen(Movimiento.Almacen.GENERAL);
        movement.setTipoMovimiento(quantity > 0
                ? Movimiento.TipoMovimiento.AJUSTE_POSITIVO
                : Movimiento.TipoMovimiento.AJUSTE_NEGATIVO);
        movement.setFechaMovimiento(date.atTime(hour, 0));
        movement.setTransaccionAlmacen(transaction);
        return movement;
    }
}
