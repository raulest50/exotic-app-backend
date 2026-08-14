package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.UnidadTiempoVencimiento;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Fuente unica para copiar y aplicar la politica de vencimiento de produccion.
 */
@Service
public class VencimientoLoteService {

    public void copiarPoliticaVigente(Producto producto, Lote lote) {
        if (!(producto instanceof Terminado terminado) || terminado.getCategoria() == null) {
            limpiarPolitica(lote);
            return;
        }

        Categoria categoria = terminado.getCategoria();
        Integer cantidad = categoria.getVidaUtilCantidad();
        UnidadTiempoVencimiento unidad = categoria.getVidaUtilUnidad();
        validarPolitica(cantidad, unidad);

        lote.setVidaUtilCantidadAplicada(cantidad);
        lote.setVidaUtilUnidadAplicada(unidad);
    }

    public LocalDate calcularFechaSugerida(Lote lote, LocalDate fechaProduccion) {
        if (lote == null || fechaProduccion == null) {
            return null;
        }

        Integer cantidad = lote.getVidaUtilCantidadAplicada();
        UnidadTiempoVencimiento unidad = lote.getVidaUtilUnidadAplicada();
        validarPolitica(cantidad, unidad);
        if (cantidad == null) {
            return null;
        }

        try {
            return switch (unidad) {
                case DIAS -> fechaProduccion.plusDays(cantidad);
                case MESES -> fechaProduccion.plusMonths(cantidad);
                case ANIOS -> fechaProduccion.plusYears(cantidad);
            };
        } catch (DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "La vida util configurada produce una fecha fuera del rango permitido", exception);
        }
    }

    public void validarFechaConfirmada(LocalDate fechaProduccion, LocalDate fechaVencimiento) {
        if (fechaVencimiento == null) {
            throw new IllegalArgumentException("La fecha de vencimiento es obligatoria para todos los lotes.");
        }
        if (fechaProduccion == null || !fechaVencimiento.isAfter(fechaProduccion)) {
            throw new IllegalArgumentException(
                    "La fecha de vencimiento debe ser posterior a la fecha de produccion.");
        }
    }

    private void validarPolitica(Integer cantidad, UnidadTiempoVencimiento unidad) {
        if ((cantidad == null) != (unidad == null)) {
            throw new IllegalStateException("La politica de vida util del lote esta incompleta.");
        }
        if (cantidad != null && cantidad <= 0) {
            throw new IllegalStateException("La cantidad de vida util del lote debe ser mayor que cero.");
        }
    }

    private void limpiarPolitica(Lote lote) {
        lote.setVidaUtilCantidadAplicada(null);
        lote.setVidaUtilUnidadAplicada(null);
    }
}
