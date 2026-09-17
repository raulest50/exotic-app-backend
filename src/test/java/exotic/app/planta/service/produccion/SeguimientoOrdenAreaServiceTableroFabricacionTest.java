package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.repo.empresa.JornadaLaboralVersionRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionDocumentoVersionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatVersionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.TableroOperativoDTO;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.TableroVista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
    private SeguimientoOrdenAreaService service;

    @BeforeEach
    void setUp() {
        seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        fabricacionRepo = mock(OrdenFabricacionOperacionRepo.class);
        loteRepo = mock(LoteRepo.class);
        masterDirectiveService = mock(MasterDirectiveService.class);
        service = new SeguimientoOrdenAreaService(
                seguimientoRepo,
                mock(SeguimientoOrdenAreaEventoRepo.class),
                mock(AreaProduccionRepo.class),
                mock(RutaProcesoCatVersionRepo.class),
                mock(JornadaLaboralVersionRepo.class),
                mock(RutaProcesoEstimacionService.class),
                mock(ReporteProduccionLoteService.class),
                mock(UserRepository.class),
                masterDirectiveService,
                mock(BatchRecordProjectionQueueService.class),
                mock(ProcesoProduccionDocumentoVersionRepo.class),
                fabricacionRepo,
                loteRepo,
                Clock.fixed(
                        Instant.parse("2026-07-16T14:00:00Z"),
                        ZoneId.of("America/Bogota")
                )
        );

        when(seguimientoRepo.findTableroActivosByResponsableUserId(USER_ID, ACTIVE_STATES))
                .thenReturn(List.of());
        when(fabricacionRepo.findActivasPorResponsable(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(ACTIVE_STATES),
                anyList()))
                .thenReturn(List.of());
        when(masterDirectiveService.isBatchRecordWorkflowEnabled()).thenReturn(true);
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
