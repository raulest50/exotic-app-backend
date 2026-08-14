package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.UnidadTiempoVencimiento;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VencimientoLoteServiceTest {

    private final VencimientoLoteService service = new VencimientoLoteService();

    @Test
    void copiarPoliticaVigente_createsImmutableSnapshotOnLot() {
        Categoria categoria = new Categoria();
        categoria.setVidaUtilCantidad(6);
        categoria.setVidaUtilUnidad(UnidadTiempoVencimiento.MESES);
        Terminado terminado = new Terminado();
        terminado.setCategoria(categoria);
        Lote lote = new Lote();

        service.copiarPoliticaVigente(terminado, lote);
        categoria.setVidaUtilCantidad(2);
        categoria.setVidaUtilUnidad(UnidadTiempoVencimiento.ANIOS);

        assertEquals(6, lote.getVidaUtilCantidadAplicada());
        assertEquals(UnidadTiempoVencimiento.MESES, lote.getVidaUtilUnidadAplicada());
    }

    @Test
    void calcularFechaSugerida_usesCalendarSemantics() {
        Lote lote = new Lote();
        lote.setVidaUtilCantidadAplicada(1);
        lote.setVidaUtilUnidadAplicada(UnidadTiempoVencimiento.MESES);

        assertEquals(
                LocalDate.of(2028, 2, 29),
                service.calcularFechaSugerida(lote, LocalDate.of(2028, 1, 31))
        );

        lote.setVidaUtilUnidadAplicada(UnidadTiempoVencimiento.ANIOS);
        assertEquals(
                LocalDate.of(2029, 2, 28),
                service.calcularFechaSugerida(lote, LocalDate.of(2028, 2, 29))
        );

        lote.setVidaUtilCantidadAplicada(10);
        lote.setVidaUtilUnidadAplicada(UnidadTiempoVencimiento.DIAS);
        assertEquals(
                LocalDate.of(2026, 8, 22),
                service.calcularFechaSugerida(lote, LocalDate.of(2026, 8, 12))
        );
    }

    @Test
    void calcularFechaSugerida_returnsNullWithoutSnapshot() {
        assertNull(service.calcularFechaSugerida(new Lote(), LocalDate.of(2026, 8, 12)));
    }

    @Test
    void validarFechaConfirmada_requiresDateAfterProduction() {
        LocalDate produccion = LocalDate.of(2026, 8, 12);
        assertThrows(IllegalArgumentException.class,
                () -> service.validarFechaConfirmada(produccion, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.validarFechaConfirmada(produccion, produccion));
        service.validarFechaConfirmada(produccion, produccion.plusDays(1));
    }
}
