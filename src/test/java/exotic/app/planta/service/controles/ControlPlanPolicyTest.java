package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.MomentoControl;
import exotic.app.planta.model.controles.PuntoAplicacionControl;
import exotic.app.planta.model.controles.PuntoExigenciaControl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControlPlanPolicyTest {

    @Test
    void procesoSiempreEsInformativoDuranteFabricacion() {
        ControlPlanPolicy.validarUbicacion(
                AmbitoControl.PROCESO, PuntoAplicacionControl.SALIDA_OPERACION, "node-1", false);

        assertEquals("CONTROL_DE_PROCESO",
                ControlPlanPolicy.propositoInterno(AmbitoControl.PROCESO));
        assertEquals(MomentoControl.DURANTE_FABRICACION,
                ControlPlanPolicy.momento(AmbitoControl.PROCESO, PuntoAplicacionControl.SALIDA_OPERACION));
        assertEquals(PuntoExigenciaControl.INFORMATIVO,
                ControlPlanPolicy.puntoExigencia(
                        AmbitoControl.PROCESO, PuntoAplicacionControl.SALIDA_OPERACION, false));
        assertThrows(IllegalArgumentException.class, () -> ControlPlanPolicy.validarUbicacion(
                AmbitoControl.PROCESO, PuntoAplicacionControl.SALIDA_OPERACION, "node-1", true));
    }

    @Test
    void calidadBloqueanteSeInfiereDesdeLaUbicacion() {
        assertEquals(MomentoControl.DURANTE_FABRICACION,
                ControlPlanPolicy.momento(AmbitoControl.CALIDAD, PuntoAplicacionControl.SALIDA_OPERACION));
        assertEquals(PuntoExigenciaControl.CIERRE_ETAPA,
                ControlPlanPolicy.puntoExigencia(
                        AmbitoControl.CALIDAD, PuntoAplicacionControl.SALIDA_OPERACION, true));
        assertEquals(MomentoControl.REVISION_FINAL,
                ControlPlanPolicy.momento(AmbitoControl.CALIDAD, PuntoAplicacionControl.LOTE_FINAL));
        assertEquals(PuntoExigenciaControl.LIBERACION,
                ControlPlanPolicy.puntoExigencia(
                        AmbitoControl.CALIDAD, PuntoAplicacionControl.LOTE_FINAL, true));
        assertEquals(PuntoExigenciaControl.INFORMATIVO,
                ControlPlanPolicy.puntoExigencia(
                        AmbitoControl.CALIDAD, PuntoAplicacionControl.LOTE_FINAL, false));
    }

    @Test
    void unaUbicacionOperativaExigeNodoGrafico() {
        assertThrows(IllegalArgumentException.class, () -> ControlPlanPolicy.validarUbicacion(
                AmbitoControl.CALIDAD, PuntoAplicacionControl.SALIDA_OPERACION, null, false));
        assertThrows(IllegalArgumentException.class, () -> ControlPlanPolicy.validarUbicacion(
                AmbitoControl.PROCESO, PuntoAplicacionControl.LOTE_FINAL, null, false));
    }
}
