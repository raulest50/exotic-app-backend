package exotic.app.planta.model.producto.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductoInventareableUpdateDTO {
    private Boolean inventareable;
    private Boolean consumoDirecto;

    public ProductoInventareableUpdateDTO(Boolean inventareable) {
        this.inventareable = inventareable;
    }
}
