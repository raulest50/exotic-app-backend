package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.produccion.EstadoMpsSemanalItem;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class MpsSemanalItemDTO {
    private Long id;
    private String terminadoId;
    private String terminadoNombre;
    private Integer categoriaId;
    private String categoriaNombre;
    private int loteSize;
    private int tiempoDiasFabricacion;
    private int numeroLotes;
    private EstadoMpsSemanalItem estadoItem;
    private double cantidadTotal;
    private LocalDate fechaLanzamiento;
    private LocalDate fechaFinalPlanificada;
    private String observacion;
    private String warning;
    private int displayOrder;
    private boolean editable;
    private String blockedReason;
    private int ordenesIniciadas;
    private int ordenesCancelables;
    private int lotesActivos;
    private int lotesCancelados;
    private List<MpsSemanalLotePlanificadoDTO> lotesPlanificados = new ArrayList<>();
}
