package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.AplicabilidadPlanControl;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordEtapa;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlWorkflowServiceNodeMatchingTest {

    @Test
    void unaReglaLegadaSinNodoConservaLaCoincidenciaAmplia() {
        AplicabilidadPlanControl regla = new AplicabilidadPlanControl();

        assertTrue(ControlWorkflowService.coincideNodoConfigurado(
                regla, new BatchRecordEtapa()));
    }

    @Test
    void unaEtapaDeOpSoloCoincideConElNodoGraficoSeleccionado() {
        AplicabilidadPlanControl regla = reglaPara("nodo-envasado");
        RutaProcesoNode nodo = new RutaProcesoNode();
        nodo.setFrontendId("nodo-envasado");
        SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
        seguimiento.setRutaProcesoNode(nodo);
        BatchRecordEtapa etapa = new BatchRecordEtapa();
        etapa.setSeguimientoOrdenArea(seguimiento);

        assertTrue(ControlWorkflowService.coincideNodoConfigurado(regla, etapa));

        nodo.setFrontendId("nodo-mezclado");
        assertFalse(ControlWorkflowService.coincideNodoConfigurado(regla, etapa));
    }

    @Test
    void unaEtapaDeOfSoloCoincideConElNodoGraficoSeleccionado() {
        AplicabilidadPlanControl regla = reglaPara("proc-pesaje");
        OrdenFabricacionOperacion operacion = new OrdenFabricacionOperacion();
        operacion.setFrontendNodeId("proc-pesaje");
        BatchRecordEtapa etapa = new BatchRecordEtapa();
        etapa.setOrdenFabricacionOperacion(operacion);

        assertTrue(ControlWorkflowService.coincideNodoConfigurado(regla, etapa));

        operacion.setFrontendNodeId("proc-mezclado");
        assertFalse(ControlWorkflowService.coincideNodoConfigurado(regla, etapa));
    }

    private AplicabilidadPlanControl reglaPara(String frontendNodeId) {
        AplicabilidadPlanControl regla = new AplicabilidadPlanControl();
        regla.setFrontendNodeId(frontendNodeId);
        return regla;
    }
}
