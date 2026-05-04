package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.service.produccion.MonitoreoAreasOperativasMetricasService.AreaOperativaMetricasDTO;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.AreaOperativaTableroDTO;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.SeguimientoOrdenAreaDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitoreoAreasOperativasMetricasServiceTest {

    @Test
    void getMetricasArea_actual_reusesSnapshotMetrics() {
        SeguimientoOrdenAreaService seguimientoService = mock(SeguimientoOrdenAreaService.class);
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        SeguimientoOrdenAreaEventoRepo eventoRepo = mock(SeguimientoOrdenAreaEventoRepo.class);
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);

        AreaOperativaTableroDTO tablero = new AreaOperativaTableroDTO();
        tablero.setFechaConsulta(LocalDate.of(2026, 5, 4));
        tablero.setPromedioMinutosEspera(12.5);
        tablero.setPromedioMinutosEnProceso(48.0);
        tablero.setEspera(List.of(new SeguimientoOrdenAreaDTO(), new SeguimientoOrdenAreaDTO()));
        tablero.setEnProceso(List.of(new SeguimientoOrdenAreaDTO()));

        when(seguimientoService.getTableroAreaPorFecha(7, LocalDate.of(2026, 5, 4))).thenReturn(tablero);

        MonitoreoAreasOperativasMetricasService service = new MonitoreoAreasOperativasMetricasService(
                seguimientoService,
                seguimientoRepo,
                eventoRepo,
                areaRepo
        );

        AreaOperativaMetricasDTO dto = service.getMetricasArea(7, "actual", LocalDate.of(2026, 5, 4), null, null);

        assertEquals("actual", dto.getModo());
        assertEquals(LocalDate.of(2026, 5, 4), dto.getFecha());
        assertEquals(12.5, dto.getPromedioMinutosEspera());
        assertEquals(48.0, dto.getPromedioMinutosEnProceso());
        assertEquals(2, dto.getMuestrasEspera());
        assertEquals(1, dto.getMuestrasEnProceso());
    }

    @Test
    void getMetricasArea_historico_calculatesClosedIntervalsForEsperaAndProceso() {
        MonitoreoAreasOperativasMetricasService service = createHistoricalService(buildMainFlowEvents());

        AreaOperativaMetricasDTO dto = service.getMetricasArea(7, "historico", null, null, null);

        assertEquals("historico", dto.getModo());
        assertEquals(22.5, dto.getPromedioMinutosEspera());
        assertEquals(37.5, dto.getPromedioMinutosEnProceso());
        assertEquals(2, dto.getMuestrasEspera());
        assertEquals(2, dto.getMuestrasEnProceso());
    }

    @Test
    void getMetricasArea_rango_filtersByClosedAtDate() {
        MonitoreoAreasOperativasMetricasService service = createHistoricalService(buildRangeEvents());

        AreaOperativaMetricasDTO dto = service.getMetricasArea(
                7,
                "rango",
                null,
                LocalDate.of(2026, 5, 2),
                LocalDate.of(2026, 5, 2)
        );

        assertEquals("rango", dto.getModo());
        assertEquals(LocalDate.of(2026, 5, 2), dto.getFechaDesde());
        assertEquals(LocalDate.of(2026, 5, 2), dto.getFechaHasta());
        assertEquals(30.0, dto.getPromedioMinutosEspera());
        assertEquals(60.0, dto.getPromedioMinutosEnProceso());
        assertEquals(1, dto.getMuestrasEspera());
        assertEquals(1, dto.getMuestrasEnProceso());
    }

    @Test
    void getMetricasArea_historico_withoutClosedIntervals_returnsNullAndZeroSamples() {
        MonitoreoAreasOperativasMetricasService service = createHistoricalService(List.of(
                event(1L, null, EstadoSeguimientoOrdenArea.ESPERA, LocalDateTime.of(2026, 5, 1, 8, 0))
        ));

        AreaOperativaMetricasDTO dto = service.getMetricasArea(7, "historico", null, null, null);

        assertNull(dto.getPromedioMinutosEspera());
        assertNull(dto.getPromedioMinutosEnProceso());
        assertEquals(0, dto.getMuestrasEspera());
        assertEquals(0, dto.getMuestrasEnProceso());
    }

    @Test
    void getMetricasArea_rangoInvalid_throwsValidationError() {
        MonitoreoAreasOperativasMetricasService service = createHistoricalService(buildMainFlowEvents());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getMetricasArea(
                        7,
                        "rango",
                        null,
                        LocalDate.of(2026, 5, 4),
                        LocalDate.of(2026, 5, 2)
                )
        );
    }

    private MonitoreoAreasOperativasMetricasService createHistoricalService(List<SeguimientoOrdenAreaEvento> events) {
        SeguimientoOrdenAreaService seguimientoService = mock(SeguimientoOrdenAreaService.class);
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        SeguimientoOrdenAreaEventoRepo eventoRepo = mock(SeguimientoOrdenAreaEventoRepo.class);
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);

        AreaOperativa area = new AreaOperativa();
        area.setAreaId(7);

        SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
        seguimiento.setId(1L);

        when(areaRepo.findById(7)).thenReturn(Optional.of(area));
        when(seguimientoRepo.findTableroByAreaId(7)).thenReturn(List.of(seguimiento));
        when(eventoRepo.findBySeguimientoOrdenArea_IdInOrderByFechaEventoAscIdAsc(List.of(1L))).thenReturn(events);

        return new MonitoreoAreasOperativasMetricasService(
                seguimientoService,
                seguimientoRepo,
                eventoRepo,
                areaRepo
        );
    }

    private List<SeguimientoOrdenAreaEvento> buildMainFlowEvents() {
        return List.of(
                event(1L, null, EstadoSeguimientoOrdenArea.ESPERA, LocalDateTime.of(2026, 5, 1, 8, 0)),
                event(1L, EstadoSeguimientoOrdenArea.ESPERA, EstadoSeguimientoOrdenArea.EN_PROCESO, LocalDateTime.of(2026, 5, 1, 8, 15)),
                event(1L, EstadoSeguimientoOrdenArea.EN_PROCESO, EstadoSeguimientoOrdenArea.ESPERA, LocalDateTime.of(2026, 5, 1, 9, 0)),
                event(1L, EstadoSeguimientoOrdenArea.ESPERA, EstadoSeguimientoOrdenArea.EN_PROCESO, LocalDateTime.of(2026, 5, 1, 9, 30)),
                event(1L, EstadoSeguimientoOrdenArea.EN_PROCESO, EstadoSeguimientoOrdenArea.COMPLETADO, LocalDateTime.of(2026, 5, 1, 10, 0))
        );
    }

    private List<SeguimientoOrdenAreaEvento> buildRangeEvents() {
        return List.of(
                event(1L, null, EstadoSeguimientoOrdenArea.ESPERA, LocalDateTime.of(2026, 5, 1, 8, 0)),
                event(1L, EstadoSeguimientoOrdenArea.ESPERA, EstadoSeguimientoOrdenArea.EN_PROCESO, LocalDateTime.of(2026, 5, 1, 8, 10)),
                event(1L, EstadoSeguimientoOrdenArea.EN_PROCESO, EstadoSeguimientoOrdenArea.ESPERA, LocalDateTime.of(2026, 5, 1, 8, 50)),
                event(1L, EstadoSeguimientoOrdenArea.ESPERA, EstadoSeguimientoOrdenArea.EN_PROCESO, LocalDateTime.of(2026, 5, 2, 9, 0)),
                event(1L, EstadoSeguimientoOrdenArea.EN_PROCESO, EstadoSeguimientoOrdenArea.COMPLETADO, LocalDateTime.of(2026, 5, 2, 10, 0))
        );
    }

    private SeguimientoOrdenAreaEvento event(
            Long seguimientoId,
            EstadoSeguimientoOrdenArea estadoOrigen,
            EstadoSeguimientoOrdenArea estadoDestino,
            LocalDateTime fechaEvento
    ) {
        SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
        seguimiento.setId(seguimientoId);

        SeguimientoOrdenAreaEvento evento = new SeguimientoOrdenAreaEvento();
        evento.setSeguimientoOrdenArea(seguimiento);
        evento.setEstadoOrigen(estadoOrigen != null ? estadoOrigen.getCode() : null);
        evento.setEstadoDestino(estadoDestino.getCode());
        evento.setFechaEvento(fechaEvento);
        evento.setActorTipo(ActorTipoEventoSeguimiento.SYSTEM);
        return evento;
    }
}
