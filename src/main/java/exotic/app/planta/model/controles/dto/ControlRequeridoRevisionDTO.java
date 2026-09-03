package exotic.app.planta.model.controles.dto;

import exotic.app.planta.model.controles.*;

import java.time.LocalDateTime;

public record ControlRequeridoRevisionDTO(
        Long requisitoId,
        String planCodigo,
        String planNombre,
        Integer versionNumero,
        AmbitoControl ambito,
        EstadoControlRequerido estado,
        OrigenControlRequerido origen,
        PuntoAplicacionControl puntoAplicacion,
        MomentoControl momento,
        PuntoExigenciaControl puntoExigencia,
        Long etapaId,
        String etapaNombre,
        Integer areaId,
        String areaNombre,
        boolean requiereRepeticion,
        boolean requiereRevalidacion,
        boolean agregadoExcepcionalmente,
        String motivoAdicion,
        String agregadoPor,
        Long revisionAdicionId,
        Long firmaAdicionId,
        Long ultimaEjecucionId,
        ResultadoEjecucionControl ultimaEjecucionResultado,
        LocalDateTime ultimaEjecucionFecha,
        String ultimaEjecucionUsuario,
        Long legacyEjecucionId) {
}
