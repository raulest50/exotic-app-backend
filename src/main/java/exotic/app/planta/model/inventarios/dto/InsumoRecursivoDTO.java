package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InsumoRecursivoDTO {
    private Integer insumoId;
    private String productoId;
    private String productoNombre;
    private double cantidadTotalRequerida;
    private String tipoUnidades;
    private String tipoProducto;
    private Boolean inventareable;
    private List<InsumoRecursivoDTO> subInsumos = new ArrayList<>();
}

