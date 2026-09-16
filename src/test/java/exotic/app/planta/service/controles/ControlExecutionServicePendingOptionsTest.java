package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.MomentoControl;
import exotic.app.planta.model.controles.TipoOrdenControl;
import exotic.app.planta.repo.controles.ControlRequeridoRepo;
import exotic.app.planta.repo.controles.DesviacionControlRepo;
import exotic.app.planta.repo.controles.EjecucionControlRepo;
import exotic.app.planta.repo.controles.RevalidacionControlRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControlExecutionServicePendingOptionsTest {

    @Mock private ControlRequeridoRepo requeridoRepo;
    @Mock private EjecucionControlRepo ejecucionRepo;
    @Mock private DesviacionControlRepo desviacionRepo;
    @Mock private RevalidacionControlRepo revalidacionRepo;
    @Mock private ControlPlanService planService;
    @Mock private BatchRecordRepo batchRecordRepo;
    @Mock private LegacyControlExecutionProjection legacyProjection;
    @Mock private ControlRequeridoRepo.EnsayoPendienteAggregateView aggregate;

    @InjectMocks private ControlExecutionService service;

    @Test
    void devuelveUnaOpcionPorPlanConLosMomentosPresentes() {
        when(aggregate.getPlanId()).thenReturn(17L);
        when(aggregate.getCodigo()).thenReturn("EC-PESO");
        when(aggregate.getNombre()).thenReturn("Peso de envase");
        when(aggregate.getControlesIntermedios()).thenReturn(2L);
        when(aggregate.getControlesProductoTerminado()).thenReturn(1L);
        when(requeridoRepo.buscarOpcionesEnsayoPendiente(
                anyCollection(), eq(4), eq(TipoOrdenControl.OP), eq("peso"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(aggregate)));

        var result = service.opcionesEnsayoCalidad(4, TipoOrdenControl.OP, " peso ", 0, 10);

        assertEquals(1, result.getTotalElements());
        assertEquals(17L, result.getContent().getFirst().planId());
        assertEquals(List.of(MomentoControl.DURANTE_FABRICACION, MomentoControl.REVISION_FINAL),
                result.getContent().getFirst().momentos());
        verify(requeridoRepo).buscarOpcionesEnsayoPendiente(
                anyCollection(), eq(4), eq(TipoOrdenControl.OP), eq("peso"), any(Pageable.class));
    }

    @Test
    void enviaElPlanSeleccionadoComoFiltroDePendientes() {
        when(requeridoRepo.buscarPendientes(
                eq(AmbitoControl.CALIDAD), anyCollection(),
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(77L),
                isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.pendientes(
                AmbitoControl.CALIDAD,
                77L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20);

        verify(requeridoRepo).buscarPendientes(
                eq(AmbitoControl.CALIDAD), anyCollection(),
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(77L),
                isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }
}
