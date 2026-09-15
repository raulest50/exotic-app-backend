package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.MomentoControl;
import exotic.app.planta.model.controles.PuntoAplicacionControl;
import exotic.app.planta.model.controles.PuntoExigenciaControl;

/**
 * Traduce la configuración mínima expuesta al usuario a la política completa
 * persistida por el motor de controles.
 */
final class ControlPlanPolicy {
    private ControlPlanPolicy() {}

    static String propositoInterno(AmbitoControl ambito) {
        return ambito == AmbitoControl.PROCESO
                ? "CONTROL_DE_PROCESO"
                : "CONTROL_DE_CALIDAD";
    }

    static void validarUbicacion(
            AmbitoControl ambito,
            PuntoAplicacionControl punto,
            String frontendNodeId,
            boolean bloqueante) {
        if (ambito == AmbitoControl.PROCESO) {
            if (punto != PuntoAplicacionControl.SALIDA_OPERACION) {
                throw new IllegalArgumentException(
                        "Un control de proceso debe ubicarse dentro de una operacion.");
            }
            if (bloqueante) {
                throw new IllegalArgumentException(
                        "Los controles de proceso son informativos y no pueden bloquear el flujo.");
            }
        }
        if (punto == PuntoAplicacionControl.SALIDA_OPERACION
                && (frontendNodeId == null || frontendNodeId.isBlank())) {
            throw new IllegalArgumentException(
                    "Seleccione graficamente la operacion o salida donde se realizara el control.");
        }
    }

    static MomentoControl momento(
            AmbitoControl ambito,
            PuntoAplicacionControl punto) {
        if (ambito == AmbitoControl.PROCESO
                || punto == PuntoAplicacionControl.SALIDA_OPERACION) {
            return MomentoControl.DURANTE_FABRICACION;
        }
        return MomentoControl.REVISION_FINAL;
    }

    static PuntoExigenciaControl puntoExigencia(
            AmbitoControl ambito,
            PuntoAplicacionControl punto,
            boolean bloqueante) {
        if (ambito == AmbitoControl.PROCESO || !bloqueante) {
            return PuntoExigenciaControl.INFORMATIVO;
        }
        return punto == PuntoAplicacionControl.LOTE_FINAL
                ? PuntoExigenciaControl.LIBERACION
                : PuntoExigenciaControl.CIERRE_ETAPA;
    }
}
