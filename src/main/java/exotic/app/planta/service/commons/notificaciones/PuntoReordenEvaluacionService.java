package exotic.app.planta.service.commons.notificaciones;

import exotic.app.planta.model.commons.notificaciones.MaterialEnPuntoReordenConOcmDTO;
import exotic.app.planta.model.commons.notificaciones.MaterialEnPuntoReordenDTO;
import exotic.app.planta.model.commons.notificaciones.MaterialStockRow;
import exotic.app.planta.model.commons.notificaciones.OcmPendienteIngresoDTO;
import exotic.app.planta.model.commons.notificaciones.PuntoReordenEvaluacionResult;
import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fuente unica de verdad para "materiales en punto de reorden"
 * usada por correo, campana Home y API de detalle.
 */
@Service
@RequiredArgsConstructor
public class PuntoReordenEvaluacionService {

    public static final double PUNTO_REORDEN_IGNORAR = -1.0;
    private static final int ESTADO_OCM_PENDIENTE_INGRESO = 2;

    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final ItemOrdenCompraRepo itemOrdenCompraRepo;

    @Transactional(readOnly = true)
    public PuntoReordenEvaluacionResult evaluar() {
        List<MaterialStockRow> rows = mapMaterialsWithStock(transaccionAlmacenRepo.findAllMaterialsWithStock());
        List<MaterialStockRow> enReordenRows = new ArrayList<>();
        List<MaterialStockRow> sinPuntoRows = new ArrayList<>();

        for (MaterialStockRow row : rows) {
            double pr = row.material().getPuntoReorden();
            if (pr == PUNTO_REORDEN_IGNORAR) {
                continue;
            }
            if (pr == 0.0) {
                sinPuntoRows.add(row);
                continue;
            }
            if (pr > 0 && row.stock() <= pr) {
                enReordenRows.add(row);
            }
        }

        ClasificacionReordenResult clasificacion = splitPendientesIngresoAlmacen(enReordenRows);
        List<MaterialEnPuntoReordenDTO> sinPunto = sortBaseDtos(
                sinPuntoRows.stream().map(this::toDto).toList()
        );

        return new PuntoReordenEvaluacionResult(
                clasificacion.pendientesOrdenar(),
                clasificacion.pendientesIngresoAlmacen(),
                sinPunto,
                clasificacion.pendientesOrdenar().size(),
                clasificacion.pendientesIngresoAlmacen().size(),
                sinPunto.size(),
                clasificacion.pendientesOrdenar().size() + clasificacion.pendientesIngresoAlmacen().size()
        );
    }

    private ClasificacionReordenResult splitPendientesIngresoAlmacen(List<MaterialStockRow> enReordenRows) {
        List<MaterialStockRow> sortedRows = new ArrayList<>(enReordenRows);
        sortedRows.sort(Comparator.comparing(r -> r.material().getProductoId(), Comparator.nullsLast(String::compareTo)));
        if (sortedRows.isEmpty()) {
            return new ClasificacionReordenResult(List.of(), List.of());
        }

        Map<String, MaterialStockRow> rowsByProductoId = sortedRows.stream()
                .collect(Collectors.toMap(
                        row -> row.material().getProductoId(),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Set<String> productoIds = rowsByProductoId.keySet();
        List<ItemOrdenCompra> itemsPendientesIngreso = itemOrdenCompraRepo.findPendientesIngresoByMaterialProductoIds(
                productoIds,
                ESTADO_OCM_PENDIENTE_INGRESO
        );

        Map<String, Map<Integer, OcmPendienteIngresoDTO>> ocmsByProductoId = new LinkedHashMap<>();
        for (ItemOrdenCompra item : itemsPendientesIngreso) {
            String productoId = item.getMaterial().getProductoId();
            if (!rowsByProductoId.containsKey(productoId)) {
                continue;
            }

            ocmsByProductoId
                    .computeIfAbsent(productoId, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(
                            item.getOrdenCompraMateriales().getOrdenCompraId(),
                            new OcmPendienteIngresoDTO(
                                    item.getOrdenCompraMateriales().getOrdenCompraId(),
                                    item.getOrdenCompraMateriales().getFechaEmision()
                            )
                    );
        }

        List<MaterialEnPuntoReordenDTO> pendientesOrdenar = new ArrayList<>();
        List<MaterialEnPuntoReordenConOcmDTO> pendientesIngresoAlmacen = new ArrayList<>();

        for (MaterialStockRow row : sortedRows) {
            MaterialEnPuntoReordenDTO baseDto = toDto(row);
            Map<Integer, OcmPendienteIngresoDTO> ocmsMaterial = ocmsByProductoId.get(row.material().getProductoId());
            if (ocmsMaterial == null || ocmsMaterial.isEmpty()) {
                pendientesOrdenar.add(baseDto);
                continue;
            }

            List<OcmPendienteIngresoDTO> ocms = new ArrayList<>(ocmsMaterial.values());
            ocms.sort(Comparator
                    .comparing(OcmPendienteIngresoDTO::getFechaEmision, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(OcmPendienteIngresoDTO::getOrdenCompraId, Comparator.reverseOrder()));

            pendientesIngresoAlmacen.add(new MaterialEnPuntoReordenConOcmDTO(
                    baseDto.getProductoId(),
                    baseDto.getNombre(),
                    baseDto.getTipoMaterial(),
                    baseDto.getTipoMaterialLabel(),
                    baseDto.getStockActual(),
                    baseDto.getPuntoReorden(),
                    baseDto.getTipoUnidades(),
                    ocms
            ));
        }

        pendientesIngresoAlmacen.sort(Comparator.comparing(
                MaterialEnPuntoReordenConOcmDTO::getProductoId,
                Comparator.nullsLast(String::compareTo)
        ));

        return new ClasificacionReordenResult(sortBaseDtos(pendientesOrdenar), pendientesIngresoAlmacen);
    }

    private static List<MaterialStockRow> mapMaterialsWithStock(List<Object[]> raw) {
        List<MaterialStockRow> out = new ArrayList<>(raw.size());
        for (Object[] row : raw) {
            Material material = (Material) row[0];
            double stock = row[1] instanceof Number number ? number.doubleValue() : 0.0;
            out.add(new MaterialStockRow(material, stock));
        }
        return out;
    }

    private static List<MaterialEnPuntoReordenDTO> sortBaseDtos(List<MaterialEnPuntoReordenDTO> dtos) {
        List<MaterialEnPuntoReordenDTO> sorted = new ArrayList<>(dtos);
        sorted.sort(Comparator.comparing(MaterialEnPuntoReordenDTO::getProductoId, Comparator.nullsLast(String::compareTo)));
        return sorted;
    }

    private MaterialEnPuntoReordenDTO toDto(MaterialStockRow row) {
        Material material = row.material();
        return new MaterialEnPuntoReordenDTO(
                material.getProductoId(),
                material.getNombre(),
                material.getTipoMaterial(),
                tipoMaterialLabel(material.getTipoMaterial()),
                row.stock(),
                material.getPuntoReorden(),
                material.getTipoUnidades() != null ? material.getTipoUnidades() : ""
        );
    }

    public static String tipoMaterialLabel(int tipoMaterial) {
        return switch (tipoMaterial) {
            case 1 -> "Materia prima";
            case 2 -> "Material de empaque";
            default -> "Otro";
        };
    }

    private record ClasificacionReordenResult(
            List<MaterialEnPuntoReordenDTO> pendientesOrdenar,
            List<MaterialEnPuntoReordenConOcmDTO> pendientesIngresoAlmacen
    ) {}
}
