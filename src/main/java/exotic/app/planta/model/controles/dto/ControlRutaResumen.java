package exotic.app.planta.model.controles.dto;

import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.PuntoAplicacionControl;

import java.util.List;

/** Información de configuración vigente; no representa ejecución ni conformidad. */
public record ControlRutaResumen(
        Long planId, String codigo, String nombre, AmbitoControl ambito, Integer version,
        PuntoAplicacionControl puntoAplicacion, String frontendNodeId,
        Integer areaOperativaId, Integer procesoProduccionId,
        String productoId, String productoNombre, Integer categoriaId,
        boolean legadoGlobal, List<String> productosExcluidosIds) {
}
