package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventarioExcelRequestDTO {
    private String searchTerm;
    private String tipoBusqueda;
}
