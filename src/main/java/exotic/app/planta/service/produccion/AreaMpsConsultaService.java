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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Configuracion de consulta del MPS; no gobierna la ejecucion de tareas asignadas. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AreaMpsConsultaService {
    private final AreaProduccionRepo areaRepo;
    private final OrdenProduccionRepo ordenRepo;
    private final RutaProcesoCatVersionRepo rutaRepo;

    public AreaOperativa requireArea(User user, boolean fabricacion) {
        AreaOperativa area = areaRepo.findAllByResponsableArea_Id(user.getId()).stream()
                .findFirst().orElseThrow(() -> new AccessDeniedException("No es responsable de un area operativa."));
        if ((fabricacion && area.getVisibilidadMps() == VisibilidadMps.SOLO_OP)
                || (!fabricacion && area.getVisibilidadMps() == VisibilidadMps.SOLO_OF)) {
            throw new AccessDeniedException("Este MPS no esta habilitado para su area operativa.");
        }
        return area;
    }

    public List<MpsSemanalOrdenProduccionListItemDTO> filtrarOrdenes(
            User user, List<MpsSemanalOrdenProduccionListItemDTO> ordenes) {
        AreaOperativa area = requireArea(user, false);
        if (area.getAlcanceMps() == AlcanceMps.TODOS) return ordenes;
        Set<Integer> visibles = idsVisibles(area, ordenes.stream().map(MpsSemanalOrdenProduccionListItemDTO::getOrdenId).toList());
        return ordenes.stream().filter(o -> visibles.contains(o.getOrdenId())).toList();
    }

    public MpsSemanalDraftDTO filtrarPrograma(User user, MpsSemanalDraftDTO mps) {
        AreaOperativa area = requireArea(user, false);
        if (area.getAlcanceMps() == AlcanceMps.TODOS) return mps;
        List<Integer> ids = mps.getDias().stream().flatMap(d -> d.getItems().stream())
                .flatMap(i -> i.getLotesPlanificados().stream()).map(MpsSemanalLotePlanificadoDTO::getOrdenProduccionId)
                .filter(Objects::nonNull).distinct().toList();
        Set<Integer> visibles = idsVisibles(area, ids);
        Set<Integer> categorias = new HashSet<>(rutaRepo.findCategoriaIdsForArea(
                area.getAreaId(), RutaProcesoCatVersion.Estado.VIGENTE));
        for (MpsSemanalDiaDTO dia : mps.getDias()) {
            List<MpsSemanalItemDTO> items = new ArrayList<>();
            for (MpsSemanalItemDTO item : dia.getItems()) {
                if (item.getLotesPlanificados().isEmpty()) {
                    if (categorias.contains(item.getCategoriaId())) items.add(item);
                    continue;
                }
                var lotes = item.getLotesPlanificados().stream().filter(l -> l.getOrdenProduccionId() != null
                        ? visibles.contains(l.getOrdenProduccionId()) : categorias.contains(item.getCategoriaId())).toList();
                if (lotes.isEmpty()) continue;
                item.setLotesPlanificados(lotes);
                var activos = lotes.stream().filter(l -> l.getEstado() != EstadoMpsSemanalLotePlanificado.CANCELADO).toList();
                item.setNumeroLotes(activos.size());
                item.setCantidadTotal(activos.stream().mapToDouble(MpsSemanalLotePlanificadoDTO::getCantidadPlanificada).sum());
                item.setLotesActivos(activos.size());
                item.setLotesCancelados(lotes.size() - activos.size());
                item.setOrdenesIniciadas((int) activos.stream().filter(MpsSemanalLotePlanificadoDTO::isOrdenIniciada).count());
                item.setOrdenesCancelables((int) activos.stream().filter(MpsSemanalLotePlanificadoDTO::isOrdenCancelable).count());
                item.setLotesCancelables((int) activos.stream().filter(l -> l.getOrdenProduccionId() == null || l.isOrdenCancelable()).count());
                item.setLotesNoCancelables(activos.size() - item.getLotesCancelables());
                item.setEditable(false);
                item.setBlockedReason("Consulta operativa de solo lectura.");
                items.add(item);
            }
            dia.setItems(items);
        }
        var items = mps.getDias().stream().flatMap(d -> d.getItems().stream()).toList();
        mps.setTotalItems(items.stream().filter(i -> i.getEstadoItem() != EstadoMpsSemanalItem.CANCELADO).count());
        mps.setTotalLotesPlanificados(items.stream().mapToLong(MpsSemanalItemDTO::getLotesActivos).sum());
        mps.setTotalOdpsGeneradas(items.stream().flatMap(i -> i.getLotesPlanificados().stream())
                .filter(l -> l.getEstado() == EstadoMpsSemanalLotePlanificado.ODP_GENERADA).count());
        return mps;
    }

    private Set<Integer> idsVisibles(AreaOperativa area, List<Integer> ids) {
        return ids.isEmpty() ? Set.of() : new HashSet<>(ordenRepo.findMpsOrderIdsForArea(ids, area.getAreaId()));
    }
}
