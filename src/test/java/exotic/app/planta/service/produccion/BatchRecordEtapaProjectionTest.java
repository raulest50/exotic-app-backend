package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.calidad.ControlProcesoPlantilla;
import exotic.app.planta.model.calidad.EstadoControlProcesoPlantilla;
import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.model.produccion.TipoEventoSeguimiento;
import exotic.app.planta.model.produccion.batchrecord.*;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacionEvento;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoEjecucionRepo;
import exotic.app.planta.repo.calidad.ControlProcesoPlantillaRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordEtapaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordFirmaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordCorreccionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRevisionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordConsumoRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordDesviacionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordDecisionCalidadRepo;
import exotic.app.planta.repo.produccion.batchrecord.CicloRevisionBatchRecordRepo;
import exotic.app.planta.repo.produccion.batchrecord.SolicitudReaperturaRechazoRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordSeccionCorreccionRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
import exotic.app.planta.service.controles.ControlWorkflowService;
import exotic.app.planta.service.empresa.EmpresaLogoDocumentalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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

    @Test
    void etapaFreepassConservaTrazabilidadOmitidaSinCongelarControlNiInventarFirma() {
        var fixture = fixture(false);
        fixture.seguimiento.setAplicaEjecucionOp(false);
        fixture.seguimiento.setEstado(SeguimientoOrdenArea.ESTADO_OMITIDO);
        SeguimientoOrdenAreaRepo seguimientos = mock(SeguimientoOrdenAreaRepo.class);
        ControlProcesoPlantillaRepo plantillas = mock(ControlProcesoPlantillaRepo.class);
        when(seguimientos.findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(881))
                .thenReturn(List.of(fixture.seguimiento));
        when(fixture.etapas.findBySeguimientoOrdenArea_Id(1L)).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(fixture.service, "seguimientoRepo", seguimientos);
        ReflectionTestUtils.setField(fixture.service, "plantillaRepo", plantillas);

        ReflectionTestUtils.invokeMethod(fixture.service, "crearEtapasDesdeSeguimiento",
                fixture.record, fixture.record.getOrdenProduccion());

        assertEquals(1, fixture.record.getEtapas().size());
        BatchRecordEtapa etapa = fixture.record.getEtapas().getFirst();
        assertEquals(EstadoBatchRecordEtapa.OMITIDA, etapa.getEstado());
        assertSame(fixture.seguimiento, etapa.getSeguimientoOrdenArea());
        assertNull(etapa.getControlProcesoPlantilla());
        assertNull(etapa.getReportadaPor());
        verifyNoInteractions(plantillas, fixture.firmas);
    }

    @Test
    void snapshotHistoricoNullSigueCongelandoSuPlantilla() {
        var fixture = fixture(false);
        fixture.seguimiento.setAplicaEjecucionOp(null);
        fixture.seguimiento.getAreaOperativa().setAlcanceEjecucion(
                AreaOperativa.AlcanceEjecucion.SOLO_OF);
        SeguimientoOrdenAreaRepo seguimientos = mock(SeguimientoOrdenAreaRepo.class);
        ControlProcesoPlantillaRepo plantillas = mock(ControlProcesoPlantillaRepo.class);
        ControlProcesoPlantilla plantilla = new ControlProcesoPlantilla();
        plantilla.setAreaOperativa(fixture.seguimiento.getAreaOperativa());
        when(seguimientos.findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(881))
                .thenReturn(List.of(fixture.seguimiento));
        when(fixture.etapas.findBySeguimientoOrdenArea_Id(1L)).thenReturn(Optional.empty());
        when(plantillas.findFirstByAreaOperativa_AreaIdAndEstado(
                13, EstadoControlProcesoPlantilla.VIGENTE)).thenReturn(Optional.of(plantilla));
        ReflectionTestUtils.setField(fixture.service, "seguimientoRepo", seguimientos);
        ReflectionTestUtils.setField(fixture.service, "plantillaRepo", plantillas);

        ReflectionTestUtils.invokeMethod(fixture.service, "crearEtapasDesdeSeguimiento",
                fixture.record, fixture.record.getOrdenProduccion());

        assertSame(plantilla, fixture.record.getEtapas().getFirst().getControlProcesoPlantilla());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replayDeEtapaFirmadaNoRetrocedeEstadoNiRepiteFirmaDeOpUOf(boolean fabricacion) {
        var fixture = fixture(fabricacion);
        LocalDateTime cierreEn = LocalDateTime.of(2026, 11, 19, 11, 0);
        User actor = new User();
        actor.setId(3L);
        fixture.etapa.setEstado(EstadoBatchRecordEtapa.COMPLETADA);
        fixture.etapa.setIniciadaEn(cierreEn.minusHours(1));
        fixture.etapa.setCompletadaEn(cierreEn);
        fixture.etapa.setReportadaPor(actor);
        fixture.etapa.setContenidoSha256("a".repeat(64));
        fixture.record.setEstado(EstadoBatchRecord.CERRADO);
        BatchRecordFirma firma = new BatchRecordFirma();
        firma.setHashContenidoFirmado("a".repeat(64));
        fixture.record.getFirmas().add(firma);

        if (fabricacion) {
            var cierre = eventoOf(fixture, 3L, cierreEn, SeguimientoOrdenArea.ESTADO_COMPLETADO);
            cierre.setActorTipo(ActorTipoEventoSeguimiento.USER);
            cierre.setTipoEvento(TipoEventoSeguimiento.OPERATIVO);
            cierre.setUsuario(actor);
            fixture.etapa.setOrdenFabricacionEventoOrigen(cierre);
            for (int vuelta = 0; vuelta < 2; vuelta++) {
                fixture.service.sincronizarEventoFabricacion(eventoOf(fixture, 1L,
                        cierreEn.minusHours(1), SeguimientoOrdenArea.ESTADO_ESPERA));
                // Desempate por ID: ambos eventos pueden tener la misma marca de tiempo.
                fixture.service.sincronizarEventoFabricacion(eventoOf(fixture, 2L,
                        cierreEn, SeguimientoOrdenArea.ESTADO_EN_PROCESO));
                fixture.service.sincronizarEventoFabricacion(cierre);
            }
            assertSame(cierre, fixture.etapa.getOrdenFabricacionEventoOrigen());
        } else {
            var cierre = eventoOp(fixture, 3L, cierreEn, SeguimientoOrdenArea.ESTADO_COMPLETADO);
            cierre.setActorTipo(ActorTipoEventoSeguimiento.USER);
            cierre.setTipoEvento(TipoEventoSeguimiento.OPERATIVO);
            cierre.setUsuario(actor);
            fixture.etapa.setSeguimientoEventoOrigen(cierre);
            for (int vuelta = 0; vuelta < 2; vuelta++) {
                fixture.service.sincronizarEventoSeguimiento(eventoOp(fixture, 1L,
                        cierreEn.minusHours(1), SeguimientoOrdenArea.ESTADO_ESPERA));
                fixture.service.sincronizarEventoSeguimiento(eventoOp(fixture, 2L,
                        cierreEn, SeguimientoOrdenArea.ESTADO_EN_PROCESO));
                fixture.service.sincronizarEventoSeguimiento(cierre);
            }
            assertSame(cierre, fixture.etapa.getSeguimientoEventoOrigen());
        }

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(fixture.etapa, "validarInvariantes"));
        assertEquals(EstadoBatchRecordEtapa.COMPLETADA, fixture.etapa.getEstado());
        assertEquals(EstadoBatchRecord.CERRADO, fixture.record.getEstado());
        assertEquals(cierreEn, fixture.etapa.getCompletadaEn());
        assertSame(actor, fixture.etapa.getReportadaPor());
        assertEquals("a".repeat(64), fixture.etapa.getContenidoSha256());
        assertEquals(List.of(firma), fixture.record.getFirmas());
        verify(fixture.etapas, never()).saveAndFlush(any());
        verifyNoInteractions(fixture.firmas, fixture.records);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unaCorreccionYaMaterializadaNoResucitaElCierreAnteriorAlReintentar(boolean fabricacion) {
        var fixture = fixture(fabricacion);
        LocalDateTime corregidaEn = LocalDateTime.of(2026, 11, 19, 12, 0);
        fixture.record.setEstado(EstadoBatchRecord.EN_CORRECCION);
        fixture.etapa.setEstado(EstadoBatchRecordEtapa.PENDIENTE);
        BatchRecordCorreccion correccion = new BatchRecordCorreccion();
        correccion.setEtapa(fixture.etapa);
        if (fabricacion) {
            var eventoCorreccion = eventoOf(fixture, 4L, corregidaEn, SeguimientoOrdenArea.ESTADO_ESPERA);
            eventoCorreccion.setTipoEvento(TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA);
            correccion.setOrdenFabricacionEventoCorreccion(eventoCorreccion);
            when(fixture.correcciones.findByBatchRecord_IdOrderByCorregidaEnAscIdAsc(5L))
                    .thenReturn(List.of(correccion));
            fixture.service.sincronizarEventoFabricacion(eventoOf(fixture, 3L,
                    corregidaEn.minusHours(1), SeguimientoOrdenArea.ESTADO_COMPLETADO));
            fixture.service.sincronizarEventoFabricacion(eventoCorreccion);
        } else {
            var eventoCorreccion = eventoOp(fixture, 4L, corregidaEn, SeguimientoOrdenArea.ESTADO_ESPERA);
            eventoCorreccion.setTipoEvento(TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA);
            correccion.setEventoCorreccion(eventoCorreccion);
            when(fixture.correcciones.findByBatchRecord_IdOrderByCorregidaEnAscIdAsc(5L))
                    .thenReturn(List.of(correccion));
            fixture.service.sincronizarEventoSeguimiento(eventoOp(fixture, 3L,
                    corregidaEn.minusHours(1), SeguimientoOrdenArea.ESTADO_COMPLETADO));
            fixture.service.sincronizarEventoSeguimiento(eventoCorreccion);
        }

        assertEquals(EstadoBatchRecordEtapa.PENDIENTE, fixture.etapa.getEstado());
        assertEquals(EstadoBatchRecord.EN_CORRECCION, fixture.record.getEstado());
        assertNull(fixture.etapa.getSeguimientoEventoOrigen());
        assertNull(fixture.etapa.getOrdenFabricacionEventoOrigen());
        verify(fixture.etapas, never()).saveAndFlush(any());
        verify(fixture.correcciones, never()).saveAndFlush(any());
        verifyNoInteractions(fixture.firmas, fixture.records);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nuevaRevisionDeCierreCongelaEstadoCerradoYFechaSinReescribirLaRevisionPrevia(
            boolean fabricacion) throws Exception {
        var fixture = fixture(fabricacion);
        Clock clock = Clock.fixed(Instant.parse("2026-11-19T15:30:00Z"), ZoneOffset.UTC);
        LocalDateTime cierreEsperado = LocalDateTime.of(2026, 11, 19, 15, 30);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("responsable-cierre");
        actor.setNombreCompleto("Responsable Cierre");
        actor.setCedula(7L);
        Producto producto = fabricacion ? new SemiTerminado() : new Terminado();
        producto.setProductoId("SEMI-CIERRE");
        producto.setNombre("Base cierre");
        ManufacturingVersions version = new ManufacturingVersions();
        version.setId(10L);
        version.setVersionNumber(1);
        Lote lote = new Lote();
        lote.setId(11L);
        lote.setBatchNumber("LOTE-CIERRE");
        lote.setProducto(producto);
        if (fabricacion) {
            fixture.record.getOrdenFabricacion().setSemiTerminado((SemiTerminado) producto);
            lote.setOrdenFabricacion(fixture.record.getOrdenFabricacion());
        } else {
            fixture.record.getOrdenProduccion().setProducto((Terminado) producto);
            lote.setOrdenProduccion(fixture.record.getOrdenProduccion());
        }
        fixture.record.setCodigo("BR-CIERRE-5");
        fixture.record.setEstado(EstadoBatchRecord.LISTO_PARA_REVISION);
        fixture.record.setCantidadPlanificada(BigDecimal.ONE);
        fixture.record.setCantidadObtenida(BigDecimal.ONE);
        fixture.record.setUnidadMedida("KG");
        fixture.record.setProductoResultado(producto);
        fixture.record.setManufacturingVersion(version);
        fixture.record.setLoteResultado(lote);
        fixture.record.setCreadoPor(actor);
        when(fixture.records.findByIdForUpdate(5L)).thenReturn(Optional.of(fixture.record));
        BatchRecordRevision anterior = new BatchRecordRevision();
        anterior.setNumero(3);
        anterior.setContenidoCanonico("{\"estado\":\"LISTO_PARA_REVISION\",\"cerradoEn\":null}");
        anterior.setContenidoSha256("b".repeat(64));
        BatchRecordRevisionRepo revisiones = mock(BatchRecordRevisionRepo.class);
        when(revisiones.findTopByBatchRecord_IdOrderByNumeroDesc(5L)).thenReturn(Optional.of(anterior));
        EmpresaLogoDocumentalVersion logo = new EmpresaLogoDocumentalVersion();
        logo.setId(1L);
        logo.setVersion(1);
        logo.setSha256("logo-fixture");
        EmpresaLogoDocumentalService logos = mock(EmpresaLogoDocumentalService.class);
        when(logos.getVigente()).thenReturn(logo);
        ReflectionTestUtils.setField(fixture.service, "revisionRepo", revisiones);
        ReflectionTestUtils.setField(fixture.service, "objectMapper", mapper);
        ReflectionTestUtils.setField(fixture.service, "applicationClock", clock);
        ReflectionTestUtils.setField(fixture.service, "empresaLogoDocumentalService", logos);
        ReflectionTestUtils.setField(fixture.service, "consumoRepo", mock(BatchRecordConsumoRepo.class));
        ReflectionTestUtils.setField(fixture.service, "desviacionRepo", mock(BatchRecordDesviacionRepo.class));
        ReflectionTestUtils.setField(fixture.service, "decisionRepo", mock(BatchRecordDecisionCalidadRepo.class));
        ReflectionTestUtils.setField(fixture.service, "cicloRevisionRepo", mock(CicloRevisionBatchRecordRepo.class));
        ReflectionTestUtils.setField(fixture.service, "solicitudReaperturaRepo", mock(SolicitudReaperturaRechazoRepo.class));
        ReflectionTestUtils.setField(fixture.service, "seccionCorreccionRepo", mock(BatchRecordSeccionCorreccionRepo.class));
        ReflectionTestUtils.setField(fixture.service, "ejecucionRepo", mock(ControlProcesoEjecucionRepo.class));
        ReflectionTestUtils.setField(fixture.service, "seguimientoRepo", mock(SeguimientoOrdenAreaRepo.class));
        ReflectionTestUtils.setField(fixture.service, "ordenFabricacionOperacionRepo", mock(OrdenFabricacionOperacionRepo.class));
        ReflectionTestUtils.setField(fixture.service, "controlWorkflowService", mock(ControlWorkflowService.class));
        ReflectionTestUtils.setField(fixture.service, "despejeLineaService", mock(DespejeLineaService.class));

        fixture.service.cerrarDocumentalmente(5L, actor);
        fixture.service.cerrarDocumentalmente(5L, actor);

        ArgumentCaptor<BatchRecordRevision> guardada = ArgumentCaptor.forClass(BatchRecordRevision.class);
        verify(revisiones).saveAndFlush(guardada.capture());
        BatchRecordRevision cierre = guardada.getValue();
        JsonNode canonico = mapper.readTree(cierre.getContenidoCanonico());
        assertEquals(TipoRevisionBatchRecord.CIERRE, cierre.getTipo());
        assertEquals(4, cierre.getNumero());
        assertEquals("CERRADO", canonico.path("estado").asText());
        assertEquals(cierreEsperado, LocalDateTime.parse(canonico.path("cerradoEn").asText()));
        assertEquals(cierreEsperado, fixture.record.getCerradoEn());
        assertEquals("{\"estado\":\"LISTO_PARA_REVISION\",\"cerradoEn\":null}", anterior.getContenidoCanonico());
        assertEquals("b".repeat(64), anterior.getContenidoSha256());
        verify(revisiones, never()).saveAndFlush(anterior);
    }

    private Fixture fixture(boolean fabricacion) {
        BatchRecordService service = mock(BatchRecordService.class, CALLS_REAL_METHODS);
        BatchRecordEtapaRepo etapas = mock(BatchRecordEtapaRepo.class);
        BatchRecordRepo records = mock(BatchRecordRepo.class);
        BatchRecordFirmaRepo firmas = mock(BatchRecordFirmaRepo.class);
        BatchRecordCorreccionRepo correcciones = mock(BatchRecordCorreccionRepo.class);
        ReflectionTestUtils.setField(service, "etapaRepo", etapas);
        ReflectionTestUtils.setField(service, "batchRecordRepo", records);
        ReflectionTestUtils.setField(service, "firmaRepo", firmas);
        ReflectionTestUtils.setField(service, "correccionRepo", correcciones);
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(13);
        area.setNombre("Fabricacion");
        BatchRecord record = new BatchRecord();
        record.setId(5L);
        BatchRecordEtapa etapa = new BatchRecordEtapa();
        etapa.setId(6L);
        etapa.setBatchRecord(record);
        etapa.setAreaOperativa(area);
        etapa.setNombre("Fabricacion");
        SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
        seguimiento.setId(1L);
        seguimiento.setAreaOperativa(area);
        OrdenFabricacionOperacion operacion = new OrdenFabricacionOperacion();
        operacion.setId(2L);
        operacion.setAreaOperativa(area);
        if (fabricacion) {
            OrdenFabricacion orden = new OrdenFabricacion();
            orden.setOrdenFabricacionId(882L);
            record.setOrdenFabricacion(orden);
            operacion.setOrdenFabricacion(orden);
            etapa.setOrdenFabricacionOperacion(operacion);
            when(etapas.findByOrdenFabricacionOperacion_Id(2L)).thenReturn(Optional.of(etapa));
        } else {
            OrdenProduccion orden = new OrdenProduccion();
            orden.setOrdenId(881);
            record.setOrdenProduccion(orden);
            seguimiento.setOrdenProduccion(orden);
            etapa.setSeguimientoOrdenArea(seguimiento);
            when(etapas.findBySeguimientoOrdenArea_Id(1L)).thenReturn(Optional.of(etapa));
        }
        // Reproduce la validacion real que IDENTITY/flush ejecuta sobre cada estado intermedio.
        when(etapas.saveAndFlush(any())).thenAnswer(invocation -> {
            BatchRecordEtapa guardada = invocation.getArgument(0);
            ReflectionTestUtils.invokeMethod(guardada, "validarInvariantes");
            return guardada;
        });
        return new Fixture(service, etapas, records, firmas, correcciones, record, etapa, seguimiento, operacion);
    }

    private SeguimientoOrdenAreaEvento eventoOp(Fixture fixture, long id, LocalDateTime fecha, int estado) {
        SeguimientoOrdenAreaEvento evento = new SeguimientoOrdenAreaEvento();
        evento.setId(id);
        evento.setSeguimientoOrdenArea(fixture.seguimiento);
        evento.setFechaEvento(fecha);
        evento.setEstadoDestino(estado);
        return evento;
    }

    private OrdenFabricacionOperacionEvento eventoOf(Fixture fixture, long id, LocalDateTime fecha, int estado) {
        OrdenFabricacionOperacionEvento evento = new OrdenFabricacionOperacionEvento();
        evento.setId(id);
        evento.setOperacion(fixture.operacion);
        evento.setFechaEvento(fecha);
        evento.setEstadoDestino(estado);
        return evento;
    }

    private record Fixture(BatchRecordService service, BatchRecordEtapaRepo etapas, BatchRecordRepo records,
                           BatchRecordFirmaRepo firmas, BatchRecordCorreccionRepo correcciones,
                           BatchRecord record, BatchRecordEtapa etapa, SeguimientoOrdenArea seguimiento,
                           OrdenFabricacionOperacion operacion) {}
}
