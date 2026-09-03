package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordFirma;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordRevision;
import exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.*;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordEtapaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ControlWorkflowServiceTest {

    @Test
    void publicacionPosteriorNoAlcanzaLoteYaExistente() {
        VersionPlanControlRepo versionRepo = mock(VersionPlanControlRepo.class);
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        LoteRepo loteRepo = mock(LoteRepo.class);
        BatchRecordRepo batchRepo = mock(BatchRecordRepo.class);
        ControlWorkflowService service = new ControlWorkflowService(versionRepo, requeridoRepo,
                mock(DesviacionControlRepo.class), loteRepo, batchRepo,
                mock(BatchRecordEtapaRepo.class), mock(SeguimientoOrdenAreaRepo.class),
                mock(OrdenFabricacionOperacionRepo.class), mock(RevalidacionControlRepo.class));

        Terminado producto = new Terminado();
        producto.setProductoId("PT-1");
        producto.setNombre("Producto");
        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(7);
        orden.setProducto(producto);
        orden.setFechaCreacion(LocalDateTime.of(2026, 1, 10, 8, 0));
        orden.setManufacturingVersion(manufactura(71L));
        Lote lote = new Lote();
        lote.setId(9L);
        lote.setBatchNumber("L-9");
        lote.setProducto(producto);
        lote.setOrdenProduccion(orden);

        PlanControl plan = new PlanControl();
        plan.setId(1L);
        plan.setCodigo("PC-1");
        plan.setNombre("Control");
        plan.setAmbito(AmbitoControl.PROCESO);
        VersionPlanControl anterior = version(plan, 11L, 1,
                LocalDateTime.of(2025, 12, 1, 8, 0),
                LocalDateTime.of(2026, 2, 1, 8, 0), producto);
        VersionPlanControl posterior = version(plan, 12L, 2,
                LocalDateTime.of(2026, 2, 1, 8, 0), null, producto);

        when(loteRepo.findById(9L)).thenReturn(Optional.of(lote));
        when(batchRepo.findByLoteResultado_Id(9L)).thenReturn(Optional.empty());
        when(versionRepo.findByEstadoIn(any())).thenReturn(List.of(anterior, posterior));
        when(requeridoRepo.findByLote_IdAndOrigenAndAmbitoSnapshotOrderByIdAsc(
                9L, OrigenControlRequerido.INDEPENDIENTE, AmbitoControl.PROCESO)).thenReturn(List.of());

        service.resolverIndependientes(AmbitoControl.PROCESO, 9L);

        var captor = org.mockito.ArgumentCaptor.forClass(ControlRequerido.class);
        verify(requeridoRepo, times(1)).save(captor.capture());
        assertSame(anterior, captor.getValue().getVersionPlan());
    }

    @Test
    void aplicabilidadEsOrEntreReglasDeduplicaCadaPlanYAcumulaPlanesDistintos() {
        VersionPlanControlRepo versionRepo = mock(VersionPlanControlRepo.class);
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        LoteRepo loteRepo = mock(LoteRepo.class);
        BatchRecordRepo batchRepo = mock(BatchRecordRepo.class);
        ControlWorkflowService service = new ControlWorkflowService(versionRepo, requeridoRepo,
                mock(DesviacionControlRepo.class), loteRepo, batchRepo,
                mock(BatchRecordEtapaRepo.class), mock(SeguimientoOrdenAreaRepo.class),
                mock(OrdenFabricacionOperacionRepo.class), mock(RevalidacionControlRepo.class));

        Categoria categoria = new Categoria();
        categoria.setCategoriaId(501);
        categoria.setCategoriaNombre("Cosmeticos");
        Terminado producto = new Terminado();
        producto.setProductoId("PT-1");
        producto.setNombre("Producto");
        producto.setCategoria(categoria);
        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(7);
        orden.setProducto(producto);
        orden.setFechaCreacion(LocalDateTime.of(2026, 1, 10, 8, 0));
        orden.setManufacturingVersion(manufactura(71L));
        Lote lote = new Lote();
        lote.setId(9L);
        lote.setBatchNumber("L-9");
        lote.setProducto(producto);
        lote.setOrdenProduccion(orden);

        VersionPlanControl peso = version(plan(1L, "PC-PESO"), 11L, 1,
                LocalDateTime.of(2025, 12, 1, 8, 0), null, producto);
        peso.getAplicabilidades().add(reglaCategoria(peso, 112L, categoria));
        VersionPlanControl viscosidad = version(plan(2L, "PC-VISC"), 21L, 1,
                LocalDateTime.of(2025, 12, 1, 8, 0), null, producto);
        viscosidad.getAplicabilidades().add(reglaCategoria(viscosidad, 212L, categoria));

        List<ControlRequerido> guardados = new ArrayList<>();
        when(loteRepo.findById(9L)).thenReturn(Optional.of(lote));
        when(batchRepo.findByLoteResultado_Id(9L)).thenReturn(Optional.empty());
        when(versionRepo.findByEstadoIn(any())).thenReturn(List.of(peso, viscosidad));
        when(requeridoRepo.save(any())).thenAnswer(invocation -> {
            ControlRequerido item = invocation.getArgument(0);
            item.setId(100L + guardados.size());
            guardados.add(item);
            return item;
        });
        when(requeridoRepo.findByLote_IdAndOrigenAndAmbitoSnapshotOrderByIdAsc(
                9L, OrigenControlRequerido.INDEPENDIENTE, AmbitoControl.PROCESO))
                .thenAnswer(invocation -> List.copyOf(guardados));

        List<ControlRequerido> result = service.resolverIndependientes(AmbitoControl.PROCESO, 9L);

        assertEquals(2, result.size(),
                "Las reglas coincidentes del mismo plan se deduplican, pero los planes se acumulan");
        Set<Long> planes = result.stream().map(r -> r.getVersionPlan().getPlan().getId())
                .collect(Collectors.toSet());
        assertEquals(Set.of(1L, 2L), planes);
        verify(requeridoRepo, times(2)).save(any(ControlRequerido.class));
    }

    @Test
    void liberacionBloqueaRevalidacionInformativaEnLecturaYEnDecision() {
        VersionPlanControlRepo versionRepo = mock(VersionPlanControlRepo.class);
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        DesviacionControlRepo desviacionRepo = mock(DesviacionControlRepo.class);
        ControlWorkflowService service = new ControlWorkflowService(versionRepo, requeridoRepo,
                desviacionRepo, mock(LoteRepo.class), mock(BatchRecordRepo.class),
                mock(BatchRecordEtapaRepo.class), mock(SeguimientoOrdenAreaRepo.class),
                mock(OrdenFabricacionOperacionRepo.class), mock(RevalidacionControlRepo.class));
        BatchRecord record = new BatchRecord();
        record.setId(77L);
        ControlRequerido ensayo = new ControlRequerido();
        ensayo.setId(88L);
        ensayo.setPlanCodigoSnapshot("EC-PH");
        ensayo.setPlanNombreSnapshot("Ensayo pH");
        ensayo.setAmbitoSnapshot(AmbitoControl.CALIDAD);
        ensayo.setPuntoExigenciaSnapshot(PuntoExigenciaControl.INFORMATIVO);
        ensayo.setEstado(EstadoControlRequerido.POR_REVALIDAR);
        ensayo.setRequiereRevalidacion(true);
        ControlRequerido marcaPersistida = new ControlRequerido();
        marcaPersistida.setId(89L);
        marcaPersistida.setPlanCodigoSnapshot("EC-VISC");
        marcaPersistida.setPlanNombreSnapshot("Ensayo viscosidad");
        marcaPersistida.setAmbitoSnapshot(AmbitoControl.CALIDAD);
        marcaPersistida.setPuntoExigenciaSnapshot(PuntoExigenciaControl.INFORMATIVO);
        marcaPersistida.setEstado(EstadoControlRequerido.CONFORME);
        marcaPersistida.setRequiereRevalidacion(true);
        ControlRequerido repeticionInformativa = new ControlRequerido();
        repeticionInformativa.setId(90L);
        repeticionInformativa.setPlanCodigoSnapshot("PC-VISUAL");
        repeticionInformativa.setPlanNombreSnapshot("Inspeccion visual");
        repeticionInformativa.setAmbitoSnapshot(AmbitoControl.PROCESO);
        repeticionInformativa.setPuntoExigenciaSnapshot(PuntoExigenciaControl.INFORMATIVO);
        repeticionInformativa.setEstado(EstadoControlRequerido.PENDIENTE);
        repeticionInformativa.setRequiereRepeticion(true);
        ControlRequerido rechazoInformativo = new ControlRequerido();
        rechazoInformativo.setId(92L);
        rechazoInformativo.setPlanCodigoSnapshot("PC-RECHAZADO");
        rechazoInformativo.setPlanNombreSnapshot("Control rechazado");
        rechazoInformativo.setAmbitoSnapshot(AmbitoControl.PROCESO);
        rechazoInformativo.setPuntoExigenciaSnapshot(PuntoExigenciaControl.INFORMATIVO);
        rechazoInformativo.setEstado(EstadoControlRequerido.NO_CONFORME);
        when(desviacionRepo.existsByControlRequerido_IdAndEstadoAndDisposicion(
                92L, EstadoDesviacionControl.CERRADA, DisposicionDesviacionControl.RECHAZAR))
                .thenReturn(true);
        when(requeridoRepo.findByBatchRecord_IdOrderByIdAsc(77L))
                .thenReturn(List.of(ensayo, marcaPersistida, repeticionInformativa, rechazoInformativo));
        when(requeridoRepo.findByBatchRecordIdForUpdate(77L))
                .thenReturn(List.of(ensayo, marcaPersistida, repeticionInformativa, rechazoInformativo));

        assertEquals(List.of(88L, 89L, 90L, 92L), service.validarBloqueosLiberacion(record).stream()
                .map(b -> b.controlRequeridoId()).toList());
        assertEquals(List.of(88L, 89L, 90L, 92L), service.validarBloqueosLiberacionParaDecision(record).stream()
                .map(b -> b.controlRequeridoId()).toList());
    }

    @Test
    void adicionExcepcionalDeProcesoEnCorreccionQuedaEjecutableEnElCicloVigente() {
        VersionPlanControlRepo versionRepo = mock(VersionPlanControlRepo.class);
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        BatchRecordRepo batchRepo = mock(BatchRecordRepo.class);
        ControlWorkflowService service = new ControlWorkflowService(versionRepo, requeridoRepo,
                mock(DesviacionControlRepo.class), mock(LoteRepo.class), batchRepo,
                mock(BatchRecordEtapaRepo.class), mock(SeguimientoOrdenAreaRepo.class),
                mock(OrdenFabricacionOperacionRepo.class), mock(RevalidacionControlRepo.class));

        Terminado producto = new Terminado();
        producto.setProductoId("PT-1");
        producto.setNombre("Producto");
        Lote lote = new Lote();
        lote.setId(9L);
        lote.setBatchNumber("L-9");
        lote.setProducto(producto);
        ManufacturingVersions manufactura = new ManufacturingVersions();
        manufactura.setId(55L);
        VersionPlanControl version = version(plan(1L, "PC-EXC"), 11L, 1,
                LocalDateTime.of(2025, 12, 1, 8, 0), null, producto);
        BatchRecord record = new BatchRecord();
        record.setId(77L);
        record.setEstado(EstadoBatchRecord.EN_CORRECCION);
        record.setCicloRevisionActual(3L);
        record.setLoteResultado(lote);
        record.setProductoResultado(producto);
        record.setManufacturingVersion(manufactura);
        User actor = new User();
        actor.setId(4L);
        actor.setUsername("director_planta");

        when(batchRepo.findByIdForUpdate(77L)).thenReturn(Optional.of(record));
        when(versionRepo.findFirstByPlan_IdAndEstado(1L, EstadoVersionPlanControl.VIGENTE))
                .thenReturn(Optional.of(version));
        when(requeridoRepo.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ControlRequerido result = service.agregarExcepcional(
                AmbitoControl.PROCESO, actor, 77L, 1L, null, "Control adicional firmado");

        assertEquals(EstadoControlRequerido.PENDIENTE, result.getEstado());
        assertEquals(3, result.getCicloRevisionNumero());
        assertEquals(true, result.isRequiereRepeticion());
    }

    @Test
    void reenvioBloqueaControlSeleccionadoPendienteDelCicloVigente() {
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        ControlWorkflowService service = new ControlWorkflowService(
                mock(VersionPlanControlRepo.class), requeridoRepo,
                mock(DesviacionControlRepo.class), mock(LoteRepo.class), mock(BatchRecordRepo.class),
                mock(BatchRecordEtapaRepo.class), mock(SeguimientoOrdenAreaRepo.class),
                mock(OrdenFabricacionOperacionRepo.class), mock(RevalidacionControlRepo.class));
        BatchRecord record = new BatchRecord();
        record.setId(77L);
        record.setCicloRevisionActual(3L);
        ControlRequerido requisito = new ControlRequerido();
        requisito.setId(91L);
        requisito.setAmbitoSnapshot(AmbitoControl.PROCESO);
        requisito.setPlanCodigoSnapshot("PC-PESO");
        requisito.setPlanNombreSnapshot("Control de peso");
        requisito.setPuntoExigenciaSnapshot(PuntoExigenciaControl.INFORMATIVO);
        requisito.setEstado(EstadoControlRequerido.PENDIENTE);
        requisito.setRequiereRepeticion(true);
        requisito.setCicloRevisionNumero(3);
        when(requeridoRepo.findByBatchRecord_IdOrderByIdAsc(77L)).thenReturn(List.of(requisito));

        assertEquals(List.of(91L), service.validarBloqueosReenvio(record).stream()
                .map(b -> b.controlRequeridoId()).toList());
    }

    @Test
    void revalidacionDeCalidadMarcadaAlDevolverNoBloqueaElReenvioQueLaHabilita() {
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        ControlWorkflowService service = new ControlWorkflowService(
                mock(VersionPlanControlRepo.class), requeridoRepo,
                mock(DesviacionControlRepo.class), mock(LoteRepo.class), mock(BatchRecordRepo.class),
                mock(BatchRecordEtapaRepo.class), mock(SeguimientoOrdenAreaRepo.class),
                mock(OrdenFabricacionOperacionRepo.class), mock(RevalidacionControlRepo.class));
        BatchRecord record = new BatchRecord();
        record.setId(77L);
        record.setEstado(EstadoBatchRecord.DEVUELTO_PRODUCCION);
        record.setCicloRevisionActual(2L);
        ControlRequerido revalidacion = new ControlRequerido();
        revalidacion.setId(91L);
        revalidacion.setAmbitoSnapshot(AmbitoControl.CALIDAD);
        revalidacion.setPlanCodigoSnapshot("EC-PH");
        revalidacion.setPlanNombreSnapshot("Ensayo pH");
        revalidacion.setPuntoExigenciaSnapshot(PuntoExigenciaControl.ENVIO_CALIDAD);
        revalidacion.setEstado(EstadoControlRequerido.POR_REVALIDAR);
        revalidacion.setRequiereRevalidacion(true);
        revalidacion.setCicloRevisionNumero(3);
        when(requeridoRepo.findByBatchRecord_IdAndPuntoExigenciaSnapshot(
                77L, PuntoExigenciaControl.ENVIO_CALIDAD)).thenReturn(List.of(revalidacion));

        assertEquals(List.of(), service.validarBloqueos(
                record, PuntoExigenciaControl.ENVIO_CALIDAD));
    }

    @Test
    void independienteNoCreaSnapshotIncompletoSinVersionDeManufactura() {
        VersionPlanControlRepo versionRepo = mock(VersionPlanControlRepo.class);
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        LoteRepo loteRepo = mock(LoteRepo.class);
        BatchRecordRepo batchRepo = mock(BatchRecordRepo.class);
        ControlWorkflowService service = new ControlWorkflowService(versionRepo, requeridoRepo,
                mock(DesviacionControlRepo.class), loteRepo, batchRepo,
                mock(BatchRecordEtapaRepo.class), mock(SeguimientoOrdenAreaRepo.class),
                mock(OrdenFabricacionOperacionRepo.class), mock(RevalidacionControlRepo.class));
        Terminado producto = new Terminado();
        producto.setProductoId("PT-LEGACY");
        producto.setNombre("Producto legado");
        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(72);
        orden.setProducto(producto);
        orden.setFechaCreacion(LocalDateTime.of(2026, 1, 10, 8, 0));
        Lote lote = new Lote();
        lote.setId(92L);
        lote.setBatchNumber("L-LEGACY");
        lote.setProducto(producto);
        lote.setOrdenProduccion(orden);
        VersionPlanControl version = version(plan(1L, "PC-PESO"), 11L, 1,
                LocalDateTime.of(2025, 12, 1, 8, 0), null, producto);
        when(loteRepo.findById(92L)).thenReturn(Optional.of(lote));
        when(batchRepo.findByLoteResultado_Id(92L)).thenReturn(Optional.empty());
        when(versionRepo.findByEstadoIn(any())).thenReturn(List.of(version));

        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.resolverIndependientes(AmbitoControl.PROCESO, 92L));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("depure su origen"));
        verify(requeridoRepo, never()).save(any());
    }

    @Test
    void revisionRegulatoriaExponeProcedenciaDeAdicionExcepcional() {
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        ControlWorkflowService service = new ControlWorkflowService(
                mock(VersionPlanControlRepo.class), requeridoRepo,
                mock(DesviacionControlRepo.class), mock(LoteRepo.class), mock(BatchRecordRepo.class),
                mock(BatchRecordEtapaRepo.class), mock(SeguimientoOrdenAreaRepo.class),
                mock(OrdenFabricacionOperacionRepo.class), mock(RevalidacionControlRepo.class));
        User actor = new User();
        actor.setUsername("calidad.admin");
        BatchRecordRevision revision = new BatchRecordRevision();
        revision.setId(501L);
        BatchRecordFirma firma = new BatchRecordFirma();
        firma.setId(601L);
        ControlRequerido requisito = new ControlRequerido();
        requisito.setId(92L);
        requisito.setAmbitoSnapshot(AmbitoControl.CALIDAD);
        requisito.setPlanCodigoSnapshot("EC-PH");
        requisito.setPlanNombreSnapshot("Ensayo de pH");
        requisito.setVersionNumeroSnapshot(2);
        requisito.setEstado(EstadoControlRequerido.PENDIENTE);
        requisito.setOrigen(OrigenControlRequerido.BATCH_RECORD);
        requisito.setPuntoAplicacionSnapshot(PuntoAplicacionControl.LOTE_FINAL);
        requisito.setMomentoSnapshot(MomentoControl.REVISION_FINAL);
        requisito.setPuntoExigenciaSnapshot(PuntoExigenciaControl.LIBERACION);
        requisito.setAgregadoExcepcionalmente(true);
        requisito.setMotivoAdicion("Seguimiento INVIMA");
        requisito.setAgregadoPor(actor);
        requisito.setRevisionAdicion(revision);
        requisito.setFirmaAdicion(firma);
        when(requeridoRepo.findByBatchRecord_IdOrderByIdAsc(77L)).thenReturn(List.of(requisito));

        var result = service.controlesCalidadPorBatchRecord(77L).getFirst();

        assertEquals(true, result.agregadoExcepcionalmente());
        assertEquals("Seguimiento INVIMA", result.motivoAdicion());
        assertEquals("calidad.admin", result.agregadoPor());
        assertEquals(501L, result.revisionAdicionId());
        assertEquals(601L, result.firmaAdicionId());
    }

    private PlanControl plan(Long id, String codigo) {
        PlanControl plan = new PlanControl();
        plan.setId(id);
        plan.setCodigo(codigo);
        plan.setNombre(codigo);
        plan.setAmbito(AmbitoControl.PROCESO);
        return plan;
    }

    private ManufacturingVersions manufactura(Long id) {
        ManufacturingVersions version = new ManufacturingVersions();
        version.setId(id);
        return version;
    }

    private AplicabilidadPlanControl reglaCategoria(
            VersionPlanControl version, Long id, Categoria categoria) {
        AplicabilidadPlanControl regla = new AplicabilidadPlanControl();
        regla.setId(id);
        regla.setVersion(version);
        regla.setCategoria(categoria);
        regla.setTipoOrden(TipoOrdenControl.AMBAS);
        regla.setPuntoAplicacion(PuntoAplicacionControl.LOTE_FINAL);
        regla.setMomento(MomentoControl.DURANTE_FABRICACION);
        regla.setPuntoExigencia(PuntoExigenciaControl.INFORMATIVO);
        return regla;
    }

    private VersionPlanControl version(PlanControl plan, Long id, int numero,
                                       LocalDateTime publicada, LocalDateTime retirada,
                                       Terminado producto) {
        VersionPlanControl version = new VersionPlanControl();
        version.setId(id);
        version.setPlan(plan);
        version.setNumero(numero);
        version.setEstado(retirada == null
                ? EstadoVersionPlanControl.VIGENTE : EstadoVersionPlanControl.RETIRADA);
        version.setPublicadaEn(publicada);
        version.setRetiradaEn(retirada);
        AplicabilidadPlanControl regla = new AplicabilidadPlanControl();
        regla.setId(id * 10);
        regla.setVersion(version);
        regla.setProducto(producto);
        regla.setTipoOrden(TipoOrdenControl.OP);
        regla.setPuntoAplicacion(PuntoAplicacionControl.LOTE_FINAL);
        regla.setMomento(MomentoControl.DURANTE_FABRICACION);
        regla.setPuntoExigencia(PuntoExigenciaControl.INFORMATIVO);
        version.getAplicabilidades().add(regla);
        return version;
    }
}
