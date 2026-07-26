package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Component
class AjustesInventarioAssembler {
    static final String RAW_MATERIAL = "MATERIA_PRIMA";
    static final String PACKAGING = "EMPAQUE";
    static final String OTHER = "OTROS";
    static final int TOP_IMPACT_LIMIT = 5;

    private static final List<String> GROUP_ORDER =
            List.of(RAW_MATERIAL, PACKAGING, OTHER);
    private static final Set<Movimiento.TipoMovimiento> ADJUSTMENT_TYPES = Set.of(
            Movimiento.TipoMovimiento.AJUSTE_POSITIVO,
            Movimiento.TipoMovimiento.AJUSTE_NEGATIVO);

    InformeInventarioDTO.AjustesInventarioDTO assemble(
            List<Movimiento> periodMovements,
            List<Movimiento> trendMovements,
            LocalDate trendStartDate,
            LocalDate trendEndDate
    ) {
        SummaryAccumulator global = new SummaryAccumulator();
        Map<String, SummaryAccumulator> byGroup = emptyGroupSummaries();

        periodMovements.stream()
                .filter(this::isAdjustment)
                .forEach(movement -> {
                    double estimatedValue = estimatedValue(movement);
                    global.add(movement, estimatedValue);
                    byGroup.get(groupOf(movement.getProducto()))
                            .add(movement, estimatedValue);
                });

        double materialImpact = byGroup.get(RAW_MATERIAL).totalImpact()
                + byGroup.get(PACKAGING).totalImpact();

        return InformeInventarioDTO.AjustesInventarioDTO.builder()
                .resumen(global.toSummary())
                .comparativo(InformeInventarioDTO.ComparativoAjustesDTO.builder()
                        .materiaPrima(byGroup.get(RAW_MATERIAL)
                                .toGroup(RAW_MATERIAL, materialImpact))
                        .empaque(byGroup.get(PACKAGING)
                                .toGroup(PACKAGING, materialImpact))
                        .otros(byGroup.get(OTHER).toGroup(OTHER, 0))
                        .build())
                .serieDiaria(buildDailySeries(
                        trendMovements,
                        trendStartDate,
                        trendEndDate))
                .mayorImpacto(InformeInventarioDTO.MayorImpactoAjustesDTO.builder()
                        .limite(TOP_IMPACT_LIMIT)
                        .materiaPrima(topImpact(periodMovements, RAW_MATERIAL))
                        .empaque(topImpact(periodMovements, PACKAGING))
                        .build())
                .build();
    }

    List<InformeInventarioDTO.MaterialImpactoAjusteDTO> aggregateMaterials(
            List<Movimiento> movements,
            String group,
            Set<Movimiento.TipoMovimiento> includedTypes
    ) {
        Map<String, MaterialAccumulator> byProduct = new HashMap<>();

        movements.stream()
                .filter(this::isAdjustment)
                .filter(movement -> movement.getProducto() instanceof Material)
                .filter(movement -> group.equals(groupOf(movement.getProducto())))
                .filter(movement -> includedTypes.contains(movement.getTipoMovimiento()))
                .forEach(movement -> byProduct
                        .computeIfAbsent(
                                movement.getProducto().getProductoId(),
                                ignored -> new MaterialAccumulator(
                                        (Material) movement.getProducto()))
                        .add(movement, estimatedValue(movement)));

        return byProduct.values().stream()
                .map(MaterialAccumulator::toDto)
                .toList();
    }

    Comparator<InformeInventarioDTO.MaterialImpactoAjusteDTO> impactComparator() {
        return Comparator
                .comparingDouble(
                        InformeInventarioDTO.MaterialImpactoAjusteDTO::impactoEstimado)
                .reversed()
                .thenComparing(
                        Comparator.comparingInt(
                                InformeInventarioDTO.MaterialImpactoAjusteDTO::movimientos)
                                .reversed())
                .thenComparing(
                        InformeInventarioDTO.MaterialImpactoAjusteDTO::ultimoAjuste,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                        InformeInventarioDTO.MaterialImpactoAjusteDTO::productoId,
                        String.CASE_INSENSITIVE_ORDER);
    }

    boolean isAdjustment(Movimiento movement) {
        return movement != null
                && movement.isAfectaInventario()
                && movement.getAlmacen() == Movimiento.Almacen.GENERAL
                && ADJUSTMENT_TYPES.contains(movement.getTipoMovimiento())
                && movement.getProducto() != null;
    }

    private List<InformeInventarioDTO.MaterialImpactoAjusteDTO> topImpact(
            List<Movimiento> movements,
            String group
    ) {
        return aggregateMaterials(movements, group, ADJUSTMENT_TYPES).stream()
                .sorted(impactComparator())
                .limit(TOP_IMPACT_LIMIT)
                .toList();
    }

    private List<InformeInventarioDTO.SerieAjusteDTO> buildDailySeries(
            List<Movimiento> movements,
            LocalDate startDate,
            LocalDate endDate
    ) {
        List<Movimiento> adjustments = movements.stream()
                .filter(this::isAdjustment)
                .toList();
        if (adjustments.isEmpty()) return List.of();

        Map<String, Set<String>> unitsByGroup = new LinkedHashMap<>();
        for (String group : GROUP_ORDER) {
            unitsByGroup.put(group, new TreeSet<>());
        }
        adjustments.forEach(movement -> unitsByGroup
                .get(groupOf(movement.getProducto()))
                .add(InventarioBiUtils.unitOf(movement.getProducto())));

        Map<SeriesKey, DailyAccumulator> series = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            for (String group : GROUP_ORDER) {
                for (String unit : unitsByGroup.get(group)) {
                    SeriesKey key = new SeriesKey(date, group, unit);
                    series.put(key, new DailyAccumulator(date, group, unit));
                }
            }
        }

        adjustments.forEach(movement -> {
            if (movement.getFechaMovimiento() == null) return;

            String group = groupOf(movement.getProducto());
            String unit = InventarioBiUtils.unitOf(movement.getProducto());
            LocalDate date = movement.getFechaMovimiento().toLocalDate();
            SeriesKey key = new SeriesKey(date, group, unit);
            series.computeIfAbsent(
                            key,
                            ignored -> new DailyAccumulator(date, group, unit))
                    .add(movement, estimatedValue(movement));
        });

        return series.values().stream()
                .sorted(Comparator
                        .comparing(DailyAccumulator::date)
                        .thenComparingInt(value -> groupIndex(value.group()))
                        .thenComparing(DailyAccumulator::unit))
                .map(DailyAccumulator::toDto)
                .toList();
    }

    private Map<String, SummaryAccumulator> emptyGroupSummaries() {
        Map<String, SummaryAccumulator> summaries = new LinkedHashMap<>();
        GROUP_ORDER.forEach(group -> summaries.put(group, new SummaryAccumulator()));
        return summaries;
    }

    private double estimatedValue(Movimiento movement) {
        Producto product = movement.getProducto();
        return InventarioBiUtils.hasValidCost(product)
                ? Math.abs(movement.getCantidad())
                * InventarioBiUtils.costAsDouble(product)
                : 0;
    }

    private String groupOf(Producto product) {
        String type = InventarioBiUtils.inventoryTypeOf(product);
        return RAW_MATERIAL.equals(type) || PACKAGING.equals(type)
                ? type
                : OTHER;
    }

    private static int groupIndex(String group) {
        int index = GROUP_ORDER.indexOf(group);
        return index < 0 ? GROUP_ORDER.size() : index;
    }

    private static final class SummaryAccumulator {
        private int positiveMovements;
        private int negativeMovements;
        private double positiveValue;
        private double negativeValue;
        private final Set<String> positiveProducts = new HashSet<>();
        private final Set<String> negativeProducts = new HashSet<>();
        private final Set<String> products = new HashSet<>();
        private final Set<TransaccionAlmacen> transactions = new HashSet<>();

        void add(Movimiento movement, double estimatedValue) {
            String productId = movement.getProducto().getProductoId();
            products.add(productId);
            if (movement.getTransaccionAlmacen() != null) {
                transactions.add(movement.getTransaccionAlmacen());
            }

            if (movement.getTipoMovimiento()
                    == Movimiento.TipoMovimiento.AJUSTE_POSITIVO) {
                positiveMovements++;
                positiveValue += estimatedValue;
                positiveProducts.add(productId);
            } else {
                negativeMovements++;
                negativeValue += estimatedValue;
                negativeProducts.add(productId);
            }
        }

        double totalImpact() {
            return positiveValue + negativeValue;
        }

        InformeInventarioDTO.ResumenAjustesDTO toSummary() {
            return InformeInventarioDTO.ResumenAjustesDTO.builder()
                    .positivos(toFlow(
                            positiveMovements,
                            positiveProducts.size(),
                            positiveValue))
                    .negativos(toFlow(
                            negativeMovements,
                            negativeProducts.size(),
                            negativeValue))
                    .balanceNeto(positiveValue - negativeValue)
                    .transacciones(transactions.size())
                    .movimientos(positiveMovements + negativeMovements)
                    .referencias(products.size())
                    .build();
        }

        InformeInventarioDTO.GrupoAjustesDTO toGroup(
                String group,
                double materialImpact
        ) {
            return InformeInventarioDTO.GrupoAjustesDTO.builder()
                    .grupo(group)
                    .positivos(toFlow(
                            positiveMovements,
                            positiveProducts.size(),
                            positiveValue))
                    .negativos(toFlow(
                            negativeMovements,
                            negativeProducts.size(),
                            negativeValue))
                    .balanceNeto(positiveValue - negativeValue)
                    .transacciones(transactions.size())
                    .movimientos(positiveMovements + negativeMovements)
                    .referencias(products.size())
                    .participacionValorAjustadoPct(
                            InventarioBiUtils.percentage(totalImpact(), materialImpact))
                    .build();
        }

        private InformeInventarioDTO.FlujoDTO toFlow(
                int movements,
                int references,
                double value
        ) {
            return InformeInventarioDTO.FlujoDTO.builder()
                    .movimientos(movements)
                    .referencias(references)
                    .valorEstimado(value)
                    .build();
        }
    }

    private static final class MaterialAccumulator {
        private final Material material;
        private double positiveQuantity;
        private double negativeQuantity;
        private double positiveValue;
        private double negativeValue;
        private int movements;
        private final Set<TransaccionAlmacen> transactions = new HashSet<>();
        private LocalDateTime lastAdjustment;

        private MaterialAccumulator(Material material) {
            this.material = material;
        }

        void add(Movimiento movement, double estimatedValue) {
            double quantity = Math.abs(movement.getCantidad());
            if (movement.getTipoMovimiento()
                    == Movimiento.TipoMovimiento.AJUSTE_POSITIVO) {
                positiveQuantity += quantity;
                positiveValue += estimatedValue;
            } else {
                negativeQuantity += quantity;
                negativeValue += estimatedValue;
            }

            movements++;
            if (movement.getTransaccionAlmacen() != null) {
                transactions.add(movement.getTransaccionAlmacen());
            }
            if (movement.getFechaMovimiento() != null
                    && (lastAdjustment == null
                    || movement.getFechaMovimiento().isAfter(lastAdjustment))) {
                lastAdjustment = movement.getFechaMovimiento();
            }
        }

        InformeInventarioDTO.MaterialImpactoAjusteDTO toDto() {
            return InformeInventarioDTO.MaterialImpactoAjusteDTO.builder()
                    .productoId(material.getProductoId())
                    .productoNombre(material.getNombre())
                    .unidadMedida(InventarioBiUtils.unitOf(material))
                    .cantidadPositiva(positiveQuantity)
                    .cantidadNegativa(negativeQuantity)
                    .balanceCantidad(positiveQuantity - negativeQuantity)
                    .valorPositivo(positiveValue)
                    .valorNegativo(negativeValue)
                    .balanceValor(positiveValue - negativeValue)
                    .impactoEstimado(positiveValue + negativeValue)
                    .movimientos(movements)
                    .transacciones(transactions.size())
                    .ultimoAjuste(lastAdjustment)
                    .costoVigente(InventarioBiUtils.hasValidCost(material))
                    .build();
        }
    }

    private record SeriesKey(LocalDate date, String group, String unit) {
    }

    private static final class DailyAccumulator {
        private final LocalDate date;
        private final String group;
        private final String unit;
        private double positiveQuantity;
        private double negativeQuantity;
        private double positiveValue;
        private double negativeValue;

        private DailyAccumulator(LocalDate date, String group, String unit) {
            this.date = date;
            this.group = group;
            this.unit = unit;
        }

        void add(Movimiento movement, double estimatedValue) {
            double quantity = Math.abs(movement.getCantidad());
            if (movement.getTipoMovimiento()
                    == Movimiento.TipoMovimiento.AJUSTE_POSITIVO) {
                positiveQuantity += quantity;
                positiveValue += estimatedValue;
            } else {
                negativeQuantity += quantity;
                negativeValue += estimatedValue;
            }
        }

        LocalDate date() {
            return date;
        }

        String group() {
            return group;
        }

        String unit() {
            return unit;
        }

        InformeInventarioDTO.SerieAjusteDTO toDto() {
            return InformeInventarioDTO.SerieAjusteDTO.builder()
                    .fecha(date)
                    .grupo(group)
                    .unidadMedida(unit)
                    .cantidadPositiva(positiveQuantity)
                    .cantidadNegativa(negativeQuantity)
                    .valorPositivo(positiveValue)
                    .valorNegativo(negativeValue)
                    .build();
        }
    }
}
