package exotic.app.planta.service.compras;

import exotic.app.planta.model.compras.dto.OcmCierreDTOs.*;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcmCierreBatchService {
    private final OrdenCompraRepo ordenes;
    private final RecepcionCompletaOcmService recepcion;
    private final OcmCierreConfigService configuracion;
    private final OcmCierreService cierre;

    @Transactional(readOnly = true)
    public List<Candidata> previsualizar() {
        Config config = configuracion.consultar();
        List<Candidata> candidatas = new ArrayList<>();
        int afterId = 0;
        while (true) {
            List<Integer> ids = ordenes.findOpenIdsAfter(afterId, PageRequest.of(0, 100));
            if (ids.isEmpty()) break;
            var pagina = ordenes.findWithItemsByIds(ids);
            var completas = recepcion.evaluar(pagina);
            for (var orden : pagina) {
                if (orden.getEstado() == 2 && Boolean.TRUE.equals(completas.get(orden.getOrdenCompraId()))) {
                    candidatas.add(new Candidata(orden.getOrdenCompraId(),
                            orden.getProveedor() == null ? "" : orden.getProveedor().getNombre(),
                            orden.getFechaEmision(), orden.getFechaRecepcionCompleta(),
                            OcmCierreService.fechaPrevista(orden, config)));
                }
            }
            afterId = ids.get(ids.size() - 1);
        }
        candidatas.sort(Comparator.comparingInt(Candidata::ordenCompraId));
        return candidatas;
    }

    // Intentionally no encompassing transaction: one failure must not undo other confirmed orders.
    public ResultadoCierre cerrarSeleccionadas(List<Integer> ids, String username) {
        if (ids == null || ids.isEmpty() || ids.size() > 100
                || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Seleccione entre 1 y 100 OCM válidas por solicitud.");
        }
        List<Integer> cerradas = new ArrayList<>();
        List<Incidencia> omitidas = new ArrayList<>();
        List<Incidencia> fallidas = new ArrayList<>();
        for (int id : new LinkedHashSet<>(ids)) {
            try {
                String motivo = cierre.cerrarCompletaManualmente(id, username);
                if (motivo == null) cerradas.add(id);
                else omitidas.add(new Incidencia(id, motivo));
            } catch (RuntimeException error) {
                log.error("No fue posible cerrar la OCM {} desde directivas", id, error);
                fallidas.add(new Incidencia(id, "No fue posible cerrar la OCM. Actualice la previsualización antes de reintentar."));
            }
        }
        return new ResultadoCierre(cerradas, omitidas, fallidas);
    }
}
