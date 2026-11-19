package exotic.app.planta.model.producto.dto.manufacturing;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
    /** Solo semiterminados: nominal OF en tipoUnidades, opcional para datos históricos. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal loteSize;
    @JsonIgnore
    private boolean loteSizePresent;
    private Integer categoriaId;
    private String categoriaNombre;
    private Integer status;
    private String fotoUrl;
    private String prefijoLote;
    private List<ProductoManufacturingInsumoDTO> insumos = new ArrayList<>();
    private ProductoManufacturingCasePackDTO casePack;
    private ProcesoProduccionCompletoDTO procesoProduccionCompleto;

    @JsonSetter("loteSize")
    public void setLoteSize(BigDecimal loteSize) {
        this.loteSize = loteSize;
        // Un cliente anterior que omite el campo no debe borrar el valor configurado.
        this.loteSizePresent = true;
    }
}
