package exotic.app.planta.model.commons.notificaciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MaterialEnPuntoReordenConOcmDTO extends MaterialEnPuntoReordenDTO {

    private List<OcmPendienteIngresoDTO> ocmsPendientesIngreso = new ArrayList<>();

    public MaterialEnPuntoReordenConOcmDTO(
            String productoId,
            String nombre,
            int tipoMaterial,
            String tipoMaterialLabel,
            double stockActual,
            double puntoReorden,
            String tipoUnidades,
            List<OcmPendienteIngresoDTO> ocmsPendientesIngreso
    ) {
        super(productoId, nombre, tipoMaterial, tipoMaterialLabel, stockActual, puntoReorden, tipoUnidades);
        this.ocmsPendientesIngreso = ocmsPendientesIngreso;
    }
}
