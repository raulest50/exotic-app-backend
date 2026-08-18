package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrdenFabricacionAutoGenerationService {

    private final MaterialRequirementSnapshotService requirementService;
    private final OrdenFabricacionService ordenFabricacionService;

    @Transactional(rollbackFor = Exception.class)
    public void generarParaOrden(OrdenProduccion orden, User actor) {
        if (orden == null || orden.getManufacturingVersion() == null || actor == null) {
            throw new IllegalArgumentException(
                    "La OP, su version de manufactura y el creador son obligatorios.");
        }
        Map<SemiTerminado, BigDecimal> demandas = requirementService.calcularOrdenesFabricacion(
                orden.getManufacturingVersion(), BigDecimal.valueOf(orden.getCantidadProducir()));
        demandas.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getProductoId()))
                .forEach(entry -> ordenFabricacionService.crearAutomatica(
                        entry.getKey(), entry.getValue(), orden, actor));
    }
}
