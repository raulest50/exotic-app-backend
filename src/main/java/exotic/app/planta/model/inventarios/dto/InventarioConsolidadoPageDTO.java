package exotic.app.planta.model.inventarios.dto;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.dto.ProductoStockDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventarioConsolidadoPageDTO {
    private List<ProductoStockDTO> content;
    private int number;
    private int size;
    private long totalElements;
    private int totalPages;
    private AlcanceInventario alcance;
    private List<Movimiento.Almacen> almacenesIncluidos;
    private OffsetDateTime fechaHoraCorte;
}
