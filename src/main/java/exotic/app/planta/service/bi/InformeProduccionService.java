package exotic.app.planta.service.bi;

import exotic.app.planta.model.bi.dto.InformeGlobalProduccionDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.EstadoMpsSemanalItem;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalDia;
import exotic.app.planta.model.produccion.MpsSemanalItem;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.MpsSemanalDiaRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InformeProduccionService {
    private final TransaccionAlmacenRepo movementRepo;
    private final MasterProductionScheduleSemanalRepo mpsRepo;
    private final MpsSemanalDiaRepo mpsDayRepo;
    private final CategoriaRepo categoryRepo;

    public InformeGlobalProduccionDTO obtenerReporte(LocalDate startDate, LocalDate endDate) {
        validateDates(startDate, endDate);
        int rangeDays = Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);

        List<Movimiento> productionMovements = findFinishedProductReceipts(
                startDate,
                endDate);
        double previousProduction = totalProduced(
                startDate.minusDays(rangeDays),
                startDate.minusDays(1));

        Map<String, ProductionReference> references = new LinkedHashMap<>();
        Set<Integer> mpsIds = loadPlannedProduction(
                startDate,
                endDate,
                references);
        addActualProduction(productionMovements, references);

        Map<Integer, Categoria> categories = loadCategories(references.values());
        Map<String, CategorySummary> categorySummaries = summarizeByCategory(
                references.values(),
                categories,
                rangeDays);
        InformeGlobalProduccionDTO.ResumenDTO summary = buildSummary(
                references.values(),
                categorySummaries.values(),
                previousProduction,
                productionMovements.size());

        return InformeGlobalProduccionDTO.builder()
                .fechaDesde(startDate)
                .fechaHasta(endDate)
                .modoFecha(startDate.equals(endDate) ? "FECHA_UNICA" : "RANGO")
                .diasRango(rangeDays)
                .mpsIds(new ArrayList<>(mpsIds))
                .resumen(summary)
                .consolidadoCategorias(categorySummaries.values().stream()
                        .sorted(Comparator.comparing(CategorySummary::displayName))
                        .map(CategorySummary::toDto)
                        .toList())
                .detalleReferencias(references.values().stream()
                        .sorted(Comparator
                                .comparing(ProductionReference::categoryDisplayName)
                                .thenComparing(ProductionReference::productDisplayName))
                        .map(ProductionReference::toDto)
                        .toList())
                .notas(buildNotes(mpsIds, summary))
                .build();
    }

    private Set<Integer> loadPlannedProduction(
            LocalDate startDate,
            LocalDate endDate,
            Map<String, ProductionReference> references
    ) {
        List<MasterProductionScheduleSemanal> candidates =
                mpsRepo.findAllOverlappingRange(startDate, endDate);
        Set<Integer> mpsIds = new LinkedHashSet<>();
        Map<LocalDate, Integer> effectiveMpsByDate = new LinkedHashMap<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDate currentDate = date;
            candidates.stream()
                    .filter(mps -> !mps.getWeekStartDate().isAfter(currentDate)
                            && !mps.getWeekEndDate().isBefore(currentDate))
                    .findFirst()
                    .ifPresent(mps -> {
                        mpsIds.add(mps.getMpsId());
                        effectiveMpsByDate.put(currentDate, mps.getMpsId());
                    });
        }

        if (!mpsIds.isEmpty()) {
            mpsDayRepo.findAllByMpsIdsAndDateRange(
                            new ArrayList<>(mpsIds),
                            startDate,
                            endDate)
                    .stream()
                    .filter(day -> Objects.equals(
                            effectiveMpsByDate.get(day.getFecha()),
                            day.getMpsSemanal().getMpsId()))
                    .forEach(day -> addPlannedDay(day, references));
        }
        return mpsIds;
    }

    private void addPlannedDay(
            MpsSemanalDia day,
            Map<String, ProductionReference> references
    ) {
        for (MpsSemanalItem item : day.getItems()) {
            if (item.getEstado() == EstadoMpsSemanalItem.CANCELADO) continue;

            FinishedProductData productData = productData(item.getTerminado());
            String productId = productData.productId() != null
                    ? productData.productId()
                    : "MPS-" + item.getId();
            String productName = firstNonBlank(
                    item.getTerminadoNombre(),
                    productData.productName(),
                    productId);
            ProductionReference reference = references.computeIfAbsent(
                    productId,
                    ignored -> new ProductionReference(productId, productName));
            reference.updateProduct(productData);
            reference.updateCategory(
                    item.getCategoriaId(),
                    firstNonBlank(item.getCategoriaNombre(), productData.categoryName()));
            reference.addPlanned(item.getCantidadTotal());
        }
    }

    private void addActualProduction(
            List<Movimiento> movements,
            Map<String, ProductionReference> references
    ) {
        for (Movimiento movement : movements) {
            FinishedProductData productData = productData(movement.getProducto());
            String productId = productData.productId() != null
                    ? productData.productId()
                    : "MOV-" + movement.getMovimientoId();
            String productName = firstNonBlank(productData.productName(), "Producto sin nombre");
            ProductionReference reference = references.computeIfAbsent(
                    productId,
                    ignored -> new ProductionReference(productId, productName));
            reference.updateProduct(productData);
            reference.addProduced(movement.getCantidad());
        }
    }

    private Map<Integer, Categoria> loadCategories(
            Collection<ProductionReference> references
    ) {
        Set<Integer> categoryIds = new HashSet<>();
        references.stream()
                .map(ProductionReference::categoryId)
                .filter(Objects::nonNull)
                .forEach(categoryIds::add);

        Map<Integer, Categoria> categories = new HashMap<>();
        categoryRepo.findAllById(categoryIds)
                .forEach(category -> categories.put(category.getCategoriaId(), category));
        return categories;
    }

    private Map<String, CategorySummary> summarizeByCategory(
            Collection<ProductionReference> references,
            Map<Integer, Categoria> categories,
            int rangeDays
    ) {
        Map<String, CategorySummary> summaries = new HashMap<>();
        for (ProductionReference reference : references) {
            int periodCapacity = dailyCapacity(categories, reference.categoryId()) * rangeDays;
            String categoryName = displayCategory(reference.categoryName());
            String categoryKey = reference.categoryId() == null
                    ? "NAME:" + categoryName
                    : "ID:" + reference.categoryId();
            summaries.computeIfAbsent(
                            categoryKey,
                            ignored -> new CategorySummary(
                                    reference.categoryId(),
                                    categoryName,
                                    periodCapacity))
                    .add(reference);
        }
        return summaries;
    }

    private InformeGlobalProduccionDTO.ResumenDTO buildSummary(
            Collection<ProductionReference> references,
            Collection<CategorySummary> categories,
            double previousProduction,
            int movementCount
    ) {
        double plannedUnits = references.stream()
                .mapToDouble(ProductionReference::plannedQuantity)
                .sum();
        double producedUnits = references.stream()
                .mapToDouble(ProductionReference::producedQuantity)
                .sum();
        double periodCapacity = categories.stream()
                .mapToDouble(CategorySummary::periodCapacity)
                .sum();
        int plannedReferences = countReferences(references, true, false);
        int producedReferences = countReferences(references, false, true);
        int plannedAndProducedReferences = countReferences(references, true, true);
        int unplannedReferences = Math.toIntExact(references.stream()
                .filter(reference -> reference.plannedQuantity() <= 0
                        && reference.producedQuantity() > 0)
                .count());
        int categoriesWithCapacity = Math.toIntExact(categories.stream()
                .filter(category -> category.periodCapacity() > 0)
                .count());

        return InformeGlobalProduccionDTO.ResumenDTO.builder()
                .unidadesPlaneadas(plannedUnits)
                .unidadesProducidas(producedUnits)
                .unidadesProducidasPeriodoAnterior(previousProduction)
                .capacidadProductivaPeriodo(periodCapacity)
                .rendimientoPlaneacionPct(percentage(producedUnits, plannedUnits))
                .cumplimientoReferenciasPct(percentage(
                        plannedAndProducedReferences,
                        plannedReferences))
                .capacidadUtilizadaPct(percentage(producedUnits, periodCapacity))
                .tendenciaProduccionPct(trend(producedUnits, previousProduction))
                .referenciasPlaneadas(plannedReferences)
                .referenciasProducidas(producedReferences)
                .referenciasPlaneadasProducidas(plannedAndProducedReferences)
                .referenciasNoPlaneadas(unplannedReferences)
                .categoriasConCapacidad(categoriesWithCapacity)
                .categoriasSinCapacidad(categories.size() - categoriesWithCapacity)
                .movimientosProduccion(movementCount)
                .build();
    }

    private int countReferences(
            Collection<ProductionReference> references,
            boolean requirePlanned,
            boolean requireProduced
    ) {
        return Math.toIntExact(references.stream()
                .filter(reference -> !requirePlanned || reference.plannedQuantity() > 0)
                .filter(reference -> !requireProduced || reference.producedQuantity() > 0)
                .count());
    }

    private List<InformeGlobalProduccionDTO.NotaDTO> buildNotes(
            Set<Integer> mpsIds,
            InformeGlobalProduccionDTO.ResumenDTO summary
    ) {
        List<InformeGlobalProduccionDTO.NotaDTO> notes = new ArrayList<>();
        if (mpsIds.isEmpty()) {
            notes.add(note(
                    "WARNING",
                    "No se encontro MPS para el periodo seleccionado; los indicadores "
                            + "contra planeacion pueden quedar sin base."));
        }
        if (summary.getCategoriasSinCapacidad() > 0) {
            notes.add(note(
                    "INFO",
                    "Hay categorias sin capacidad productiva configurada; la capacidad "
                            + "utilizada puede quedar incompleta."));
        }
        if (summary.getReferenciasNoPlaneadas() > 0) {
            notes.add(note(
                    "INFO",
                    "Se encontraron referencias producidas que no estaban planeadas en "
                            + "el MPS del periodo."));
        }
        return notes;
    }

    private InformeGlobalProduccionDTO.NotaDTO note(String type, String message) {
        return InformeGlobalProduccionDTO.NotaDTO.builder()
                .tipo(type)
                .mensaje(message)
                .build();
    }

    private List<Movimiento> findFinishedProductReceipts(
            LocalDate startDate,
            LocalDate endDate
    ) {
        DateTimeRange range = toDateTimeRange(startDate, endDate);
        return movementRepo.findIngresosTerminadoPorFechaEfectiva(
                startDate,
                endDate,
                range.start(),
                range.end(),
                Movimiento.TipoMovimiento.BACKFLUSH);
    }

    private double totalProduced(LocalDate startDate, LocalDate endDate) {
        DateTimeRange range = toDateTimeRange(startDate, endDate);
        Double total = movementRepo.sumIngresosTerminadoPorFechaEfectiva(
                startDate,
                endDate,
                range.start(),
                range.end(),
                Movimiento.TipoMovimiento.BACKFLUSH);
        return total == null ? 0d : total;
    }

    private FinishedProductData productData(Producto product) {
        Integer categoryId = null;
        String categoryName = null;
        if (product instanceof Terminado finishedProduct
                && finishedProduct.getCategoria() != null) {
            categoryId = finishedProduct.getCategoria().getCategoriaId();
            categoryName = finishedProduct.getCategoria().getCategoriaNombre();
        }
        return new FinishedProductData(
                product == null ? null : product.getProductoId(),
                product == null ? null : product.getNombre(),
                categoryId,
                categoryName);
    }

    private int dailyCapacity(Map<Integer, Categoria> categories, Integer categoryId) {
        Categoria category = categoryId == null ? null : categories.get(categoryId);
        return category == null ? 0 : category.getCapacidadProductivaDiaria();
    }

    private DateTimeRange toDateTimeRange(LocalDate startDate, LocalDate endDate) {
        return new DateTimeRange(startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX));
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("fechaDesde y fechaHasta son obligatorias");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("fechaDesde no puede ser posterior a fechaHasta");
        }
    }

    private static Double percentage(double numerator, double denominator) {
        return denominator <= 0 ? null : numerator * 100d / denominator;
    }

    private static Double trend(double current, double previous) {
        return previous <= 0 ? null : (current - previous) * 100d / previous;
    }

    private static String displayCategory(String categoryName) {
        return firstNonBlank(categoryName, "Sin categoria");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private record DateTimeRange(LocalDateTime start, LocalDateTime end) {
    }

    private record FinishedProductData(
            String productId,
            String productName,
            Integer categoryId,
            String categoryName
    ) {
    }

    private static final class ProductionReference {
        private final String productId;
        private String productName;
        private Integer categoryId;
        private String categoryName;
        private double plannedQuantity;
        private double producedQuantity;

        private ProductionReference(String productId, String productName) {
            this.productId = productId;
            this.productName = productName;
        }

        void updateProduct(FinishedProductData product) {
            if (product.productName() != null && !product.productName().isBlank()) {
                productName = product.productName();
            }
            updateCategory(product.categoryId(), product.categoryName());
        }

        void updateCategory(Integer newCategoryId, String newCategoryName) {
            if (categoryId == null && newCategoryId != null) categoryId = newCategoryId;
            if ((categoryName == null || categoryName.isBlank())
                    && newCategoryName != null
                    && !newCategoryName.isBlank()) {
                categoryName = newCategoryName;
            }
        }

        void addPlanned(double quantity) {
            plannedQuantity += quantity;
        }

        void addProduced(double quantity) {
            producedQuantity += quantity;
        }

        Integer categoryId() {
            return categoryId;
        }

        String categoryName() {
            return categoryName;
        }

        String categoryDisplayName() {
            return displayCategory(categoryName);
        }

        String productDisplayName() {
            return firstNonBlank(productName, productId);
        }

        double plannedQuantity() {
            return plannedQuantity;
        }

        double producedQuantity() {
            return producedQuantity;
        }

        InformeGlobalProduccionDTO.DetalleReferenciaDTO toDto() {
            boolean planned = plannedQuantity > 0;
            boolean produced = producedQuantity > 0;
            return InformeGlobalProduccionDTO.DetalleReferenciaDTO.builder()
                    .productoId(productId)
                    .productoNombre(productDisplayName())
                    .categoriaId(categoryId)
                    .categoriaNombre(categoryDisplayName())
                    .cantidadPlaneada(plannedQuantity)
                    .cantidadProducida(producedQuantity)
                    .diferencia(producedQuantity - plannedQuantity)
                    .rendimientoPlaneacionPct(percentage(
                            producedQuantity,
                            plannedQuantity))
                    .planeado(planned)
                    .producido(produced)
                    .noPlaneado(!planned && produced)
                    .build();
        }
    }

    private static final class CategorySummary {
        private final Integer categoryId;
        private final String categoryName;
        private final int periodCapacity;
        private double plannedQuantity;
        private double producedQuantity;
        private int plannedReferences;
        private int producedReferences;
        private int plannedAndProducedReferences;

        private CategorySummary(
                Integer categoryId,
                String categoryName,
                int periodCapacity
        ) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.periodCapacity = periodCapacity;
        }

        void add(ProductionReference reference) {
            plannedQuantity += reference.plannedQuantity();
            producedQuantity += reference.producedQuantity();
            if (reference.plannedQuantity() > 0) plannedReferences++;
            if (reference.producedQuantity() > 0) producedReferences++;
            if (reference.plannedQuantity() > 0 && reference.producedQuantity() > 0) {
                plannedAndProducedReferences++;
            }
        }

        String displayName() {
            return displayCategory(categoryName);
        }

        int periodCapacity() {
            return periodCapacity;
        }

        InformeGlobalProduccionDTO.ConsolidadoCategoriaDTO toDto() {
            return InformeGlobalProduccionDTO.ConsolidadoCategoriaDTO.builder()
                    .categoriaId(categoryId)
                    .categoriaNombre(displayName())
                    .unidadesPlaneadas(plannedQuantity)
                    .unidadesProducidas(producedQuantity)
                    .capacidadProductivaPeriodo(periodCapacity)
                    .rendimientoPlaneacionPct(percentage(
                            producedQuantity,
                            plannedQuantity))
                    .cumplimientoReferenciasPct(percentage(
                            plannedAndProducedReferences,
                            plannedReferences))
                    .capacidadUtilizadaPct(percentage(
                            producedQuantity,
                            periodCapacity))
                    .referenciasPlaneadas(plannedReferences)
                    .referenciasProducidas(producedReferences)
                    .referenciasPlaneadasProducidas(plannedAndProducedReferences)
                    .build();
        }
    }
}
