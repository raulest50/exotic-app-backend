package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.batchrecord.*;
import exotic.app.planta.repo.calidad.ControlProcesoPlantillaRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordEtapaRepo;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BatchRecordEtapaProjectionTest {
    @Test
    void puedeCrearLaEstructuraDeUnaEtapaYaTerminadaAntesDeReproducirSuHistorial() {
        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(881);
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(13);
        area.setNombre("Fabricacion");
        SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
        seguimiento.setId(1L);
        seguimiento.setOrdenProduccion(orden);
        seguimiento.setAreaOperativa(area);
        seguimiento.setEstado(SeguimientoOrdenArea.ESTADO_COMPLETADO);
        BatchRecord record = new BatchRecord();
        record.setOrdenProduccion(orden);

        SeguimientoOrdenAreaRepo seguimientos = mock(SeguimientoOrdenAreaRepo.class);
        BatchRecordEtapaRepo etapas = mock(BatchRecordEtapaRepo.class);
        when(seguimientos.findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(881))
                .thenReturn(List.of(seguimiento));
        // IDENTITY ejecuta la validacion de persistencia antes del replay.
        when(etapas.save(any(BatchRecordEtapa.class))).thenAnswer(invocation -> {
            BatchRecordEtapa etapa = invocation.getArgument(0);
            ReflectionTestUtils.invokeMethod(etapa, "validarInvariantes");
            return etapa;
        });
        BatchRecordService service = mock(BatchRecordService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "seguimientoRepo", seguimientos);
        ReflectionTestUtils.setField(service, "etapaRepo", etapas);
        ReflectionTestUtils.setField(service, "plantillaRepo", mock(ControlProcesoPlantillaRepo.class));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                service, "crearEtapasDesdeSeguimiento", record, orden));

        assertEquals(1, record.getEtapas().size());
        assertEquals(EstadoBatchRecordEtapa.PENDIENTE, record.getEtapas().get(0).getEstado());
        assertNull(record.getEtapas().get(0).getReportadaPor());
        assertSame(seguimiento, record.getEtapas().get(0).getSeguimientoOrdenArea());
    }
}
