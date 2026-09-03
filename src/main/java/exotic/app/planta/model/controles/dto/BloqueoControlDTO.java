package exotic.app.planta.model.controles.dto;

import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.EstadoControlRequerido;
import exotic.app.planta.model.controles.PuntoExigenciaControl;

public record BloqueoControlDTO(
        Long controlRequeridoId,
        String planCodigo,
        String planNombre,
        AmbitoControl ambito,
        EstadoControlRequerido estado,
        PuntoExigenciaControl puntoExigencia,
        String mensaje) {
}
