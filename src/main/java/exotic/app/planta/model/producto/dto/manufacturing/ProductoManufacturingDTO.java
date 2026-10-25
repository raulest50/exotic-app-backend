package exotic.app.planta.model.producto.dto.manufacturing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoManufacturingDTO {
    private String productoId;
    private String tipoProducto;
    private String nombre;
    private String observaciones;
    private Double costo;
    private Double ivaPercentual;
    /** Unidad única de inventario y de cantidadSalidaReceta para terminados y semiterminados. */
    private String tipoUnidades;
    /** En recetas explícitas solo aplica a terminados en U; 0/null cuando no aplica. */
    private Double cantidadUnidad;
    private String unidadContenidoPorEnvase;
    private Boolean inventareable;
    private Boolean requiereOrdenFabricacion;
    private Integer categoriaId;
    private String categoriaNombre;
    private Integer status;
    private String fotoUrl;
    private String prefijoLote;
    private List<ProductoManufacturingInsumoDTO> insumos = new ArrayList<>();
    private ProductoManufacturingCasePackDTO casePack;
    private ProcesoProduccionCompletoDTO procesoProduccionCompleto;
}
