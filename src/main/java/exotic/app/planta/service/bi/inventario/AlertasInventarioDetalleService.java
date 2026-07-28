package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.AlertasMaterialesExploracionDTO;
import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.bi.dto.PaginaInformeInventarioDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertasInventarioDetalleService {
    private static final int MAX_PRIORITY_ITEMS = 10;
    private static final int MAX_SEARCH_LENGTH = 100;
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 20);

    private final InventarioStockReader stockReader;
    private final AlertasInventarioClassifier classifier;
    private final Clock applicationClock;

    public AlertasMaterialesExploracionDTO getAlerts(
            String rawType,
            String rawGroup,
            String rawUnit,
            String rawOrder,
            String rawSearch,
            int page,
            int size
    ) {
        validatePage(page, size);
        AlertType type = AlertType.parse(rawType);
        MaterialGroup group = MaterialGroup.parse(rawGroup);
        AlertOrder order = AlertOrder.parse(rawOrder);
        String unit = normalizeOptional(rawUnit);
        String search = normalizeSearch(rawSearch);
        if (order == AlertOrder.STOCK_ASC && unit.isBlank()) {
            throw new IllegalArgumentException(
                    "Debe seleccionar una unidad para ordenar por stock.");
        }

        List<InformeInventarioDTO.AlertaStockDTO> allAlerts =
                classifier.classify(stockReader.readGeneralStock());
        InformeInventarioDTO.AlertasDTO mainSummary =
                classifier.summarize(allAlerts, MAX_PRIORITY_ITEMS);
        List<InformeInventarioDTO.AlertaStockDTO> filtered = allAlerts.stream()
                .filter(alert -> type.matches(alert.tipo()))
                .filter(alert -> group.matches(alert.grupo()))
                .filter(alert -> unit.isBlank()
                        || unit.equals(normalizeOptional(alert.unidadMedida())))
                .filter(alert -> matchesSearch(alert, search))
                .sorted(comparator(order))
                .toList();

        PaginaInformeInventarioDTO<InformeInventarioDTO.AlertaStockDTO> resultPage =
                toPage(filtered, page, size);

        return AlertasMaterialesExploracionDTO.builder()
                .fechaHoraCorteStock(LocalDateTime.now(applicationClock))
                .resumen(toSummary(mainSummary))
                .prioritarios(mainSummary.items())
                .facetas(AlertasMaterialesExploracionDTO.FacetasAlertasDTO.builder()
                        .gruposDisponibles(allAlerts.stream()
                                .map(InformeInventarioDTO.AlertaStockDTO::grupo)
                                .distinct()
                                .sorted()
                                .toList())
                        .unidadesDisponibles(allAlerts.stream()
                                .map(InformeInventarioDTO.AlertaStockDTO::unidadMedida)
                                .distinct()
                                .sorted()
                                .toList())
                        .build())
                .pagina(resultPage)
                .build();
    }

    private AlertasMaterialesExploracionDTO.ResumenAlertasDTO toSummary(
            InformeInventarioDTO.AlertasDTO summary
    ) {
        return AlertasMaterialesExploracionDTO.ResumenAlertasDTO.builder()
                .total(summary.total())
                .negativas(summary.negativas())
                .agotadas(summary.agotadas())
                .bajoUmbral(summary.bajoUmbral())
                .sinCosto(summary.sinCosto())
                .build();
    }

    private Comparator<InformeInventarioDTO.AlertaStockDTO> comparator(
            AlertOrder order
    ) {
        Comparator<InformeInventarioDTO.AlertaStockDTO> priority =
                classifier.priorityComparator();
        return switch (order) {
            case PRIORIDAD -> priority;
            case MAYOR_BRECHA_RELATIVA -> Comparator
                    .comparing(
                            InformeInventarioDTO.AlertaStockDTO::brechaPct,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(priority);
            case STOCK_ASC -> Comparator
                    .comparingDouble(InformeInventarioDTO.AlertaStockDTO::stock)
                    .thenComparing(
                            InformeInventarioDTO.AlertaStockDTO::productoId,
                            String.CASE_INSENSITIVE_ORDER);
            case NOMBRE -> Comparator
                    .comparing(
                            (InformeInventarioDTO.AlertaStockDTO alert) ->
                                    normalizeOptional(alert.productoNombre()),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(
                            InformeInventarioDTO.AlertaStockDTO::productoId,
                            String.CASE_INSENSITIVE_ORDER);
        };
    }

    private boolean matchesSearch(
            InformeInventarioDTO.AlertaStockDTO alert,
            String search
    ) {
        if (search.isBlank()) return true;
        return normalizeOptional(alert.productoId()).contains(search)
                || normalizeOptional(alert.productoNombre()).contains(search);
    }

    private String normalizeSearch(String value) {
        String search = normalizeOptional(value);
        if (search.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException(
                    "La busqueda no puede superar 100 caracteres.");
        }
        return search;
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("La pagina no puede ser negativa.");
        }
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw new IllegalArgumentException(
                    "El tamaño de pagina debe ser 10 o 20.");
        }
    }

    private PaginaInformeInventarioDTO<InformeInventarioDTO.AlertaStockDTO> toPage(
            List<InformeInventarioDTO.AlertaStockDTO> items,
            int requestedPage,
            int size
    ) {
        int totalElements = items.size();
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil(totalElements / (double) size);
        int page = totalPages == 0
                ? 0
                : Math.min(requestedPage, totalPages - 1);
        int from = Math.min(page * size, totalElements);
        int to = Math.min(from + size, totalElements);

        return new PaginaInformeInventarioDTO<>(
                items.subList(from, to),
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                totalPages == 0 || page >= totalPages - 1);
    }

    private enum AlertType {
        TODAS,
        STOCK_NEGATIVO,
        AGOTADO,
        BAJO_UMBRAL,
        SIN_COSTO;

        boolean matches(String value) {
            return this == TODAS || name().equals(value);
        }

        static AlertType parse(String rawValue) {
            try {
                return valueOf(normalizeEnum(rawValue));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "El tipo de alerta no es valido.");
            }
        }
    }

    private enum MaterialGroup {
        TODOS,
        MATERIA_PRIMA,
        EMPAQUE,
        OTROS;

        boolean matches(String value) {
            return this == TODOS || name().equals(value);
        }

        static MaterialGroup parse(String rawValue) {
            try {
                return valueOf(normalizeEnum(rawValue));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "El grupo de material no es valido.");
            }
        }
    }

    private enum AlertOrder {
        PRIORIDAD,
        MAYOR_BRECHA_RELATIVA,
        STOCK_ASC,
        NOMBRE;

        static AlertOrder parse(String rawValue) {
            try {
                return valueOf(normalizeEnum(rawValue));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "El orden de alertas no es valido.");
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
