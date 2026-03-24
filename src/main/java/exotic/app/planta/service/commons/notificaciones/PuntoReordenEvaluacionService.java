package exotic.app.planta.service.commons.notificaciones;

import exotic.app.planta.model.commons.notificaciones.MaterialEnPuntoReordenDTO;
import exotic.app.planta.model.commons.notificaciones.MaterialStockRow;
import exotic.app.planta.model.commons.notificaciones.PuntoReordenEvaluacionResult;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fuente única de verdad para "materiales en punto de reorden" (correo, campana Home, API paginada).
 */
@Service
@RequiredArgsConstructor
public class PuntoReordenEvaluacionService {

    public static final double PUNTO_REORDEN_IGNORAR = -1.0;

    private final TransaccionAlmacenRepo transaccionAlmacenRepo;

    @Transactional(readOnly = true)
    public PuntoReordenEvaluacionResult evaluar() {
        List<MaterialStockRow> rows = mapMaterialsWithStock(transaccionAlmacenRepo.findAllMaterialsWithStock());
        List<MaterialStockRow> enReorden = new ArrayList<>();
        List<MaterialStockRow> sinPunto = new ArrayList<>();

        for (MaterialStockRow row : rows) {
            double pr = row.material().getPuntoReorden();
            if (pr == PUNTO_REORDEN_IGNORAR) {
                continue;
            }
            if (pr == 0.0) {
                sinPunto.add(row);
                continue;
            }
            if (pr > 0 && row.stock() <= pr) {
                enReorden.add(row);
            }
        }

        return new PuntoReordenEvaluacionResult(enReorden, sinPunto);
    }

    /**
     * Página de materiales en reorden, ordenados por productoId. Re-ejecuta {@link #evaluar()} en cada llamada.
     */
    @Transactional(readOnly = true)
    public Page<MaterialEnPuntoReordenDTO> pageMaterialesEnReorden(Pageable pageable) {
        PuntoReordenEvaluacionResult result = evaluar();
        List<MaterialStockRow> sorted = new ArrayList<>(result.enReorden());
        sorted.sort(Comparator.comparing(r -> r.material().getProductoId(), Comparator.nullsLast(String::compareTo)));

        List<MaterialEnPuntoReordenDTO> allDtos = sorted.stream().map(this::toDto).toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allDtos.size());
        List<MaterialEnPuntoReordenDTO> slice = start >= allDtos.size()
                ? List.of()
                : allDtos.subList(start, end);

        return new PageImpl<>(slice, pageable, allDtos.size());
    }

    private static List<MaterialStockRow> mapMaterialsWithStock(List<Object[]> raw) {
        List<MaterialStockRow> out = new ArrayList<>(raw.size());
        for (Object[] row : raw) {
            Material m = (Material) row[0];
            double stock = row[1] instanceof Number n ? n.doubleValue() : 0.0;
            out.add(new MaterialStockRow(m, stock));
        }
        return out;
    }

    private MaterialEnPuntoReordenDTO toDto(MaterialStockRow row) {
        Material m = row.material();
        return new MaterialEnPuntoReordenDTO(
                m.getProductoId(),
                m.getNombre(),
                m.getTipoMaterial(),
                tipoMaterialLabel(m.getTipoMaterial()),
                row.stock(),
                m.getPuntoReorden(),
                m.getTipoUnidades() != null ? m.getTipoUnidades() : ""
        );
    }

    public static String tipoMaterialLabel(int tipoMaterial) {
        return switch (tipoMaterial) {
            case 1 -> "Materia prima";
            case 2 -> "Material de empaque";
            default -> "Otro";
        };
    }
}
