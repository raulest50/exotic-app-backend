package exotic.app.planta.model.inventarios.dto;

import exotic.app.planta.model.producto.manufacturing.packaging.dto.CasePackResponseDTO;
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
public class DispensacionResumenDTO {
    private List<InsumoRecursivoDTO> insumosReceta = new ArrayList<>();
    private List<InsumoDesglosadoDTO> insumosEmpaque = new ArrayList<>();
    private CasePackResponseDTO casePack;
    private List<TransaccionAlmacenDetalleDTO> historialDispensaciones = new ArrayList<>();
    private List<ItemPendienteReposicionDTO> itemsPendientesReposicion = new ArrayList<>();
}

