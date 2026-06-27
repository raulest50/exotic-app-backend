package exotic.app.planta.model.producto.dto.manufacturing.templates;

import exotic.app.planta.model.producto.dto.manufacturing.ProductoManufacturingCasePackDTO;
import exotic.app.planta.model.producto.dto.manufacturing.ProductoManufacturingInsumoDTO;
import exotic.app.planta.model.producto.dto.manufacturing.ProcesoProduccionCompletoDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaManufacturingTemplateDTO {
    private Long id;
    private Integer categoriaId;
    private String categoriaNombre;
    private Double rendimientoTeorico;
    private List<ProductoManufacturingInsumoDTO> insumos = new ArrayList<>();
    private ProductoManufacturingCasePackDTO casePack;
    private ProcesoProduccionCompletoDTO procesoProduccionCompleto;
}
