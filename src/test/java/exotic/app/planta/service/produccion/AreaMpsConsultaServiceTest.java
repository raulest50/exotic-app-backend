package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.*;
import exotic.app.planta.model.produccion.EstadoMpsSemanalItem;
import exotic.app.planta.model.produccion.EstadoMpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.dto.*;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatVersionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AreaMpsConsultaServiceTest {
    @Mock private AreaProduccionRepo areaRepo;
    @Mock private OrdenProduccionRepo ordenRepo;
    @Mock private RutaProcesoCatVersionRepo rutaRepo;
    @InjectMocks private AreaMpsConsultaService service;
    private User user;
    private AreaOperativa area;

    @BeforeEach
    void setup() {
        user = new User();
        user.setId(8L);
        area = new AreaOperativa();
        area.setAreaId(7);
    }

    @ParameterizedTest
    @CsvSource({"SOLO_OP,TODOS,true,false", "SOLO_OP,SOLO_RUTA,true,false",
            "SOLO_OF,TODOS,false,true", "SOLO_OF,SOLO_RUTA,false,true",
            "AMBOS,TODOS,true,true", "AMBOS,SOLO_RUTA,true,true"})
    void lasSeisCombinacionesRestringenSoloElMpsElegido(
            VisibilidadMps visibilidad, AlcanceMps alcance, boolean opPermitido, boolean ofPermitido) {
        area.setVisibilidadMps(visibilidad);
        area.setAlcanceMps(alcance);
        when(areaRepo.findAllByResponsableArea_Id(8L)).thenReturn(List.of(area));
        if (opPermitido) assertSame(area, service.requireArea(user, false));
        else assertThrows(AccessDeniedException.class, () -> service.requireArea(user, false));
        if (ofPermitido) assertSame(area, service.requireArea(user, true));
        else assertThrows(AccessDeniedException.class, () -> service.requireArea(user, true));
    }

    @Test
    void areaNuevaMantieneComportamientoAnteriorYUsuarioSinAreaNoAccede() {
        assertEquals(VisibilidadMps.SOLO_OP, area.getVisibilidadMps());
        assertEquals(AlcanceMps.TODOS, area.getAlcanceMps());
        assertThrows(AccessDeniedException.class, () -> service.requireArea(user, false));
    }

    @Test
    void alcanceTodosDevuelveElMismoProgramaSinConsultarRutas() {
        when(areaRepo.findAllByResponsableArea_Id(8L)).thenReturn(List.of(area));
        var mps = new MpsSemanalDraftDTO();
        assertSame(mps, service.filtrarPrograma(user, mps));
        verifyNoInteractions(ordenRepo, rutaRepo);
    }

    @Test
    void mezclaDeRutasHistoricasYActualesNoFiltraPorCategoriaLasOpYaEmitidas() {
        area.setAlcanceMps(AlcanceMps.SOLO_RUTA);
        when(areaRepo.findAllByResponsableArea_Id(8L)).thenReturn(List.of(area));
        when(ordenRepo.findMpsOrderIdsForArea(anyCollection(), eq(7))).thenReturn(List.of(101));
        when(rutaRepo.findCategoriaIdsForArea(7, RutaProcesoCatVersion.Estado.VIGENTE)).thenReturn(List.of(10));

        // Misma categoria, dos OP con rutas congeladas distintas y una tanda sin emitir.
        var visible = lote(101, EstadoMpsSemanalLotePlanificado.ODP_GENERADA, 100);
        var oculto = lote(102, EstadoMpsSemanalLotePlanificado.ODP_GENERADA, 900);
        var pendiente = lote(null, EstadoMpsSemanalLotePlanificado.PENDIENTE_ODP, 50);
        var cancelado = lote(null, EstadoMpsSemanalLotePlanificado.CANCELADO, 50);
        var item = item(10, List.of(visible, oculto, pendiente, cancelado));
        var fueraDeRuta = item(20, List.of(lote(null, EstadoMpsSemanalLotePlanificado.PENDIENTE_ODP, 700)));
        var dia = new MpsSemanalDiaDTO();
        dia.setItems(List.of(item, fueraDeRuta));
        var mps = new MpsSemanalDraftDTO();
        mps.setDias(List.of(dia));
        mps.setTotalItems(2);
        mps.setTotalOdpsGeneradas(2);
        mps.setTotalLotesPlanificados(4);

        var result = service.filtrarPrograma(user, mps);
        assertEquals(List.of(item), result.getDias().get(0).getItems());
        assertEquals(List.of(visible, pendiente, cancelado), item.getLotesPlanificados());
        assertEquals(150, item.getCantidadTotal());
        assertEquals(2, item.getNumeroLotes());
        assertEquals(1, item.getLotesCancelados());
        assertFalse(item.isEditable());
        assertEquals(1, result.getTotalItems());
        assertEquals(1, result.getTotalOdpsGeneradas());
        assertEquals(2, result.getTotalLotesPlanificados());
    }

    @Test
    void listadoDeOpSoloExponeIdsDeRutasCongeladasDelArea() {
        area.setAlcanceMps(AlcanceMps.SOLO_RUTA);
        when(areaRepo.findAllByResponsableArea_Id(8L)).thenReturn(List.of(area));
        when(ordenRepo.findMpsOrderIdsForArea(List.of(101, 102), 7)).thenReturn(List.of(101));
        var visible = new MpsSemanalOrdenProduccionListItemDTO();
        visible.setOrdenId(101);
        var oculto = new MpsSemanalOrdenProduccionListItemDTO();
        oculto.setOrdenId(102);
        assertEquals(List.of(visible), service.filtrarOrdenes(user, List.of(visible, oculto)));
    }

    private MpsSemanalItemDTO item(int categoriaId, List<MpsSemanalLotePlanificadoDTO> lotes) {
        var item = new MpsSemanalItemDTO();
        item.setCategoriaId(categoriaId);
        item.setEstadoItem(EstadoMpsSemanalItem.ACTIVO);
        item.setLotesPlanificados(lotes);
        return item;
    }

    private MpsSemanalLotePlanificadoDTO lote(Integer op, EstadoMpsSemanalLotePlanificado estado, double cantidad) {
        var lote = new MpsSemanalLotePlanificadoDTO();
        lote.setOrdenProduccionId(op);
        lote.setEstado(estado);
        lote.setCantidadPlanificada(cantidad);
        return lote;
    }
}
