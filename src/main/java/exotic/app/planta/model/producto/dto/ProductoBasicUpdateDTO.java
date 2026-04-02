package exotic.app.planta.model.producto.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductoBasicUpdateDTO {
    private String productoId;
    private String nombre;
    private Double cantidadUnidad;
    private String observaciones;
    private Double ivaPercentual;
    private Integer tipoMaterial;
    private Double puntoReorden;
    private String prefijoLote;
    private Integer categoriaId;
}
