package exotic.app.planta.model.commons.notificaciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialEnPuntoReordenDTO {

    private String productoId;
    private String nombre;
    private int tipoMaterial;
    private String tipoMaterialLabel;
    private double stockActual;
    private double puntoReorden;
    private String tipoUnidades;
}
