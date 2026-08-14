package exotic.app.planta.model.producto.dto;

import exotic.app.planta.model.producto.UnidadTiempoVencimiento;
import lombok.Data;

/**
 * Reemplaza atomicamente la politica de vida util de una categoria.
 * Ambos valores en null representan una categoria sin calculo automatico.
 */
@Data
public class CategoriaVidaUtilRequestDTO {
    private Integer vidaUtilCantidad;
    private UnidadTiempoVencimiento vidaUtilUnidad;
}
