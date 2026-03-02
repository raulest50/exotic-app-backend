package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialAveriaDTO {
    private int transaccionId;
    private LocalDateTime fechaTransaccion;
    private String observaciones;
    private String usuarioAprobador;
    private List<HistorialAveriaItemDTO> items;
}
