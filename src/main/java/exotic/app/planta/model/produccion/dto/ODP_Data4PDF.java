package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.organizacion.AreaOperativa;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ODP_Data4PDF {

    private Terminado terminado;

    private List<Material> materials;
    private List<SemiTerminado> semiterminados;

    private List<AreaOperativa> areasProduccion;
}
