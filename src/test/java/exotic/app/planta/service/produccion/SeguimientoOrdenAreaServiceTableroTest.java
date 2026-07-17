package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.empresa.JornadaLaboralVersionRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatVersionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.TableroOperativoDTO;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.TableroVista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeguimientoOrdenAreaServiceTableroTest {

    private static final Long USER_ID = 45L;
    private static final List<Integer> ACTIVE_STATES = List.of(
            EstadoSeguimientoOrdenArea.COLA.getCode(),
            EstadoSeguimientoOrdenArea.ESPERA.getCode(),
            EstadoSeguimientoOrdenArea.EN_PROCESO.getCode()
    );

    private SeguimientoOrdenAreaRepo seguimientoRepo;
    private SeguimientoOrdenAreaService service;

    @BeforeEach
    void setUp() {
        seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        service = new SeguimientoOrdenAreaService(
                seguimientoRepo,
                mock(SeguimientoOrdenAreaEventoRepo.class),
                mock(AreaProduccionRepo.class),
                mock(RutaProcesoCatVersionRepo.class),
                mock(JornadaLaboralVersionRepo.class),
                mock(RutaProcesoEstimacionService.class),
                mock(ReporteProduccionLoteService.class),
                mock(UserRepository.class),
                mock(MasterDirectiveService.class),
                Clock.fixed(
                        Instant.parse("2026-07-16T14:00:00Z"),
                        ZoneId.of("America/Bogota")
                )
        );
    }

    @Test
    void hoy_keepsAllActiveCardsAndLimitsCompletedCardsToBogotaDay() {
        when(seguimientoRepo.findTableroActivosByResponsableUserId(USER_ID, ACTIVE_STATES))
                .thenReturn(List.of(
                        seguimiento(1L, 101, EstadoSeguimientoOrdenArea.COLA, null),
                        seguimiento(2L, 102, EstadoSeguimientoOrdenArea.EN_PROCESO, null)
                ));
        when(seguimientoRepo.findTableroCompletadosByResponsableUserIdAndFechaCompletadoBetween(
                USER_ID,
                EstadoSeguimientoOrdenArea.COMPLETADO.getCode(),
                LocalDateTime.of(2026, 7, 16, 0, 0),
                LocalDateTime.of(2026, 7, 17, 0, 0)
        )).thenReturn(List.of(
                seguimiento(
                        3L,
                        103,
                        EstadoSeguimientoOrdenArea.COMPLETADO,
                        LocalDateTime.of(2026, 7, 16, 8, 30)
                )
        ));

        TableroOperativoDTO result = service.getTableroOperativoUsuario(USER_ID, TableroVista.HOY);

        assertEquals(LocalDate.of(2026, 7, 16), result.getPeriodStartDate());
        assertEquals(LocalDate.of(2026, 7, 16), result.getPeriodEndDate());
        assertEquals(3L, result.getResumen().getTotal());
        assertEquals(1L, result.getResumen().getCola());
        assertEquals(1L, result.getResumen().getEnProceso());
        assertEquals(1L, result.getResumen().getCompletado());
        assertNull(result.getPaginacionCompletadas());
        verify(seguimientoRepo).findTableroActivosByResponsableUserId(USER_ID, ACTIVE_STATES);
    }

    @Test
    void semanaActual_usesMondayThroughSundayWithExclusiveNextMondayBoundary() {
        when(seguimientoRepo.findTableroActivosByResponsableUserId(USER_ID, ACTIVE_STATES))
                .thenReturn(List.of());
        when(seguimientoRepo.findTableroCompletadosByResponsableUserIdAndFechaCompletadoBetween(
                USER_ID,
                EstadoSeguimientoOrdenArea.COMPLETADO.getCode(),
                LocalDateTime.of(2026, 7, 13, 0, 0),
                LocalDateTime.of(2026, 7, 20, 0, 0)
        )).thenReturn(List.of());

        TableroOperativoDTO result = service.getTableroOperativoUsuario(
                USER_ID,
                TableroVista.SEMANA_ACTUAL
        );

        assertEquals(LocalDate.of(2026, 7, 13), result.getPeriodStartDate());
        assertEquals(LocalDate.of(2026, 7, 19), result.getPeriodEndDate());
        verify(seguimientoRepo).findTableroCompletadosByResponsableUserIdAndFechaCompletadoBetween(
                USER_ID,
                EstadoSeguimientoOrdenArea.COMPLETADO.getCode(),
                LocalDateTime.of(2026, 7, 13, 0, 0),
                LocalDateTime.of(2026, 7, 20, 0, 0)
        );
    }

    @Test
    void historico_includesCompletedCardsWithoutCompletionDateAndHasNoPeriodMetadata() {
        when(seguimientoRepo.findTableroActivosByResponsableUserId(USER_ID, ACTIVE_STATES))
                .thenReturn(List.of());
        when(seguimientoRepo.findTableroCompletadosHistoricosByResponsableUserId(
                USER_ID,
                EstadoSeguimientoOrdenArea.COMPLETADO.getCode(),
                "",
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(seguimiento(4L, 104, EstadoSeguimientoOrdenArea.COMPLETADO, null)),
                PageRequest.of(0, 20),
                1
        ));

        TableroOperativoDTO result = service.getTableroOperativoUsuario(
                USER_ID,
                TableroVista.HISTORICO
        );

        assertNull(result.getPeriodStartDate());
        assertNull(result.getPeriodEndDate());
        assertEquals(1L, result.getResumen().getTotal());
        assertEquals(1L, result.getResumen().getCompletado());
        assertEquals(0, result.getPaginacionCompletadas().getPage());
        assertEquals(20, result.getPaginacionCompletadas().getSize());
        assertEquals(1L, result.getPaginacionCompletadas().getTotalElements());
        assertEquals(1, result.getPaginacionCompletadas().getTotalPages());
        verify(seguimientoRepo, never())
                .findTableroCompletadosByResponsableUserIdAndFechaCompletadoBetween(
                        USER_ID,
                        EstadoSeguimientoOrdenArea.COMPLETADO.getCode(),
                        LocalDateTime.of(2026, 7, 16, 0, 0),
                        LocalDateTime.of(2026, 7, 17, 0, 0)
                );
    }

    @Test
    void historico_searchKeepsGlobalKpiAndUsesFilteredPaginationTotal() {
        when(seguimientoRepo.findTableroActivosByResponsableUserId(USER_ID, ACTIVE_STATES))
                .thenReturn(List.of(seguimiento(1L, 101, EstadoSeguimientoOrdenArea.ESPERA, null)));
        when(seguimientoRepo.findTableroCompletadosHistoricosByResponsableUserId(
                USER_ID,
                EstadoSeguimientoOrdenArea.COMPLETADO.getCode(),
                "%riso%",
                PageRequest.of(1, 20)
        )).thenReturn(new PageImpl<>(
                List.of(seguimiento(
                        22L,
                        122,
                        EstadoSeguimientoOrdenArea.COMPLETADO,
                        LocalDateTime.of(2026, 7, 10, 8, 0)
                )),
                PageRequest.of(1, 20),
                21
        ));
        when(seguimientoRepo.countTableroCompletadosHistoricosByResponsableUserId(
                USER_ID,
                EstadoSeguimientoOrdenArea.COMPLETADO.getCode()
        )).thenReturn(45L);

        TableroOperativoDTO result = service.getTableroOperativoUsuario(
                USER_ID,
                TableroVista.HISTORICO,
                1,
                20,
                " RISO "
        );

        assertEquals(46L, result.getResumen().getTotal());
        assertEquals(45L, result.getResumen().getCompletado());
        assertEquals(1, result.getCompletado().size());
        assertEquals(21L, result.getPaginacionCompletadas().getTotalElements());
        assertEquals(2, result.getPaginacionCompletadas().getTotalPages());
    }

    @Test
    void historico_rejectsInvalidPagination() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getTableroOperativoUsuario(USER_ID, TableroVista.HISTORICO, -1, 20, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getTableroOperativoUsuario(USER_ID, TableroVista.HISTORICO, 0, 101, null)
        );
    }

    private SeguimientoOrdenArea seguimiento(
            Long seguimientoId,
            int ordenId,
            EstadoSeguimientoOrdenArea estado,
            LocalDateTime fechaCompletado
    ) {
        Terminado producto = new Terminado();
        producto.setProductoId("TER-" + ordenId);
        producto.setNombre("Terminado " + ordenId);
        producto.setTipoUnidades("Unidades");

        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(ordenId);
        orden.setLoteAsignado("LOT-" + ordenId);
        orden.setProducto(producto);
        orden.setCantidadProducir(100);
        orden.setEstadoOrden(1);

        AreaOperativa area = new AreaOperativa();
        area.setAreaId(20);
        area.setNombre("Fabricacion");

        RutaProcesoNode node = new RutaProcesoNode();
        node.setId((long) ordenId);
        node.setLabel("Fabricacion");
        node.setAreaOperativa(area);

        SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
        seguimiento.setId(seguimientoId);
        seguimiento.setOrdenProduccion(orden);
        seguimiento.setAreaOperativa(area);
        seguimiento.setRutaProcesoNode(node);
        seguimiento.setEstadoEnum(estado);
        seguimiento.setFechaEstadoActual(LocalDateTime.of(2026, 7, 16, 8, 0));
        seguimiento.setFechaCompletado(fechaCompletado);
        return seguimiento;
    }
}
