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
public class DispensacionV2MaterialDTO {
    private String productoId;
    private String productoNombre;
    private String tipoUnidades;
    private String tipoProducto;
    private boolean inventareable;
    private boolean checked;
    private double cantidadReceta;
    private double cantidadADispensar;
    private double cantidadHistorica;
    private double totalConHistorico;
    private boolean excedeReceta;
    private String warning;
    private List<DispensacionV2LoteOrigenDTO> lotesOrigen = new ArrayList<>();
}
