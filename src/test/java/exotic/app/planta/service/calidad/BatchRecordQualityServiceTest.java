package exotic.app.planta.service.calidad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import exotic.app.planta.model.calidad.ControlProcesoPlantilla;
import exotic.app.planta.model.calidad.ResultadoControlProceso;
import exotic.app.planta.model.calidad.dto.BatchRecordQualityDTOs;
import exotic.app.planta.model.controles.dto.ResumenControlesBatchRecordDTO;
import exotic.app.planta.model.controles.dto.ControlRequeridoRevisionDTO;
import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.EstadoControlRequerido;
import exotic.app.planta.model.controles.MomentoControl;
import exotic.app.planta.model.controles.OrigenControlRequerido;
import exotic.app.planta.model.controles.PuntoAplicacionControl;
import exotic.app.planta.model.controles.PuntoExigenciaControl;
import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordEtapa;
import exotic.app.planta.model.produccion.batchrecord.DecisionCalidadBatchRecord;
import exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord;
import exotic.app.planta.model.produccion.dto.BatchRecordDTOs;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoEjecucionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordDesviacionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordEtapaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.repo.produccion.batchrecord.CicloRevisionBatchRecordRepo;
import exotic.app.planta.service.controles.ControlWorkflowService;
import exotic.app.planta.service.controles.ControlExecutionService;
import exotic.app.planta.model.controles.dto.ControlDTOs.EjecucionDetalleResponse;
import exotic.app.planta.service.produccion.BatchRecordService;
import exotic.app.planta.service.produccion.OrdenFabricacionOperacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class BatchRecordQualityServiceTest {

    private BatchRecordRepo batchRecordRepo;
    private BatchRecordEtapaRepo etapaRepo;
    private BatchRecordDesviacionRepo desviacionRepo;
    private ControlProcesoEjecucionRepo ejecucionRepo;
    private BatchRecordService batchRecordService;
    private ControlWorkflowService controlWorkflowService;
    private ControlExecutionService controlExecutionService;
    private BatchRecordQualityService service;
    private BatchRecord record;
    private BatchRecordEtapa etapa;

    @BeforeEach
    void setUp() {
        batchRecordRepo = mock(BatchRecordRepo.class);
        etapaRepo = mock(BatchRecordEtapaRepo.class);
        desviacionRepo = mock(BatchRecordDesviacionRepo.class);
        ejecucionRepo = mock(ControlProcesoEjecucionRepo.class);
        batchRecordService = mock(BatchRecordService.class);
        controlWorkflowService = mock(ControlWorkflowService.class);
        controlExecutionService = mock(ControlExecutionService.class);
        service = new BatchRecordQualityService(
                batchRecordRepo,
                mock(CicloRevisionBatchRecordRepo.class),
                etapaRepo,
                desviacionRepo,
                ejecucionRepo,
                batchRecordService,
                controlWorkflowService,
                controlExecutionService,
                mock(OrdenFabricacionOperacionService.class),
                new ObjectMapper());

        record = expedientePendiente();
        etapa = etapa(record);
        when(batchRecordRepo.findById(1L)).thenReturn(Optional.of(record));
        when(etapaRepo.findByBatchRecord_IdOrderBySecuenciaAscIdAsc(1L))
                .thenReturn(List.of(etapa));
        when(batchRecordService.detalle(1L)).thenReturn(new BatchRecordDTOs.Detail());
        when(controlWorkflowService.resumenPorBatchRecord(1L))
                .thenReturn(new ResumenControlesBatchRecordDTO(
                        1L, 1, 0, 1, 0, 0, 0, 0));
        when(controlWorkflowService.validarBloqueosLiberacion(record)).thenReturn(List.of());
    }

    @Test
    void detalleConservaControlDeProcesoEjecutadoAntesDelEnvio() {
        ControlProcesoEjecucion ejecucion = new ControlProcesoEjecucion();
        ejecucion.setId(80L);
        ejecucion.setResultado(ResultadoControlProceso.CONFORME);
        ejecucion.setFechaRegistro(LocalDateTime.of(2026, 8, 31, 9, 0));
        when(ejecucionRepo.findTopByBatchRecordEtapa_IdOrderByFechaRegistroDescIdDesc(11L))
                .thenReturn(Optional.of(ejecucion));

        BatchRecordQualityDTOs.ReviewDetail detalle = service.detalle(1L);

        assertEquals(80L, detalle.getControles().getFirst().getUltimaEjecucionId());
        assertFalse(detalle.getControles().getFirst().isPendiente());
    }

    @Test
    void detalleExponeControlesNeutralesDeProcesoEnSoloLectura() {
        ControlRequeridoRevisionDTO control = new ControlRequeridoRevisionDTO(
                91L, "PC-PESO", "Peso de envasado", 4, AmbitoControl.PROCESO,
                EstadoControlRequerido.CONFORME, OrigenControlRequerido.BATCH_RECORD,
                PuntoAplicacionControl.SALIDA_OPERACION, MomentoControl.DURANTE_FABRICACION,
                PuntoExigenciaControl.CIERRE_ETAPA, 11L, "Mezclado", 5,
                "Mezclas", false, false, false, null, null, null, null,
                80L, null, null, "operario", null);
        when(controlWorkflowService.controlesProcesoPorBatchRecord(1L))
                .thenReturn(List.of(control));

        BatchRecordQualityDTOs.ReviewDetail detalle = service.detalle(1L);

        assertEquals(List.of(control), detalle.getControlesProceso());
        assertEquals(AmbitoControl.PROCESO,
                detalle.getControlesProceso().getFirst().ambito());
    }

    @Test
    void detalleExponeEnsayosYProcedenciaExcepcionalSinPermisoDeEjecucion() {
        ControlRequeridoRevisionDTO control = new ControlRequeridoRevisionDTO(
                92L, "EC-PH", "Ensayo de pH", 2, AmbitoControl.CALIDAD,
                EstadoControlRequerido.CONFORME, OrigenControlRequerido.BATCH_RECORD,
                PuntoAplicacionControl.LOTE_FINAL, MomentoControl.REVISION_FINAL,
                PuntoExigenciaControl.LIBERACION, null, null, null, null,
                false, false, true, "Seguimiento INVIMA", "calidad.admin", 501L, 601L,
                81L, null, null, "analista", null);
        EjecucionDetalleResponse ejecucion = new EjecucionDetalleResponse(null, List.of(), null);
        when(controlWorkflowService.controlesCalidadPorBatchRecord(1L)).thenReturn(List.of(control));
        when(controlExecutionService.evidenciaPorBatchRecord(AmbitoControl.CALIDAD, 1L))
                .thenReturn(List.of(ejecucion));

        BatchRecordQualityDTOs.ReviewDetail detalle = service.detalle(1L);

        assertEquals(List.of(control), detalle.getControlesCalidad());
        assertEquals("Seguimiento INVIMA", detalle.getControlesCalidad().getFirst().motivoAdicion());
        assertEquals(601L, detalle.getControlesCalidad().getFirst().firmaAdicionId());
        assertEquals(List.of(ejecucion), detalle.getEjecucionesCalidad());
    }

    @Test
    void devolucionProyectaEtapasControlesYSeccionesSinConfundirMetadatosHttp()
            throws Exception {
        when(batchRecordRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(record));
        when(etapaRepo.findById(11L)).thenReturn(Optional.of(etapa));
        BatchRecordQualityDTOs.DecisionRequest request = new BatchRecordQualityDTOs.DecisionRequest();
        request.setDecision(DecisionCalidadBatchRecord.DEVOLVER_A_PRODUCCION);
        request.setMotivo("Corregir evidencia y repetir peso");
        request.setEtapaIds(new LinkedHashSet<>(List.of(11L)));
        request.setRequisitoIds(new LinkedHashSet<>(List.of(91L)));
        request.setSeccionesDocumentales(new LinkedHashSet<>(List.of("Registro de limpieza")));
        User actor = new User();

        service.decidir(1L, actor, request, "127.0.0.1", "test-agent");

        verify(batchRecordService).proyectarAlcanceDevolucion(
                record, request.getEtapaIds(), request.getSeccionesDocumentales(), actor);
        verify(controlWorkflowService).marcarRepeticionRequerida(
                record, request.getRequisitoIds(), 1L);
        ArgumentCaptor<String> alcance = ArgumentCaptor.forClass(String.class);
        verify(batchRecordService).registrarDecisionCalidad(
                eq(record),
                eq(actor),
                eq(DecisionCalidadBatchRecord.DEVOLVER_A_PRODUCCION),
                eq("Corregir evidencia y repetir peso"),
                alcance.capture(),
                eq("127.0.0.1"),
                eq("test-agent"));
        JsonNode json = new ObjectMapper().readTree(alcance.getValue());
        assertEquals(11L, json.path("etapaIds").get(0).asLong());
        assertEquals(91L, json.path("requisitoIds").get(0).asLong());
        assertEquals("Registro de limpieza",
                json.path("seccionesDocumentales").get(0).asText());
    }

    private BatchRecord expedientePendiente() {
        Terminado producto = new Terminado();
        producto.setProductoId("PT-1");
        producto.setNombre("Producto de prueba");
        Lote lote = new Lote();
        lote.setId(20L);
        lote.setBatchNumber("LOT-1");
        lote.setProductionDate(LocalDate.of(2026, 8, 31));
        lote.setExpirationDate(LocalDate.of(2027, 8, 31));
        lote.setEstadoCalidad(EstadoCalidadLote.CUARENTENA);
        BatchRecord resultado = new BatchRecord();
        resultado.setId(1L);
        resultado.setCodigo("BR-OP-1");
        resultado.setEstado(EstadoBatchRecord.PENDIENTE_REVISION);
        resultado.setCicloRevisionActual(1L);
        resultado.setEnviadoRevisionEn(LocalDateTime.of(2026, 9, 1, 10, 0));
        resultado.setCantidadObtenida(java.math.BigDecimal.TEN);
        resultado.setUnidadMedida("kg");
        resultado.setProductoResultado(producto);
        resultado.setLoteResultado(lote);
        return resultado;
    }

    private BatchRecordEtapa etapa(BatchRecord expediente) {
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(5);
        area.setNombre("Mezclas");
        ControlProcesoPlantilla plantilla = new ControlProcesoPlantilla();
        plantilla.setId(30L);
        plantilla.setVersion(2);
        plantilla.setAreaOperativa(area);
        BatchRecordEtapa resultado = new BatchRecordEtapa();
        resultado.setId(11L);
        resultado.setBatchRecord(expediente);
        resultado.setSecuencia(1);
        resultado.setNombre("Mezclado");
        resultado.setAreaOperativa(area);
        resultado.setControlProcesoPlantilla(plantilla);
        return resultado;
    }
}
