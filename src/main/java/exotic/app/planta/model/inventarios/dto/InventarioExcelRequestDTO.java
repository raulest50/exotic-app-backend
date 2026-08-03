package exotic.app.planta.model.inventarios.dto;

import exotic.app.planta.model.inventarios.Movimiento;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class InventarioExcelRequestDTO {
    private String searchTerm;
    private String tipoBusqueda;
    private AlcanceInventario alcance = AlcanceInventario.FISICO_TOTAL;
    private List<Movimiento.Almacen> almacenes = new ArrayList<>();

    public InventarioExcelRequestDTO(String searchTerm, String tipoBusqueda) {
        this.searchTerm = searchTerm;
        this.tipoBusqueda = tipoBusqueda;
    }

    public InventarioExcelRequestDTO(
            String searchTerm,
            String tipoBusqueda,
            AlcanceInventario alcance,
            List<Movimiento.Almacen> almacenes
    ) {
        this.searchTerm = searchTerm;
        this.tipoBusqueda = tipoBusqueda;
        this.alcance = alcance;
        this.almacenes = almacenes;
    }
}
