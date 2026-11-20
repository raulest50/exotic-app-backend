package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.model.produccion.TipoEventoSeguimiento;
import exotic.app.planta.model.produccion.dto.ReporteProduccionEtapaDTOs.Configuracion;
import exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.empresa.JornadaLaboralVersionRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionDocumentoVersionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatVersionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import jakarta.persistence.EntityManager;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.TableroOperativoDTO;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.TableroVista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SeguimientoOrdenAreaServiceTableroFabricacionTest {

    private static final Long USER_ID = 45L;
    private static final int COMPLETADO = EstadoSeguimientoOrdenArea.COMPLETADO.getCode();
    private static final List<Integer> ACTIVE_STATES = List.of(
            EstadoSeguimientoOrdenArea.COLA.getCode(),
            EstadoSeguimientoOrdenArea.ESPERA.getCode(),
            EstadoSeguimientoOrdenArea.EN_PROCESO.getCode()
    );

    private SeguimientoOrdenAreaRepo seguimientoRepo;
    private OrdenFabricacionOperacionRepo fabricacionRepo;
    private LoteRepo loteRepo;
    private MasterDirectiveService masterDirectiveService;
    private SeguimientoOrdenAreaEventoRepo seguimientoEventoRepo;
    private DespejeLineaService despejeLineaService;
    private OrdenProduccionRepo ordenProduccionRepo;
    private UserRepository userRepository;
    private ReporteProduccionEtapaService reporteEtapaService;
    private SeguimientoOrdenAreaService service;

    @BeforeEach
    void setUp() {
        seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        fabricacionRepo = mock(OrdenFabricacionOperacionRepo.class);
        loteRepo = mock(LoteRepo.class);
        masterDirectiveService = mock(MasterDirectiveService.class);
        seguimientoEventoRepo = mock(SeguimientoOrdenAreaEventoRepo.class);
        despejeLineaService = mock(DespejeLineaService.class);
        ordenProduccionRepo = mock(OrdenProduccionRepo.class);
        userRepository = mock(UserRepository.class);
        reporteEtapaService = mock(ReporteProduccionEtapaService.class);
        service = new SeguimientoOrdenAreaService(
                seguimientoRepo,
                seguimientoEventoRepo,
                mock(AreaProduccionRepo.class),
                mock(RutaProcesoCatVersionRepo.class),
                mock(JornadaLaboralVersionRepo.class),
                mock(RutaProcesoEstimacionService.class),
                mock(ReporteProduccionLoteService.class),
                userRepository,
                masterDirectiveService,
                mock(BatchRecordProjectionQueueService.class),
                despejeLineaService,
                mock(ProcesoProduccionDocumentoVersionRepo.class),
                fabricacionRepo,
                loteRepo,
                Clock.fixed(
                        Instant.parse("2026-07-16T14:00:00Z"),
                        ZoneId.of("America/Bogota")
                ),
                ordenProduccionRepo,
                mock(TransaccionAlmacenRepo.class),
                mock(MaterialRequirementSnapshotService.class),
                mock(EntityManager.class),
                reporteEtapaService
        );

        when(seguimientoRepo.findTableroActivosByResponsableUserId(USER_ID, ACTIVE_STATES))
                .thenReturn(List.of());
        when(fabricacionRepo.findActivasPorResponsable(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(ACTIVE_STATES),
                anyList()))
                .thenReturn(List.of());
        when(masterDirectiveService.isBatchRecordWorkflowEnabled()).thenReturn(true);
        when(seguimientoRepo.save(any(SeguimientoOrdenArea.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(seguimientoEventoRepo.saveAndFlush(any(SeguimientoOrdenAreaEvento.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void historicoConsultaFabricacionSinParametrosDeFecha() {
        PageRequest firstPage = PageRequest.of(0, 20);
        when(seguimientoRepo.findTableroCompletadosHistoricosByResponsableUserId(
                USER_ID, COMPLETADO, "", firstPage))
                .thenReturn(Page.empty(firstPage));
        OrdenFabricacionOperacion operacion = operacionFabricacionCompletada();
        when(fabricacionRepo.findCompletadasHistoricasPorResponsable(
                USER_ID, COMPLETADO, ""))
                .thenReturn(List.of(operacion));
        when(fabricacionRepo
                .findByOrdenFabricacion_OrdenFabricacionIdOrderByPosicionSecuenciaAsc(71L))
                .thenReturn(List.of(operacion));
        when(loteRepo.findByOrdenFabricacion_OrdenFabricacionId(71L)).thenReturn(List.of());

        TableroOperativoDTO result = service.getTableroOperativoUsuario(
                USER_ID, TableroVista.HISTORICO);

        assertThat(result.getPeriodStartDate()).isNull();
        assertThat(result.getPeriodEndDate()).isNull();
        assertThat(result.getCompletado()).hasSize(1);
        assertThat(result.getCompletado().get(0).getTipoOrden()).isEqualTo("OF");
        assertThat(result.getResumen().getCompletado()).isEqualTo(1L);
        assertThat(result.getPaginacionCompletadas().getTotalElements()).isEqualTo(1L);
        verify(fabricacionRepo).findCompletadasHistoricasPorResponsable(
                USER_ID, COMPLETADO, "");
        verify(fabricacionRepo, never()).findCompletadasPorResponsableEnRango(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void hoyConsultaFabricacionConRangoBogotaNoNulo() {
        LocalDateTime desde = LocalDateTime.of(2026, 7, 16, 0, 0);
        LocalDateTime hasta = LocalDateTime.of(2026, 7, 17, 0, 0);
        when(seguimientoRepo
                .findTableroCompletadosByResponsableUserIdAndFechaCompletadoBetween(
                        USER_ID, COMPLETADO, desde, hasta))
                .thenReturn(List.of());
        when(fabricacionRepo.findCompletadasPorResponsableEnRango(
                USER_ID, COMPLETADO, desde, hasta, ""))
                .thenReturn(List.of());

        TableroOperativoDTO result = service.getTableroOperativoUsuario(
                USER_ID, TableroVista.HOY);

        assertThat(result.getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 7, 16));
        assertThat(result.getPeriodEndDate()).isEqualTo(LocalDate.of(2026, 7, 16));
        verify(fabricacionRepo).findCompletadasPorResponsableEnRango(
                USER_ID, COMPLETADO, desde, hasta, "");
        verify(fabricacionRepo, never()).findCompletadasHistoricasPorResponsable(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void semanaActualConservaRangoLunesALunesExclusivo() {
        LocalDateTime desde = LocalDateTime.of(2026, 7, 13, 0, 0);
        LocalDateTime hasta = LocalDateTime.of(2026, 7, 20, 0, 0);
        when(seguimientoRepo
                .findTableroCompletadosByResponsableUserIdAndFechaCompletadoBetween(
                        USER_ID, COMPLETADO, desde, hasta))
                .thenReturn(List.of());
        when(fabricacionRepo.findCompletadasPorResponsableEnRango(
                USER_ID, COMPLETADO, desde, hasta, ""))
                .thenReturn(List.of());

        TableroOperativoDTO result = service.getTableroOperativoUsuario(
                USER_ID, TableroVista.SEMANA_ACTUAL);

        assertThat(result.getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 7, 13));
        assertThat(result.getPeriodEndDate()).isEqualTo(LocalDate.of(2026, 7, 19));
        verify(fabricacionRepo).findCompletadasPorResponsableEnRango(
                USER_ID, COMPLETADO, desde, hasta, "");
    }

    @Test
    void directivaDesactivadaNoConsultaOperacionesDeFabricacion() {
        when(masterDirectiveService.isBatchRecordWorkflowEnabled()).thenReturn(false);
        PageRequest firstPage = PageRequest.of(0, 20);
        when(seguimientoRepo.findTableroCompletadosHistoricosByResponsableUserId(
                USER_ID, COMPLETADO, "", firstPage))
                .thenReturn(Page.empty(firstPage));

        service.getTableroOperativoUsuario(USER_ID, TableroVista.HISTORICO);

        verifyNoInteractions(fabricacionRepo);
    }

    @Test
    void nuevaOpCongelaAlcanceYOmiteCadenaOfAlLiberarseAlmacen() {
        OrdenProduccion orden = ordenProduccion();
        RutaProcesoNode almacen = nodo(1L, -1, AreaOperativa.AlcanceEjecucion.AMBOS);
        RutaProcesoNode fabricacionUno = nodo(2L, 2, AreaOperativa.AlcanceEjecucion.SOLO_OF);
        RutaProcesoNode fabricacionDos = nodo(3L, 3, AreaOperativa.AlcanceEjecucion.SOLO_OF);
        RutaProcesoNode envasado = nodo(4L, 4, AreaOperativa.AlcanceEjecucion.SOLO_OP);
        ruta(orden, List.of(almacen, fabricacionUno, fabricacionDos, envasado),
                conexion(almacen, fabricacionUno), conexion(fabricacionUno, fabricacionDos),
                conexion(fabricacionDos, envasado));
        Configuracion sinReporte = new Configuracion(false, List.of());
        when(reporteEtapaService.congelarOp(orden, orden.getRutaProcesoCatVersion()))
                .thenReturn(Map.of(1L, sinReporte, 2L, sinReporte, 3L, sinReporte, 4L, sinReporte));
        List<SeguimientoOrdenArea> creados = new ArrayList<>();
        when(seguimientoRepo.save(any(SeguimientoOrdenArea.class))).thenAnswer(invocation -> {
            SeguimientoOrdenArea seguimiento = invocation.getArgument(0);
            seguimiento.setId(seguimiento.getRutaProcesoNode().getId() + 100);
            creados.add(seguimiento);
            return seguimiento;
        });

        service.inicializarSeguimiento(orden);

        assertThat(creados).hasSize(4);
        SeguimientoOrdenArea pasoAlmacen = creados.get(0);
        SeguimientoOrdenArea omitidoUno = creados.get(1);
        SeguimientoOrdenArea omitidoDos = creados.get(2);
        SeguimientoOrdenArea pasoEnvasado = creados.get(3);
        assertThat(omitidoUno.getAplicaEjecucionOp()).isFalse();
        assertThat(omitidoDos.getAplicaEjecucionOp()).isFalse();
        assertThat(pasoEnvasado.getAplicaEjecucionOp()).isTrue();
        assertThat(pasoEnvasado.getConfiguracionReporteProduccion().requerido()).isFalse();
        assertThat(omitidoUno.getDuracionEstimadaMinutos()).isZero();
        assertThat(omitidoUno.isRequiereJornadaLaboral()).isFalse();
        assertThat(omitidoUno.getPoeDocumentoVersion()).isNull();
        assertThat(omitidoUno.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.COLA);

        // Un cambio posterior de configuracion no redefine una orden emitida.
        fabricacionUno.getAreaOperativa().setAlcanceEjecucion(AreaOperativa.AlcanceEjecucion.AMBOS);
        when(seguimientoRepo.findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(
                orden.getOrdenId(), -1, SeguimientoOrdenArea.ESTADO_ESPERA))
                .thenReturn(Optional.of(pasoAlmacen));
        when(seguimientoRepo.findSucesoresPendientes(orden.getOrdenId(), 1L)).thenReturn(List.of(omitidoUno));
        when(seguimientoRepo.findSucesoresPendientes(orden.getOrdenId(), 2L)).thenReturn(List.of(omitidoDos));
        when(seguimientoRepo.findSucesoresPendientes(orden.getOrdenId(), 3L)).thenReturn(List.of(pasoEnvasado));

        service.autoCompletarAlmacenGeneralPorDispensacion(orden.getOrdenId(), null, "Dispensado");

        assertThat(pasoAlmacen.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.COMPLETADO);
        assertThat(omitidoUno.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.OMITIDO);
        assertThat(omitidoDos.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.OMITIDO);
        assertThat(pasoEnvasado.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.ESPERA);
        assertThat(omitidoUno.getFechaCompletado()).isNull();
        assertThat(omitidoUno.getUsuarioReporta()).isNull();
        verify(despejeLineaService).registrarSalida(pasoAlmacen, null);
        verify(despejeLineaService, never()).registrarSalida(eq(omitidoUno), any());
        verify(despejeLineaService, never()).registrarSalida(eq(omitidoDos), any());
        ArgumentCaptor<SeguimientoOrdenAreaEvento> eventos = ArgumentCaptor.forClass(SeguimientoOrdenAreaEvento.class);
        verify(seguimientoEventoRepo, org.mockito.Mockito.atLeastOnce()).saveAndFlush(eventos.capture());
        assertThat(eventos.getAllValues()).filteredOn(evento ->
                        evento.getEstadoDestino() == SeguimientoOrdenArea.ESTADO_OMITIDO)
                .hasSize(2)
                .allSatisfy(evento -> {
                    assertThat(evento.getActorTipo()).isEqualTo(ActorTipoEventoSeguimiento.SYSTEM);
                    assertThat(evento.getTipoEvento()).isEqualTo(TipoEventoSeguimiento.SISTEMA);
                });
    }

    @Test
    void omisionNoSaltaJoinMientrasOtroPredecesorSigaPendiente() {
        OrdenProduccion orden = ordenProduccion();
        SeguimientoOrdenArea almacen = paso(orden, 1L, -1, true, EstadoSeguimientoOrdenArea.ESPERA);
        SeguimientoOrdenArea soloOf = paso(orden, 2L, 2, false, EstadoSeguimientoOrdenArea.COLA);
        SeguimientoOrdenArea otroPredecesor = paso(orden, 3L, 3, true, EstadoSeguimientoOrdenArea.EN_PROCESO);
        SeguimientoOrdenArea union = paso(orden, 4L, 4, false, EstadoSeguimientoOrdenArea.COLA);
        SeguimientoOrdenArea posterior = paso(orden, 5L, 5, true, EstadoSeguimientoOrdenArea.COLA);
        ruta(orden, List.of(almacen.getRutaProcesoNode(), soloOf.getRutaProcesoNode(),
                        otroPredecesor.getRutaProcesoNode(), union.getRutaProcesoNode(), posterior.getRutaProcesoNode()),
                conexion(almacen.getRutaProcesoNode(), soloOf.getRutaProcesoNode()),
                conexion(almacen.getRutaProcesoNode(), otroPredecesor.getRutaProcesoNode()),
                conexion(soloOf.getRutaProcesoNode(), union.getRutaProcesoNode()),
                conexion(otroPredecesor.getRutaProcesoNode(), union.getRutaProcesoNode()),
                conexion(union.getRutaProcesoNode(), posterior.getRutaProcesoNode()));
        when(seguimientoRepo.findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(
                orden.getOrdenId(), -1, SeguimientoOrdenArea.ESTADO_ESPERA)).thenReturn(Optional.of(almacen));
        when(seguimientoRepo.findSucesoresPendientes(orden.getOrdenId(), 1L)).thenReturn(List.of(soloOf));
        when(seguimientoRepo.findSucesoresPendientes(orden.getOrdenId(), 2L)).thenReturn(List.of(union));
        when(seguimientoRepo.countPredecesoresNoCompletados(orden.getOrdenId(), 4L)).thenReturn(1L);

        service.autoCompletarAlmacenGeneralPorDispensacion(orden.getOrdenId(), null, "Dispensado");

        assertThat(soloOf.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.OMITIDO);
        assertThat(otroPredecesor.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.EN_PROCESO);
        assertThat(union.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.COLA);
        assertThat(posterior.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.COLA);
        verify(seguimientoRepo, never()).findSucesoresPendientes(orden.getOrdenId(), 4L);
        verify(despejeLineaService, never()).registrarSalida(eq(union), any());
    }

    @Test
    void seguimientoHistoricoSinSnapshotConservaTrabajoAunqueAreaAhoraSeaSoloOf() {
        OrdenProduccion orden = ordenProduccion();
        SeguimientoOrdenArea almacen = paso(orden, 1L, -1, null, EstadoSeguimientoOrdenArea.ESPERA);
        SeguimientoOrdenArea historico = paso(orden, 2L, 2, null, EstadoSeguimientoOrdenArea.COLA);
        historico.getAreaOperativa().setAlcanceEjecucion(AreaOperativa.AlcanceEjecucion.SOLO_OF);
        when(seguimientoRepo.findByOrdenProduccion_OrdenIdAndAreaOperativa_AreaIdAndEstado(
                orden.getOrdenId(), -1, SeguimientoOrdenArea.ESTADO_ESPERA)).thenReturn(Optional.of(almacen));
        when(seguimientoRepo.findSucesoresPendientes(orden.getOrdenId(), 1L)).thenReturn(List.of(historico));

        service.autoCompletarAlmacenGeneralPorDispensacion(orden.getOrdenId(), null, "Dispensado");

        assertThat(historico.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.ESPERA);
        assertThat(historico.getAplicaEjecucionOp()).isNull();
        verify(seguimientoRepo, never()).findSucesoresPendientes(orden.getOrdenId(), 2L);
        verify(despejeLineaService, never()).registrarSalida(eq(historico), any());
    }

    @Test
    void cancelacionReconciliaSoloPasosAplicablesIncluidoHistorico() {
        OrdenProduccion orden = ordenProduccion();
        SeguimientoOrdenArea actual = paso(orden, 1L, 1, true, EstadoSeguimientoOrdenArea.EN_PROCESO);
        SeguimientoOrdenArea historico = paso(orden, 2L, 2, null, EstadoSeguimientoOrdenArea.ESPERA);
        SeguimientoOrdenArea excluido = paso(orden, 3L, 3, false, EstadoSeguimientoOrdenArea.OMITIDO);
        User actor = new User();
        when(seguimientoRepo.findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(orden.getOrdenId()))
                .thenReturn(List.of(actual, historico, excluido));

        service.reconciliarDespejesPorCancelacion(orden.getOrdenId(), actor);

        verify(despejeLineaService).reconciliarCorreccion(actual, actor);
        verify(despejeLineaService).reconciliarCorreccion(historico, actor);
        verify(despejeLineaService, never()).reconciliarCorreccion(excluido, actor);
    }

    @Test
    void correccionDePredecesorReabreOmisionAutomaticaSinCrearTrabajoFisicoEnElla() {
        OrdenProduccion orden = ordenProduccion();
        SeguimientoOrdenArea almacen = paso(orden, 1L, -1, true, EstadoSeguimientoOrdenArea.COMPLETADO);
        SeguimientoOrdenArea previo = paso(orden, 2L, 2, true, EstadoSeguimientoOrdenArea.COMPLETADO);
        SeguimientoOrdenArea omitido = paso(orden, 3L, 3, false, EstadoSeguimientoOrdenArea.OMITIDO);
        SeguimientoOrdenArea posterior = paso(orden, 4L, 4, true, EstadoSeguimientoOrdenArea.ESPERA);
        ruta(orden, List.of(almacen.getRutaProcesoNode(), previo.getRutaProcesoNode(),
                        omitido.getRutaProcesoNode(), posterior.getRutaProcesoNode()),
                conexion(almacen.getRutaProcesoNode(), previo.getRutaProcesoNode()),
                conexion(previo.getRutaProcesoNode(), omitido.getRutaProcesoNode()),
                conexion(omitido.getRutaProcesoNode(), posterior.getRutaProcesoNode()));
        User actor = new User();
        actor.setId(USER_ID);
        SeguimientoOrdenAreaEvento eventoOmision = new SeguimientoOrdenAreaEvento();
        eventoOmision.setId(903L);
        eventoOmision.setSeguimientoOrdenArea(omitido);
        eventoOmision.setEstadoDestino(SeguimientoOrdenArea.ESTADO_OMITIDO);
        eventoOmision.setActorTipo(ActorTipoEventoSeguimiento.SYSTEM);
        eventoOmision.setTipoEvento(TipoEventoSeguimiento.SISTEMA);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(actor));
        when(seguimientoRepo.findOrdenIdById(previo.getId())).thenReturn(Optional.of(orden.getOrdenId()));
        when(ordenProduccionRepo.findByIdForUpdate(orden.getOrdenId())).thenReturn(Optional.of(orden));
        when(seguimientoRepo.findByIdForUpdate(previo.getId())).thenReturn(Optional.of(previo));
        when(seguimientoRepo.findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(orden.getOrdenId()))
                .thenReturn(List.of(almacen, previo, omitido, posterior));
        when(seguimientoEventoRepo.findEventosActualesNoRevertidos(omitido.getId(), SeguimientoOrdenArea.ESTADO_OMITIDO))
                .thenReturn(List.of(eventoOmision));

        service.corregirEstadoAdministrativamente(previo.getAreaOperativa().getAreaId(), previo.getId(),
                SeguimientoOrdenArea.ESTADO_COMPLETADO, SeguimientoOrdenArea.ESTADO_ESPERA,
                USER_ID, "Se corrige cierre prematuro");

        assertThat(previo.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.ESPERA);
        assertThat(omitido.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.COLA);
        assertThat(omitido.getAplicaEjecucionOp()).isFalse();
        assertThat(omitido.getFechaVisible()).isNull();
        assertThat(posterior.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.COLA);
        verify(despejeLineaService).reconciliarCorreccion(previo, actor);
        verify(despejeLineaService).reconciliarCorreccion(posterior, actor);
        verify(despejeLineaService, never()).reconciliarCorreccion(omitido, actor);
        ArgumentCaptor<SeguimientoOrdenAreaEvento> eventos = ArgumentCaptor.forClass(SeguimientoOrdenAreaEvento.class);
        verify(seguimientoEventoRepo, org.mockito.Mockito.atLeastOnce()).saveAndFlush(eventos.capture());
        assertThat(eventos.getAllValues()).filteredOn(evento -> evento.getSeguimientoOrdenArea() == omitido)
                .singleElement().satisfies(evento -> {
                    assertThat(evento.getEstadoDestino()).isEqualTo(SeguimientoOrdenArea.ESTADO_COLA);
                    assertThat(evento.getTipoEvento()).isEqualTo(TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA);
                    assertThat(evento.getEventoRevertido()).isSameAs(eventoOmision);
                });
    }

    private static OrdenProduccion ordenProduccion() {
        Categoria categoria = new Categoria();
        categoria.setCategoriaId(10);
        Terminado producto = new Terminado();
        producto.setProductoId("PT-PRUEBA");
        producto.setNombre("Producto terminado de prueba");
        producto.setCategoria(categoria);
        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(80);
        orden.setProducto(producto);
        return orden;
    }

    private static RutaProcesoNode nodo(Long nodeId, int areaId, AreaOperativa.AlcanceEjecucion alcance) {
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(areaId);
        area.setNombre("Area " + areaId);
        area.setAlcanceEjecucion(alcance);
        RutaProcesoNode nodo = new RutaProcesoNode();
        nodo.setId(nodeId);
        nodo.setAreaOperativa(area);
        nodo.setDuracionEstimadaMinutos(120);
        nodo.setRequiereJornadaLaboral(true);
        return nodo;
    }

    private static SeguimientoOrdenArea paso(OrdenProduccion orden, Long nodeId, int areaId,
                                             Boolean aplica, EstadoSeguimientoOrdenArea estado) {
        RutaProcesoNode nodo = nodo(nodeId, areaId, AreaOperativa.AlcanceEjecucion.AMBOS);
        SeguimientoOrdenArea paso = new SeguimientoOrdenArea();
        paso.setId(nodeId + 100);
        paso.setOrdenProduccion(orden);
        paso.setRutaProcesoNode(nodo);
        paso.setAreaOperativa(nodo.getAreaOperativa());
        paso.setAplicaEjecucionOp(aplica);
        paso.setEstadoEnum(estado);
        paso.setFechaEstadoActual(LocalDateTime.of(2026, 7, 16, 8, 0));
        paso.setPosicionSecuencia(nodeId.intValue());
        return paso;
    }

    private static RutaProcesoEdge conexion(RutaProcesoNode origen, RutaProcesoNode destino) {
        RutaProcesoEdge edge = new RutaProcesoEdge();
        edge.setSourceNode(origen);
        edge.setTargetNode(destino);
        return edge;
    }

    private static void ruta(OrdenProduccion orden, List<RutaProcesoNode> nodos, RutaProcesoEdge... conexiones) {
        RutaProcesoCatVersion version = new RutaProcesoCatVersion();
        version.setId(10L);
        version.setNodes(nodos);
        version.setEdges(List.of(conexiones));
        nodos.forEach(nodo -> nodo.setRutaProcesoCatVersion(version));
        version.getEdges().forEach(edge -> edge.setRutaProcesoCatVersion(version));
        orden.setRutaProcesoCatVersion(version);
    }

    private static OrdenFabricacionOperacion operacionFabricacionCompletada() {
        SemiTerminado semiterminado = new SemiTerminado();
        semiterminado.setProductoId("SEMIT-001");
        semiterminado.setNombre("Semiterminado de prueba");

        OrdenFabricacion orden = new OrdenFabricacion();
        orden.setOrdenFabricacionId(71L);
        orden.setSemiTerminado(semiterminado);
        orden.setEstado(EstadoOrdenFabricacion.EN_EJECUCION);
        orden.setCantidadPlanificada(new BigDecimal("100.0000"));
        orden.setUnidadMedida("KG");

        AreaOperativa area = new AreaOperativa();
        area.setAreaId(3);
        area.setNombre("FABRICACION 3");

        OrdenFabricacionOperacion operacion = new OrdenFabricacionOperacion();
        operacion.setId(701L);
        operacion.setOrdenFabricacion(orden);
        operacion.setAreaOperativa(area);
        operacion.setFrontendNodeId("node-1");
        operacion.setProcesoNombre("Fabricacion");
        operacion.setPosicionSecuencia(0);
        operacion.setEstadoEnum(EstadoSeguimientoOrdenArea.COMPLETADO);
        operacion.setFechaEstadoActual(LocalDateTime.of(2026, 7, 16, 8, 0));
        operacion.setFechaCompletado(LocalDateTime.of(2026, 7, 16, 9, 0));
        return operacion;
    }
}
