package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.EstadoControlRequerido;
import exotic.app.planta.model.controles.PuntoExigenciaControl;
import exotic.app.planta.model.controles.dto.BloqueoControlDTO;
import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.batchrecord.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoEjecucionRepo;
import exotic.app.planta.repo.calidad.ControlProcesoPlantillaRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.batchrecord.*;
import exotic.app.planta.repo.usuarios.FirmaVisualUsuarioVersionRepo;
import exotic.app.planta.service.controles.ControlWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchRecordServiceWorkflowTest {

    private BatchRecordRepo recordRepo;
    private BatchRecordEtapaRepo etapaRepo;
    private BatchRecordRevisionRepo revisionRepo;
    private BatchRecordFirmaRepo firmaRepo;
    private CicloRevisionBatchRecordRepo cicloRepo;
    private SolicitudReaperturaRechazoRepo reaperturaRepo;
    private BatchRecordSeccionCorreccionRepo seccionRepo;
    private LoteRepo loteRepo;
    private MaterialRequirementSnapshotService materialRequirementSnapshotService;
    private ControlWorkflowService controlWorkflowService;
    private BatchRecordService service;

    @BeforeEach
    void setUp() {
        recordRepo = mock(BatchRecordRepo.class);
        etapaRepo = mock(BatchRecordEtapaRepo.class);
        revisionRepo = mock(BatchRecordRevisionRepo.class);
        firmaRepo = mock(BatchRecordFirmaRepo.class);
        cicloRepo = mock(CicloRevisionBatchRecordRepo.class);
        reaperturaRepo = mock(SolicitudReaperturaRechazoRepo.class);
        seccionRepo = mock(BatchRecordSeccionCorreccionRepo.class);
        loteRepo = mock(LoteRepo.class);
        materialRequirementSnapshotService = mock(MaterialRequirementSnapshotService.class);
        controlWorkflowService = mock(ControlWorkflowService.class);
        service = spy(new BatchRecordService(
                recordRepo,
                etapaRepo,
                revisionRepo,
                firmaRepo,
                mock(BatchRecordCorreccionRepo.class),
                mock(BatchRecordDecisionCalidadRepo.class),
                cicloRepo,
                reaperturaRepo,
                seccionRepo,
                mock(BatchRecordConsumoRepo.class),
                mock(BatchRecordDesviacionRepo.class),
                mock(SeguimientoOrdenAreaRepo.class),
                mock(ControlProcesoPlantillaRepo.class),
                mock(ControlProcesoEjecucionRepo.class),
                mock(FirmaVisualUsuarioVersionRepo.class),
                mock(TransaccionAlmacenHeaderRepo.class),
                loteRepo,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-02T15:00:00Z"), ZoneOffset.UTC),
                materialRequirementSnapshotService,
                controlWorkflowService));
        when(materialRequirementSnapshotService.leer(any())).thenReturn(List.of());
        when(firmaRepo.save(any(BatchRecordFirma.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void proyectarDevolucionHabilitaSoloEtapaSeleccionadaYMaterializaSeccion() {
        BatchRecord record = record(EstadoBatchRecord.PENDIENTE_REVISION, 3L);
        BatchRecordEtapa etapa = new BatchRecordEtapa();
        etapa.setId(21L);
        etapa.setBatchRecord(record);
        etapa.setEstado(EstadoBatchRecordEtapa.COMPLETADA);
        CicloRevisionBatchRecord ciclo = new CicloRevisionBatchRecord();
        ciclo.setEstado(EstadoCicloRevisionBatchRecord.EN_REVISION);
        User actor = actor(7L);
        when(recordRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(record));
        when(cicloRepo.findByBatchRecord_IdAndNumero(1L, 3L)).thenReturn(Optional.of(ciclo));
        when(etapaRepo.findByIdAndBatchRecord_Id(21L, 1L)).thenReturn(Optional.of(etapa));

        service.proyectarAlcanceDevolucion(
                record, List.of(21L), List.of(" Registro de limpieza "), actor);

        assertEquals(EstadoBatchRecordEtapa.EN_CORRECCION, etapa.getEstado());
        assertEquals(3L, etapa.getCicloCorreccionHabilitado());
        ArgumentCaptor<BatchRecordSeccionCorreccion> captor =
                ArgumentCaptor.forClass(BatchRecordSeccionCorreccion.class);
        verify(seccionRepo).save(captor.capture());
        assertEquals("Registro de limpieza", captor.getValue().getSeccion());
        assertEquals(3L, captor.getValue().getCicloRevisionNumero());
        assertEquals(EstadoSeccionCorreccionBatchRecord.PENDIENTE,
                captor.getValue().getEstado());
    }

    @Test
    void reenvioRepiteGateYRechazaSeccionDocumentalPendiente() {
        BatchRecord record = record(EstadoBatchRecord.DEVUELTO_PRODUCCION, 1L);
        when(recordRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(record));
        when(seccionRepo.countByBatchRecord_IdAndCicloRevisionNumeroAndEstado(
                1L, 1L, EstadoSeccionCorreccionBatchRecord.PENDIENTE)).thenReturn(1L);

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.reenviarARevisionCalidad(
                        1L, actor(7L), "Correcciones terminadas", "127.0.0.1", "test"));

        assertTrue(error.getMessage().contains("sección(es) documentales"));
    }

    @Test
    void reenvioBloqueaControlSeleccionadoAunqueSuPoliticaFueraInformativa() {
        BatchRecord record = record(EstadoBatchRecord.DEVUELTO_PRODUCCION, 1L);
        record.setCantidadObtenida(BigDecimal.TEN);
        BloqueoControlDTO bloqueo = new BloqueoControlDTO(
                92L, "PC-VISUAL", "Inspección visual", AmbitoControl.PROCESO,
                EstadoControlRequerido.PENDIENTE, PuntoExigenciaControl.INFORMATIVO,
                "La repetición seleccionada sigue pendiente");
        when(recordRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(record));
        when(seccionRepo.countByBatchRecord_IdAndCicloRevisionNumeroAndEstado(
                1L, 1L, EstadoSeccionCorreccionBatchRecord.PENDIENTE)).thenReturn(0L);
        when(etapaRepo.findByBatchRecord_IdOrderBySecuenciaAscIdAsc(1L)).thenReturn(List.of());
        when(controlWorkflowService.validarBloqueosReenvio(record))
                .thenReturn(List.of(bloqueo));

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.reenviarARevisionCalidad(
                        1L, actor(7L), "Corrección terminada", "127.0.0.1", "test"));

        assertTrue(error.getMessage().contains("repetición seleccionada"));
        assertEquals(EstadoBatchRecord.DEVUELTO_PRODUCCION, record.getEstado());
        assertEquals(1L, record.getCicloRevisionActual());
    }

    @Test
    void prevalidacionReenvioIncluyeRepeticionInformativaPendiente() {
        BatchRecord record = record(EstadoBatchRecord.EN_CORRECCION, 2L);
        record.setCantidadObtenida(BigDecimal.TEN);
        BloqueoControlDTO bloqueo = new BloqueoControlDTO(
                92L, "PC-VISUAL", "Inspección visual", AmbitoControl.PROCESO,
                EstadoControlRequerido.PENDIENTE, PuntoExigenciaControl.INFORMATIVO,
                "La repetición seleccionada sigue pendiente");
        when(recordRepo.findById(1L)).thenReturn(Optional.of(record));
        when(etapaRepo.findByBatchRecord_IdOrderBySecuenciaAscIdAsc(1L)).thenReturn(List.of());
        when(controlWorkflowService.validarBloqueos(
                record, PuntoExigenciaControl.ENVIO_CALIDAD)).thenReturn(List.of());
        when(controlWorkflowService.validarBloqueosReenvio(record))
                .thenReturn(List.of(bloqueo));

        var resultado = service.prevalidarEnvio(1L, true);

        assertFalse(resultado.isPermitido());
        assertEquals(List.of(bloqueo), resultado.getBloqueosControl());
    }

    @Test
    void envioInicialCreaCicloInmutableYFirmaExplicita() {
        BatchRecord record = record(EstadoBatchRecord.LISTO_PARA_REVISION, 0L);
        record.setCantidadObtenida(BigDecimal.TEN);
        User actor = actor(7L);
        BatchRecordRevision revision = revision(record, actor);
        when(recordRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(record));
        when(etapaRepo.findByBatchRecord_IdOrderBySecuenciaAscIdAsc(1L)).thenReturn(List.of());
        when(controlWorkflowService.validarBloqueos(
                record, PuntoExigenciaControl.ENVIO_CALIDAD)).thenReturn(List.of());
        doReturn(revision).when(service).crearRevision(
                eq(record), eq(TipoRevisionBatchRecord.ENVIO_CALIDAD), eq(actor), anyString());

        BatchRecordRevision resultado = service.enviarARevisionCalidad(
                1L, actor, "Expediente revisado", "127.0.0.1", "test-agent");

        assertEquals(revision, resultado);
        assertEquals(EstadoBatchRecord.PENDIENTE_REVISION, record.getEstado());
        assertEquals(1L, record.getCicloRevisionActual());
        ArgumentCaptor<CicloRevisionBatchRecord> cicloCaptor =
                ArgumentCaptor.forClass(CicloRevisionBatchRecord.class);
        verify(cicloRepo, org.mockito.Mockito.times(2)).save(cicloCaptor.capture());
        CicloRevisionBatchRecord ciclo = cicloCaptor.getValue();
        assertEquals(1L, ciclo.getNumero());
        assertEquals(OrigenCicloRevisionBatchRecord.ENVIO_INICIAL, ciclo.getOrigen());
        assertEquals(EstadoCicloRevisionBatchRecord.EN_REVISION, ciclo.getEstado());
        assertEquals(revision, ciclo.getRevisionEnvio());
        ArgumentCaptor<BatchRecordFirma> firmaCaptor =
                ArgumentCaptor.forClass(BatchRecordFirma.class);
        verify(firmaRepo).save(firmaCaptor.capture());
        assertEquals(AlcanceFirmaBatchRecord.REVISION_PRODUCCION,
                firmaCaptor.getValue().getAlcance());
        assertEquals(revision, firmaCaptor.getValue().getRevision());
        verify(controlWorkflowService, never()).prepararRevalidacionCalidad(any(), anyLong());
    }

    @Test
    void liberacionRepiteGateBajoBloqueoYNoMutaElExpediente() {
        BatchRecord record = recordConLote(EstadoBatchRecord.PENDIENTE_REVISION, 2L);
        User actor = actor(8L);
        BloqueoControlDTO bloqueo = new BloqueoControlDTO(
                44L, "PC-1", "Peso final", AmbitoControl.PROCESO,
                EstadoControlRequerido.PENDIENTE, PuntoExigenciaControl.ENVIO_CALIDAD,
                "Control de peso pendiente");
        when(controlWorkflowService.validarBloqueosLiberacionParaDecision(record))
                .thenReturn(List.of(bloqueo));
        when(recordRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(record));

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.registrarDecisionCalidad(
                        record, actor, DecisionCalidadBatchRecord.LIBERAR,
                        "Liberar", null, "127.0.0.1", "test-agent"));

        assertTrue(error.getMessage().contains("Control de peso pendiente"));
        assertEquals(EstadoBatchRecord.PENDIENTE_REVISION, record.getEstado());
        assertEquals(EstadoCalidadLote.CUARENTENA,
                record.getLoteResultado().getEstadoCalidad());
        verify(controlWorkflowService).validarBloqueosLiberacionParaDecision(record);
        verify(cicloRepo, never()).save(any());
    }

    @Test
    void liberacionDefinitivaRechazaDesviacionNeutralAbierta() {
        BatchRecord record = recordConLote(EstadoBatchRecord.PENDIENTE_REVISION, 2L);
        User actor = actor(8L);
        when(controlWorkflowService.validarBloqueosLiberacionParaDecision(record))
                .thenReturn(List.of());
        when(recordRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(record));
        org.mockito.Mockito.doThrow(new IllegalStateException(
                        "El lote conserva desviaciones de controles sin cierre"))
                .when(controlWorkflowService)
                .validarSinDesviacionesAbiertasParaDecision(record);

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.registrarDecisionCalidad(
                        record, actor, DecisionCalidadBatchRecord.LIBERAR,
                        "Liberar", null, "127.0.0.1", "test-agent"));

        assertTrue(error.getMessage().contains("desviaciones de controles"));
        assertEquals(EstadoBatchRecord.PENDIENTE_REVISION, record.getEstado());
        assertEquals(EstadoCalidadLote.CUARENTENA,
                record.getLoteResultado().getEstadoCalidad());
        verify(cicloRepo, never()).save(any());
    }

    @Test
    void devolucionMarcaEnsayosParaRevalidarDesdeElInicioDeLaCorreccion() {
        BatchRecord record = recordConLote(EstadoBatchRecord.PENDIENTE_REVISION, 2L);
        User actor = actor(8L);
        CicloRevisionBatchRecord ciclo = new CicloRevisionBatchRecord();
        ciclo.setBatchRecord(record);
        ciclo.setNumero(2L);
        ciclo.setEstado(EstadoCicloRevisionBatchRecord.EN_REVISION);
        BatchRecordRevision revision = revision(record, actor);
        when(recordRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(record));
        when(cicloRepo.findByBatchRecord_IdAndNumero(1L, 2L)).thenReturn(Optional.of(ciclo));
        doReturn(revision).when(service).crearRevision(
                eq(record), eq(TipoRevisionBatchRecord.DECISION_CALIDAD), eq(actor), anyString());

        service.registrarDecisionCalidad(
                record, actor, DecisionCalidadBatchRecord.DEVOLVER_A_PRODUCCION,
                "Corregir etapa", "{\"etapaIds\":[21]}", "127.0.0.1", "test-agent");

        assertEquals(EstadoBatchRecord.DEVUELTO_PRODUCCION, record.getEstado());
        verify(controlWorkflowService).prepararRevalidacionCalidad(record, 3L);
    }

    @Test
    void reaperturaDeRechazoExigeAprobadorDistinto() {
        BatchRecord record = recordConLote(EstadoBatchRecord.RECHAZADO, 3L);
        User solicitante = actor(7L);
        SolicitudReaperturaRechazo solicitud = solicitud(record, solicitante);
        when(reaperturaRepo.findById(51L)).thenReturn(Optional.of(solicitud));
        when(recordRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(record));
        when(reaperturaRepo.findByIdForUpdate(51L)).thenReturn(Optional.of(solicitud));
        when(cicloRepo.findByBatchRecord_IdAndNumero(1L, 3L))
                .thenReturn(Optional.of(cicloRechazado(record)));

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.aprobarReaperturaRechazo(
                        1L, 51L, solicitante, "Aprobada", "127.0.0.1", "test-agent"));

        assertTrue(error.getMessage().contains("distinto del solicitante"));
        assertEquals(EstadoBatchRecord.RECHAZADO, record.getEstado());
        assertEquals(EstadoSolicitudReaperturaRechazo.PENDIENTE, solicitud.getEstado());
        verify(loteRepo, never()).save(any());
    }

    @Test
    void aprobacionExcepcionalConservaRechazoHistoricoYDevuelveLoteACuarentena() {
        BatchRecord record = recordConLote(EstadoBatchRecord.RECHAZADO, 3L);
        User solicitante = actor(7L);
        User aprobador = actor(8L);
        SolicitudReaperturaRechazo solicitud = solicitud(record, solicitante);
        BatchRecordRevision revision = revision(record, aprobador);
        when(reaperturaRepo.findById(51L)).thenReturn(Optional.of(solicitud));
        when(recordRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(record));
        when(reaperturaRepo.findByIdForUpdate(51L)).thenReturn(Optional.of(solicitud));
        when(cicloRepo.findByBatchRecord_IdAndNumero(1L, 3L))
                .thenReturn(Optional.of(cicloRechazado(record)));
        when(reaperturaRepo.save(any(SolicitudReaperturaRechazo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(revision).when(service).crearRevision(
                eq(record), eq(TipoRevisionBatchRecord.REAPERTURA_RECHAZO),
                eq(aprobador), anyString());

        SolicitudReaperturaRechazo resultado = service.aprobarReaperturaRechazo(
                1L, 51L, aprobador, "Se autoriza corrección controlada",
                "127.0.0.1", "test-agent");

        assertEquals(EstadoSolicitudReaperturaRechazo.APROBADA, resultado.getEstado());
        assertEquals(aprobador, resultado.getAprobadaPor());
        assertEquals(EstadoBatchRecord.DEVUELTO_PRODUCCION, record.getEstado());
        assertEquals(EstadoCalidadLote.CUARENTENA,
                record.getLoteResultado().getEstadoCalidad());
        assertEquals(3L, resultado.getCicloRevisionNumero());
        assertEquals(revision, resultado.getRevisionAprobacion());
        verify(controlWorkflowService).prepararRevalidacionCalidad(record, 4L);
    }

    private BatchRecord record(EstadoBatchRecord estado, long ciclo) {
        BatchRecord record = new BatchRecord();
        record.setId(1L);
        record.setEstado(estado);
        record.setCicloRevisionActual(ciclo);
        return record;
    }

    private BatchRecord recordConLote(EstadoBatchRecord estado, long ciclo) {
        BatchRecord record = record(estado, ciclo);
        Lote lote = new Lote();
        lote.setId(12L);
        lote.setBatchNumber("LOT-12");
        lote.setEstadoCalidad(estado == EstadoBatchRecord.RECHAZADO
                ? EstadoCalidadLote.RECHAZADO
                : EstadoCalidadLote.CUARENTENA);
        record.setLoteResultado(lote);
        return record;
    }

    private BatchRecordRevision revision(BatchRecord record, User actor) {
        BatchRecordRevision revision = new BatchRecordRevision();
        revision.setId(70L);
        revision.setBatchRecord(record);
        revision.setCreadaPor(actor);
        revision.setContenidoSha256("a".repeat(64));
        return revision;
    }

    private SolicitudReaperturaRechazo solicitud(BatchRecord record, User solicitante) {
        SolicitudReaperturaRechazo solicitud = new SolicitudReaperturaRechazo();
        solicitud.setId(51L);
        solicitud.setBatchRecord(record);
        solicitud.setCicloRevisionNumero(record.getCicloRevisionActual());
        solicitud.setEstado(EstadoSolicitudReaperturaRechazo.PENDIENTE);
        solicitud.setSolicitadaPor(solicitante);
        solicitud.setSolicitadaEn(java.time.LocalDateTime.of(2026, 9, 2, 9, 0));
        solicitud.setMotivo("Reabrir para corregir");
        solicitud.setEvidencia("Evidencia documentada");
        solicitud.setAlcance("Etapa de mezclado");
        return solicitud;
    }

    private CicloRevisionBatchRecord cicloRechazado(BatchRecord record) {
        CicloRevisionBatchRecord ciclo = new CicloRevisionBatchRecord();
        ciclo.setBatchRecord(record);
        ciclo.setNumero(record.getCicloRevisionActual());
        ciclo.setEstado(EstadoCicloRevisionBatchRecord.RECHAZADO);
        return ciclo;
    }

    private User actor(Long id) {
        User actor = new User();
        actor.setId(id);
        actor.setUsername("responsable" + id);
        actor.setCedula(1000L + id);
        return actor;
    }
}
