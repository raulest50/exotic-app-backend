package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.bi.dto.PaginaInformeInventarioDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AjustesInventarioDetalleService {
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(5, 10);
    private static final Set<Movimiento.TipoMovimiento> ALL_ADJUSTMENT_TYPES = Set.of(
            Movimiento.TipoMovimiento.AJUSTE_POSITIVO,
            Movimiento.TipoMovimiento.AJUSTE_NEGATIVO);

    private final TransaccionAlmacenRepo movementRepo;
    private final AjustesInventarioAssembler assembler;

    public PaginaInformeInventarioDTO<InformeInventarioDTO.MaterialImpactoAjusteDTO>
    getMaterials(
            LocalDate startDate,
            LocalDate endDate,
            String rawGroup,
            String rawType,
            String rawOrder,
            String search,
            int page,
            int size
    ) {
        validatePage(page, size);
        AdjustmentGroup group = AdjustmentGroup.parse(rawGroup);
        AdjustmentDirection direction = AdjustmentDirection.parse(rawType);
        AdjustmentOrder order = AdjustmentOrder.parse(rawOrder);

        List<Movimiento> movements =
                movementRepo.findAjustesMaterialesBiByAlmacenAndRango(
                        Movimiento.Almacen.GENERAL,
                        ALL_ADJUSTMENT_TYPES,
                        startDate.atStartOfDay(),
                        endDate.atTime(LocalTime.MAX));

        String normalizedSearch = normalize(search);
        List<InformeInventarioDTO.MaterialImpactoAjusteDTO> items = assembler
                .aggregateMaterials(
                        movements,
                        group.inventoryGroup,
                        direction.includedTypes)
                .stream()
                .filter(item -> matches(item, normalizedSearch))
                .sorted(comparator(order))
                .toList();

        return toPage(items, page, size);
    }

    private Comparator<InformeInventarioDTO.MaterialImpactoAjusteDTO> comparator(
            AdjustmentOrder order
    ) {
        Comparator<InformeInventarioDTO.MaterialImpactoAjusteDTO> impact =
                assembler.impactComparator();
        return switch (order) {
            case IMPACTO -> impact;
            case MOVIMIENTOS -> Comparator
                    .comparingInt(
                            InformeInventarioDTO.MaterialImpactoAjusteDTO::movimientos)
                    .reversed()
                    .thenComparing(impact);
            case RECIENTES -> Comparator
                    .comparing(
                            InformeInventarioDTO.MaterialImpactoAjusteDTO::ultimoAjuste,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(impact);
            case NOMBRE -> Comparator
                    .comparing(
                            (InformeInventarioDTO.MaterialImpactoAjusteDTO item) ->
                                    normalize(item.productoNombre()),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(
                            InformeInventarioDTO.MaterialImpactoAjusteDTO::productoId,
                            String.CASE_INSENSITIVE_ORDER);
        };
    }

    private boolean matches(
            InformeInventarioDTO.MaterialImpactoAjusteDTO item,
            String normalizedSearch
    ) {
        if (normalizedSearch == null || normalizedSearch.isBlank()) return true;
        return normalize(item.productoId()).contains(normalizedSearch)
                || normalize(item.productoNombre()).contains(normalizedSearch);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("La pagina no puede ser negativa.");
        }
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw new IllegalArgumentException(
                    "El tamaño de pagina debe ser 5 o 10.");
        }
    }

    private PaginaInformeInventarioDTO<InformeInventarioDTO.MaterialImpactoAjusteDTO>
    toPage(
            List<InformeInventarioDTO.MaterialImpactoAjusteDTO> items,
            int page,
            int size
    ) {
        int totalElements = items.size();
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil(totalElements / (double) size);
        int from = Math.min(page * size, totalElements);
        int to = Math.min(from + size, totalElements);
        List<InformeInventarioDTO.MaterialImpactoAjusteDTO> pageItems =
                items.subList(from, to);

        return new PaginaInformeInventarioDTO<>(
                pageItems,
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                totalPages == 0 || page >= totalPages - 1);
    }

    private enum AdjustmentGroup {
        MATERIA_PRIMA(AjustesInventarioAssembler.RAW_MATERIAL),
        EMPAQUE(AjustesInventarioAssembler.PACKAGING);

        private final String inventoryGroup;

        AdjustmentGroup(String inventoryGroup) {
            this.inventoryGroup = inventoryGroup;
        }

        static AdjustmentGroup parse(String rawValue) {
            try {
                return valueOf(normalizeEnum(rawValue));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "El grupo debe ser MATERIA_PRIMA o EMPAQUE.");
            }
        }
    }

    private enum AdjustmentDirection {
        TODOS(ALL_ADJUSTMENT_TYPES),
        POSITIVO(Set.of(Movimiento.TipoMovimiento.AJUSTE_POSITIVO)),
        NEGATIVO(Set.of(Movimiento.TipoMovimiento.AJUSTE_NEGATIVO));

        private final Set<Movimiento.TipoMovimiento> includedTypes;

        AdjustmentDirection(Set<Movimiento.TipoMovimiento> includedTypes) {
            this.includedTypes = includedTypes;
        }

        static AdjustmentDirection parse(String rawValue) {
            try {
                return valueOf(normalizeEnum(rawValue));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "El tipo debe ser TODOS, POSITIVO o NEGATIVO.");
            }
        }
    }

    private enum AdjustmentOrder {
        IMPACTO,
        MOVIMIENTOS,
        RECIENTES,
        NOMBRE;

        static AdjustmentOrder parse(String rawValue) {
            try {
                return valueOf(normalizeEnum(rawValue));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "El orden debe ser IMPACTO, MOVIMIENTOS, RECIENTES o NOMBRE.");
            }
        }
    }

    private static String normalizeEnum(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("El filtro es obligatorio.");
        }
        return rawValue.trim().toUpperCase(Locale.ROOT);
    }
}
