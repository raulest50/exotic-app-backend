package exotic.app.planta.service.controles;

import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import exotic.app.planta.model.calidad.ControlProcesoPlantilla;
import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.controles.dto.ControlDTOs.*;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.*;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ControlExecutionServiceTest {
    private ControlRequeridoRepo requeridoRepo;
    private EjecucionControlRepo ejecucionRepo;
    private DesviacionControlRepo desviacionRepo;
    private BatchRecordRepo batchRecordRepo;
    private LegacyControlExecutionProjection legacyProjection;
    private ControlExecutionService service;
    private ControlRequerido requerido;
    private User actor;

    @BeforeEach
    void setUp() {
        requeridoRepo = mock(ControlRequeridoRepo.class);
        ejecucionRepo = mock(EjecucionControlRepo.class);
        desviacionRepo = mock(DesviacionControlRepo.class);
        batchRecordRepo = mock(BatchRecordRepo.class);
        legacyProjection = mock(LegacyControlExecutionProjection.class);
        service = new ControlExecutionService(requeridoRepo, ejecucionRepo, desviacionRepo,
                mock(RevalidacionControlRepo.class), mock(ControlPlanService.class),
                batchRecordRepo, legacyProjection);
        requerido = requisito();
        actor = new User(); actor.setId(9L); actor.setUsername("operario"); actor.setNombreCompleto("Operario");
        when(requeridoRepo.findByIdForUpdate(30L)).thenReturn(Optional.of(requerido));
        when(requeridoRepo.findById(30L)).thenReturn(Optional.of(requerido));
        when(ejecucionRepo.findByControlRequerido_IdOrderByFechaRegistroDescIdDesc(30L))
                .thenReturn(List.of());
        when(ejecucionRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            EjecucionControl e = invocation.getArgument(0); e.setId(40L); return e;
        });
        when(desviacionRepo.findByEjecucionOrigen_Id(any())).thenReturn(Optional.empty());
    }

    @Test
    void ejecutar_aceptaLimitesInclusivosYBooleanoEsperadoFalse() {
        EjecucionWriteRequest request = new EjecucionWriteRequest(30L, null, null, null, List.of(
                new MuestraWriteRequest(11L, 1, List.of(
                        new LecturaWriteRequest(1, new BigDecimal("1.00000000"), null),
                        new LecturaWriteRequest(2, new BigDecimal("2.00000000"), null))),
                new MuestraWriteRequest(12L, 1, List.of(
                        new LecturaWriteRequest(1, null, false)))));

        EjecucionDetalleResponse response = service.ejecutar(AmbitoControl.PROCESO, actor, request);

        assertEquals(ResultadoEjecucionControl.CONFORME, response.resumen().resultado());
        assertEquals(EstadoControlRequerido.CONFORME, requerido.getEstado());
        assertTrue(response.muestras().stream().flatMap(m -> m.lecturas().stream())
                .allMatch(LecturaResponse::conforme));
        verify(desviacionRepo, never()).save(any());
    }

    @Test
    void ejecutar_noConformeAbreDesviacionYConservaResultado() {
        EjecucionWriteRequest request = new EjecucionWriteRequest(30L, "Fuera de rango", null, null, List.of(
                new MuestraWriteRequest(11L, 1, List.of(
                        new LecturaWriteRequest(1, new BigDecimal("0.99"), null),
                        new LecturaWriteRequest(2, new BigDecimal("2.00000000"), null))),
                new MuestraWriteRequest(12L, 1, List.of(
                        new LecturaWriteRequest(1, null, false)))));

        EjecucionDetalleResponse response = service.ejecutar(AmbitoControl.PROCESO, actor, request);

        assertEquals(ResultadoEjecucionControl.NO_CONFORME, response.resumen().resultado());
        assertEquals(EstadoControlRequerido.NO_CONFORME, requerido.getEstado());
        verify(desviacionRepo).save(any(DesviacionControl.class));
    }

    @Test
    void ejecutar_rechazaMatrizIncompleta() {
        EjecucionWriteRequest request = new EjecucionWriteRequest(30L, null, null, null, List.of(
                new MuestraWriteRequest(11L, 1, List.of(
                        new LecturaWriteRequest(1, BigDecimal.ONE, null)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.ejecutar(AmbitoControl.PROCESO, actor, request));
        verify(ejecucionRepo, never()).saveAndFlush(any());
    }

    @Test
    void ejecutar_rechazaDecimalesQueExcedenEscalaVisiblePeroAceptaCerosFinales() {
        EjecucionWriteRequest excedida = new EjecucionWriteRequest(30L, null, null, null, List.of(
                new MuestraWriteRequest(11L, 1, List.of(
                        new LecturaWriteRequest(1, new BigDecimal("1.234"), null),
                        new LecturaWriteRequest(2, new BigDecimal("2.00"), null))),
                new MuestraWriteRequest(12L, 1, List.of(
                        new LecturaWriteRequest(1, null, false)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.ejecutar(AmbitoControl.PROCESO, actor, excedida));
    }

    @Test
    void ejecutar_noPermiteEludirRechazoTerminalConRetest() {
        when(desviacionRepo.existsByControlRequerido_IdAndEstadoAndDisposicion(
                30L, EstadoDesviacionControl.CERRADA, DisposicionDesviacionControl.RECHAZAR))
                .thenReturn(true);
        EjecucionWriteRequest request = new EjecucionWriteRequest(30L, null, null, null, List.of());
        assertThrows(IllegalStateException.class,
                () -> service.ejecutar(AmbitoControl.PROCESO, actor, request));
    }

    @Test
    void ejecutar_rechazaEscrituraDesdeLaFachadaDelOtroAmbito() {
        EjecucionWriteRequest request = ejecucionConforme(null, null);

        assertThrows(java.util.NoSuchElementException.class,
                () -> service.ejecutar(AmbitoControl.CALIDAD, actor, request));

        verify(ejecucionRepo, never()).saveAndFlush(any());
        verify(desviacionRepo, never()).save(any());
        assertEquals(EstadoControlRequerido.PENDIENTE, requerido.getEstado());
    }

    @Test
    void repetirEsAppendOnlyYUnResultadoConformeNoOcultaLaDesviacionAbierta() {
        EjecucionControl anterior = new EjecucionControl();
        anterior.setId(39L);
        anterior.setControlRequerido(requerido);
        anterior.setUsuario(actor);
        anterior.setResultado(ResultadoEjecucionControl.NO_CONFORME);
        requerido.setEstado(EstadoControlRequerido.NO_CONFORME);
        when(ejecucionRepo.findByControlRequerido_IdOrderByFechaRegistroDescIdDesc(30L))
                .thenReturn(List.of(anterior));
        when(desviacionRepo.existsByControlRequerido_IdAndEstadoNot(
                30L, EstadoDesviacionControl.CERRADA)).thenReturn(true);
        AtomicReference<EjecucionControl> nueva = new AtomicReference<>();
        doAnswer(invocation -> {
            EjecucionControl ejecucion = invocation.getArgument(0);
            ejecucion.setId(40L);
            nueva.set(ejecucion);
            return ejecucion;
        }).when(ejecucionRepo).saveAndFlush(any());

        EjecucionDetalleResponse response = service.ejecutar(
                AmbitoControl.PROCESO, actor, ejecucionConforme(39L, "Balanza verificada"));

        assertEquals(ResultadoEjecucionControl.CONFORME, response.resumen().resultado());
        assertSame(anterior, nueva.get().getRepeticionDe());
        assertEquals("Balanza verificada", nueva.get().getMotivoRepeticion());
        assertEquals(ResultadoEjecucionControl.NO_CONFORME, anterior.getResultado());
        assertEquals(EstadoControlRequerido.NO_CONFORME, requerido.getEstado(),
                "La desviacion abierta conserva el requisito como no conforme");
        assertSame(requerido, nueva.get().getControlRequerido());
    }

    @Test
    void ejecutarEnCorreccionSoloPermiteControlSeleccionadoDelCicloVigente() {
        BatchRecord record = new BatchRecord();
        record.setId(70L);
        record.setEstado(EstadoBatchRecord.EN_CORRECCION);
        record.setCicloRevisionActual(2L);
        requerido.setBatchRecord(record);
        when(batchRecordRepo.findByIdForUpdate(70L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class,
                () -> service.ejecutar(AmbitoControl.PROCESO, actor, ejecucionConforme(null, null)));
        verify(ejecucionRepo, never()).saveAndFlush(any());

        requerido.setRequiereRepeticion(true);
        requerido.setCicloRevisionNumero(2);
        EjecucionDetalleResponse response = service.ejecutar(
                AmbitoControl.PROCESO, actor, ejecucionConforme(null, null));

        assertEquals(ResultadoEjecucionControl.CONFORME, response.resumen().resultado());
        verify(ejecucionRepo).saveAndFlush(any(EjecucionControl.class));
    }

    @Test
    void ensayoDuranteFabricacionNoPuedeRegistrarseInicialmenteTrasElEnvio() {
        BatchRecord record = new BatchRecord();
        record.setId(70L);
        record.setEstado(EstadoBatchRecord.PENDIENTE_REVISION);
        requerido.setBatchRecord(record);
        requerido.setAmbitoSnapshot(AmbitoControl.CALIDAD);
        requerido.setMomentoSnapshot(MomentoControl.DURANTE_FABRICACION);
        requerido.setPuntoExigenciaSnapshot(PuntoExigenciaControl.ENVIO_CALIDAD);
        when(batchRecordRepo.findByIdForUpdate(70L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class,
                () -> service.ejecutar(AmbitoControl.CALIDAD, actor, ejecucionConforme(null, null)));

        verify(ejecucionRepo, never()).saveAndFlush(any());
        assertEquals(EstadoControlRequerido.PENDIENTE, requerido.getEstado());
    }

    @Test
    void ensayoDuranteFabricacionExigibleEnLiberacionNoPuedeDocumentarseRetrospectivamente() {
        BatchRecord record = new BatchRecord();
        record.setId(70L);
        record.setEstado(EstadoBatchRecord.PENDIENTE_REVISION);
        requerido.setBatchRecord(record);
        requerido.setAmbitoSnapshot(AmbitoControl.CALIDAD);
        requerido.setMomentoSnapshot(MomentoControl.DURANTE_FABRICACION);
        requerido.setPuntoExigenciaSnapshot(PuntoExigenciaControl.LIBERACION);
        when(batchRecordRepo.findByIdForUpdate(70L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class,
                () -> service.ejecutar(AmbitoControl.CALIDAD, actor, ejecucionConforme(null, null)));

        verify(ejecucionRepo, never()).saveAndFlush(any());
        assertEquals(EstadoControlRequerido.PENDIENTE, requerido.getEstado());
    }

    @Test
    void ensayoDuranteFabricacionPuedeRecuperarseDuranteCorreccion() {
        BatchRecord record = new BatchRecord();
        record.setId(70L);
        record.setEstado(EstadoBatchRecord.EN_CORRECCION);
        requerido.setBatchRecord(record);
        requerido.setAmbitoSnapshot(AmbitoControl.CALIDAD);
        requerido.setMomentoSnapshot(MomentoControl.DURANTE_FABRICACION);
        requerido.setPuntoExigenciaSnapshot(PuntoExigenciaControl.LIBERACION);
        when(batchRecordRepo.findByIdForUpdate(70L)).thenReturn(Optional.of(record));

        EjecucionDetalleResponse response = service.ejecutar(
                AmbitoControl.CALIDAD, actor, ejecucionConforme(null, null));

        assertEquals(ResultadoEjecucionControl.CONFORME, response.resumen().resultado());
        assertEquals(EstadoControlRequerido.CONFORME, requerido.getEstado());
    }

    @Test
    void ejecutarVersionMigrada_creaEspejoLegadoEnLaMismaEjecucion() {
        ControlProcesoPlantilla legacyPlan = new ControlProcesoPlantilla();
        legacyPlan.setId(80L);
        requerido.getVersionPlan().setLegacyPlantilla(legacyPlan);
        ControlProcesoEjecucion mirror = new ControlProcesoEjecucion();
        mirror.setId(90L);
        when(legacyProjection.createMirror(eq(requerido), eq(actor), any(),
                eq(ResultadoEjecucionControl.CONFORME), isNull(), anyList()))
                .thenReturn(mirror);
        AtomicReference<EjecucionControl> saved = new AtomicReference<>();
        doAnswer(invocation -> {
            EjecucionControl execution = invocation.getArgument(0);
            execution.setId(40L);
            saved.set(execution);
            return execution;
        }).when(ejecucionRepo).saveAndFlush(any());

        service.ejecutar(AmbitoControl.PROCESO, actor, ejecucionConforme(null, null));

        assertSame(mirror, saved.get().getLegacyEjecucion());
        verify(legacyProjection).createMirror(eq(requerido), eq(actor), any(),
                eq(ResultadoEjecucionControl.CONFORME), isNull(), anyList());
    }

    @Test
    void ejecutarDesdeLegado_vinculaSinCrearSegundoEspejo() {
        ControlProcesoPlantilla legacyPlan = new ControlProcesoPlantilla();
        legacyPlan.setId(80L);
        requerido.getVersionPlan().setLegacyPlantilla(legacyPlan);
        requerido.setOrigen(OrigenControlRequerido.LEGACY);
        ControlProcesoEjecucion legacyExecution = new ControlProcesoEjecucion();
        legacyExecution.setId(90L);
        legacyExecution.setPlantilla(legacyPlan);
        legacyExecution.setLote(requerido.getLote());
        legacyExecution.setUsuario(actor);
        LocalDateTime legacyTimestamp = LocalDateTime.of(2026, 9, 2, 12, 30);
        legacyExecution.setFechaRegistro(legacyTimestamp);
        when(ejecucionRepo.findByLegacyEjecucion_Id(90L)).thenReturn(Optional.empty());
        AtomicReference<EjecucionControl> saved = new AtomicReference<>();
        doAnswer(invocation -> {
            EjecucionControl execution = invocation.getArgument(0);
            execution.setId(40L);
            saved.set(execution);
            return execution;
        }).when(ejecucionRepo).saveAndFlush(any());

        service.ejecutarDesdeLegado(AmbitoControl.PROCESO, actor,
                ejecucionConforme(null, null), legacyExecution);

        assertSame(legacyExecution, saved.get().getLegacyEjecucion());
        assertSame(legacyExecution, requerido.getLegacyEjecucion());
        assertEquals(legacyTimestamp, saved.get().getFechaRegistro());
        verifyNoInteractions(legacyProjection);
    }

    private EjecucionWriteRequest ejecucionConforme(Long repeticionDeId, String motivo) {
        return new EjecucionWriteRequest(30L, null, repeticionDeId, motivo, List.of(
                new MuestraWriteRequest(11L, 1, List.of(
                        new LecturaWriteRequest(1, new BigDecimal("1.00"), null),
                        new LecturaWriteRequest(2, new BigDecimal("2.00"), null))),
                new MuestraWriteRequest(12L, 1, List.of(
                        new LecturaWriteRequest(1, null, false)))));
    }

    private ControlRequerido requisito() {
        PlanControl plan = new PlanControl(); plan.setId(1L); plan.setCodigo("PC-1");
        plan.setNombre("Control"); plan.setAmbito(AmbitoControl.PROCESO);
        VersionPlanControl version = new VersionPlanControl(); version.setId(2L); version.setPlan(plan);
        version.setNumero(1); version.setProposito("AJUSTE_PROCESO");

        CaracteristicaPlanControl numerica = new CaracteristicaPlanControl();
        numerica.setId(11L); numerica.setVersion(version); numerica.setNombre("Peso");
        numerica.setTipo(TipoCaracteristicaControl.NUMERICA); numerica.setCantidadMuestras(1);
        numerica.setEscalaVisible(2);
        numerica.setUnidadesPorMuestra(2); numerica.setLimiteInferior(new BigDecimal("1.00000000"));
        numerica.setLimiteSuperior(new BigDecimal("2.00000000")); numerica.setUnidadSimboloSnapshot("g");
        CaracteristicaPlanControl booleana = new CaracteristicaPlanControl();
        booleana.setId(12L); booleana.setVersion(version); booleana.setNombre("Defecto presente");
        booleana.setTipo(TipoCaracteristicaControl.BOOLEANA); booleana.setCantidadMuestras(1);
        booleana.setEscalaVisible(0);
        booleana.setUnidadesPorMuestra(1); booleana.setValorBooleanoEsperado(false);
        version.getCaracteristicas().addAll(List.of(numerica, booleana));

        Lote lote = new Lote(); lote.setId(20L); lote.setBatchNumber("L-20");
        ControlRequerido result = new ControlRequerido(); result.setId(30L); result.setVersionPlan(version);
        result.setLote(lote); result.setAmbitoSnapshot(AmbitoControl.PROCESO);
        result.setPlanCodigoSnapshot("PC-1"); result.setPlanNombreSnapshot("Control");
        result.setVersionNumeroSnapshot(1); result.setTipoOrdenSnapshot(TipoOrdenControl.OP);
        result.setEstado(EstadoControlRequerido.PENDIENTE);
        return result;
    }
}
