package exotic.app.planta.model.commons.dto.eliminaciones;

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
public class EliminacionTerminadosBatchResultDTO {
    private boolean permitted;
    private boolean executed;
    private String message;
    private int totalTerminados;
    private int eliminados;
    private int fallidos;
    private List<String> productoIdsProcesados = new ArrayList<>();
    private List<EliminacionBatchFailureDTO> failures = new ArrayList<>();
}
