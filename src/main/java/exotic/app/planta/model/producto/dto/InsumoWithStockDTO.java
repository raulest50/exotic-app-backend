package exotic.app.planta.model.producto.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class InsumoWithStockDTO {
    private int insumoId;
    private String productoId;
    private String productoNombre;
    private double cantidadRequerida;
    private double stockActual;
    private TipoProducto tipoProducto; // Nuevo campo para el tipo de producto
    private String tipoUnidades; // Unidad de medida (KG, L, U, etc.)
    private Boolean inventareable; // Indica si el producto es inventariable (true) o no (false, como el agua)
    private Boolean consumoDirecto; // Registra consumo productivo sin afectar stock ni lotes

    // Nueva lista para almacenar los insumos de un semielaborado
    private List<InsumoWithStockDTO> subInsumos = new ArrayList<>();

    // Enum para representar los tipos de productos
    public enum TipoProducto {
        M,      // Material
        S,      // Semielaborado
        T       // Terminado
    }
}
