package exotic.app.planta.service.produccion;

import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.PoolCapacidad;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.dto.GuardarMpsSemanalDraftRequestDTO;
import exotic.app.planta.model.produccion.dto.GuardarProgramacionProduccionSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.produccion.dto.ProgramacionProduccionSemanalItemRequestDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarBlockDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarCellDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarDayDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarRowDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalCalendarDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalItemDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalSummaryDTO;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ProgramacionProduccionSemanalService {

    private static final double EPSILON = 0.000001;

    private final TerminadoRepo terminadoRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final MasterProductionScheduleDraftService masterProductionScheduleDraftService;

    private record EntryKey(LocalDate date, String productoId) {}

    private record CapacityUnit(
            String rowKey,
            Integer categoriaId,
            String categoriaNombre,
            Integer poolCapacidadId,
            String poolCapacidadNombre,
            int capacidadDiaria
    ) {}

    private record ProductCategoryContext(
            Integer categoriaId,
            String categoriaNombre,
            Integer poolCapacidadId,
            String poolCapacidadNombre
    ) {}

    private static final class ConsolidatedEntry {
        private final LocalDate date;
        private final String productoId;
        private double unidades;

        private ConsolidatedEntry(LocalDate date, String productoId, double unidades) {
            this.date = date;
            this.productoId = productoId;
            this.unidades = unidades;
        }
    }

    private static final class ValidatedEntry {
        private final LocalDate date;
        private final Terminado terminado;
        private final double unidades;
        private final int lotes;

        private ValidatedEntry(LocalDate date, Terminado terminado, double unidades, int lotes) {
            this.date = date;
            this.terminado = terminado;
            this.unidades = unidades;
            this.lotes = lotes;
        }
    }

    private static final class ProductTotal {
        private final Terminado terminado;
        private double unidades;
        private int lotes;
        private LocalDate firstDate;
        private boolean desbordaSemana;

        private ProductTotal(Terminado terminado) {
            this.terminado = terminado;
        }

        private void add(LocalDate date, double unidades, int lotes, LocalDate weekEndDate) {
            this.unidades += unidades;
            this.lotes += lotes;
            if (firstDate == null || date.isBefore(firstDate)) {
                firstDate = date;
            }

            int tiempoDiasFabricacion = resolveTiempoDiasFabricacion(terminado);
            if (date.plusDays(Math.max(tiempoDiasFabricacion, 0)).isAfter(weekEndDate)) {
                desbordaSemana = true;
            }
        }
    }

    public MpsSemanalDraftDTO guardarBorradorDirecto(GuardarProgramacionProduccionSemanalRequestDTO request) {
        validateRequestShell(request);

        LocalDate weekStartDate = request.getWeekStartDate();
        LocalDate weekEndDate = weekStartDate.plusDays(5);
        List<ConsolidatedEntry> consolidatedEntries = consolidateEntries(request.getEntradas(), weekStartDate);
        List<String> productoIds = consolidatedEntries.stream()
                .map(entry -> entry.productoId)
                .distinct()
                .toList();

        Map<String, Terminado> terminadosById = productoIds.isEmpty()
                ? Map.of()
                : terminadoRepo.findByProductoIdIn(productoIds).stream()
                .collect(Collectors.toMap(Terminado::getProductoId, Function.identity()));

        List<ValidatedEntry> validatedEntries = new ArrayList<>();
        LinkedHashMap<String, ProductTotal> totalsByProducto = new LinkedHashMap<>();

        for (ConsolidatedEntry entry : consolidatedEntries) {
            Terminado terminado = terminadosById.get(entry.productoId);
            validateEntry(entry, terminado);

            int loteSize = resolveLoteSize(terminado);
            int lotes = calculateExactLotes(entry.unidades, loteSize, entry.productoId);
            validatedEntries.add(new ValidatedEntry(entry.date, terminado, entry.unidades, lotes));

            totalsByProducto
                    .computeIfAbsent(terminado.getProductoId(), ignored -> new ProductTotal(terminado))
                    .add(entry.date, entry.unidades, lotes, weekEndDate);
        }

        GuardarMpsSemanalDraftRequestDTO draftRequest = new GuardarMpsSemanalDraftRequestDTO();
        draftRequest.setWeekStartDate(weekStartDate);
        draftRequest.setSummary(buildSummary(totalsByProducto));
        draftRequest.setItems(buildItems(totalsByProducto));
        draftRequest.setCalendar(buildCalendar(validatedEntries, weekStartDate));

        return masterProductionScheduleDraftService.saveDraft(draftRequest);
    }

    private void validateRequestShell(GuardarProgramacionProduccionSemanalRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de programacion semanal no puede ser nula.");
        }
        if (request.getWeekStartDate() == null) {
            throw new IllegalArgumentException("weekStartDate es obligatorio.");
        }
        if (request.getWeekStartDate().getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStartDate debe corresponder a un lunes.");
        }
        if (request.getEntradas() == null || request.getEntradas().isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una entrada de programacion.");
        }
    }

    private List<ConsolidatedEntry> consolidateEntries(
            List<ProgramacionProduccionSemanalItemRequestDTO> inputEntries,
            LocalDate weekStartDate
    ) {
        LocalDate weekEndDate = weekStartDate.plusDays(5);
        LinkedHashMap<EntryKey, ConsolidatedEntry> grouped = new LinkedHashMap<>();

        for (ProgramacionProduccionSemanalItemRequestDTO inputEntry : inputEntries) {
            if (inputEntry == null) {
                continue;
            }
            if (inputEntry.getDate() == null) {
                throw new IllegalArgumentException("La fecha de cada entrada es obligatoria.");
            }
            if (inputEntry.getDate().isBefore(weekStartDate) || inputEntry.getDate().isAfter(weekEndDate)) {
                throw new IllegalArgumentException("Todas las entradas deben estar dentro de la semana lunes-sabado.");
            }
            if (inputEntry.getProductoId() == null || inputEntry.getProductoId().isBlank()) {
                throw new IllegalArgumentException("El productoId de cada entrada es obligatorio.");
            }
            if (!Double.isFinite(inputEntry.getUnidades()) || inputEntry.getUnidades() <= 0) {
                throw new IllegalArgumentException("Las unidades de cada entrada deben ser mayores que cero.");
            }

            String productoId = inputEntry.getProductoId().trim();
            EntryKey key = new EntryKey(inputEntry.getDate(), productoId);
            ConsolidatedEntry existing = grouped.get(key);
            if (existing == null) {
                grouped.put(key, new ConsolidatedEntry(inputEntry.getDate(), productoId, inputEntry.getUnidades()));
            } else {
                existing.unidades += inputEntry.getUnidades();
            }
        }

        if (grouped.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una entrada de programacion valida.");
        }

        return new ArrayList<>(grouped.values());
    }

    private void validateEntry(ConsolidatedEntry entry, Terminado terminado) {
        if (terminado == null) {
            throw new IllegalArgumentException("Producto no encontrado o no es un terminado: " + entry.productoId);
        }
        if (resolveLoteSize(terminado) <= 0) {
            throw new IllegalArgumentException("El producto terminado no tiene lote size configurado: " + entry.productoId);
        }
        if (terminado.getPrefijoLote() == null || terminado.getPrefijoLote().isBlank()) {
            throw new IllegalArgumentException("El producto terminado no tiene prefijo de lote definido: " + entry.productoId);
        }
    }

    private int calculateExactLotes(double unidades, int loteSize, String productoId) {
        double rawLotes = unidades / loteSize;
        long roundedLotes = Math.round(rawLotes);
        if (roundedLotes <= 0 || Math.abs(unidades - (roundedLotes * loteSize)) > EPSILON) {
            throw new IllegalArgumentException(
                    "Las unidades del producto " + productoId + " deben ser multiplo exacto del lote size " + loteSize + "."
            );
        }
        if (roundedLotes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("La cantidad de lotes excede el limite permitido para " + productoId + ".");
        }
        return (int) roundedLotes;
    }

    private PropuestaMpsSemanalSummaryDTO buildSummary(Map<String, ProductTotal> totalsByProducto) {
        PropuestaMpsSemanalSummaryDTO summary = new PropuestaMpsSemanalSummaryDTO();
        summary.setTotalTerminadosEvaluados(totalsByProducto.size());
        summary.setTotalPlanificables(totalsByProducto.size());
        summary.setTotalNoPlanificablesPorFaltaLoteSize(0);
        summary.setTotalLotesPropuestos(totalsByProducto.values().stream().mapToInt(total -> total.lotes).sum());
        summary.setTotalUnidadesPropuestas(totalsByProducto.values().stream().mapToDouble(total -> total.unidades).sum());
        return summary;
    }

    private List<PropuestaMpsSemanalItemDTO> buildItems(Map<String, ProductTotal> totalsByProducto) {
        List<PropuestaMpsSemanalItemDTO> items = new ArrayList<>();

        for (ProductTotal total : totalsByProducto.values()) {
            Terminado terminado = total.terminado;
            Categoria categoria = terminado.getCategoria();
            PoolCapacidad poolCapacidad = categoria != null ? categoria.getPoolCapacidad() : null;
            int tiempoDiasFabricacion = resolveTiempoDiasFabricacion(terminado);

            PropuestaMpsSemanalItemDTO item = new PropuestaMpsSemanalItemDTO();
            item.setProductoId(terminado.getProductoId());
            item.setProductoNombre(terminado.getNombre());
            item.setCategoriaNombre(categoria != null ? categoria.getCategoriaNombre() : null);
            item.setPoolCapacidadId(poolCapacidad != null ? poolCapacidad.getId() : null);
            item.setPoolCapacidadNombre(poolCapacidad != null ? poolCapacidad.getNombre() : null);
            item.setLoteSize(resolveLoteSize(terminado));
            item.setTiempoDiasFabricacion(tiempoDiasFabricacion);
            item.setStockActual(normalizeStock(transaccionAlmacenRepo.findTotalCantidadByProductoId(terminado.getProductoId())));
            item.setNecesidadManual(total.unidades);
            item.setNecesidadNeta(total.unidades);
            item.setLotesPropuestos(total.lotes);
            item.setCantidadPropuesta(total.unidades);
            item.setDeltaVsNecesidad(0);
            item.setPorcentajeParticipacion(0);
            item.setCantidadVendida(0);
            item.setValorTotal(0);
            item.setFechaLanzamientoSugerida(total.firstDate);
            item.setFechaFinalPlanificadaSugerida(total.firstDate != null ? total.firstDate.plusDays(Math.max(tiempoDiasFabricacion, 0)) : null);
            item.setDesbordaSemana(total.desbordaSemana);
            item.setPlanificable(true);
            if (total.desbordaSemana) {
                item.setWarning("La fecha final sugerida desborda la semana lunes-sabado.");
            }
            items.add(item);
        }

        return items;
    }

    private PropuestaMpsSemanalCalendarDTO buildCalendar(List<ValidatedEntry> entries, LocalDate weekStartDate) {
        PropuestaMpsSemanalCalendarDTO calendar = new PropuestaMpsSemanalCalendarDTO();
        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            PropuestaMpsCalendarDayDTO day = new PropuestaMpsCalendarDayDTO();
            day.setDayIndex(dayIndex);
            day.setDate(weekStartDate.plusDays(dayIndex));
            calendar.getDays().add(day);
        }

        LinkedHashMap<String, PropuestaMpsCalendarRowDTO> rowsByKey = new LinkedHashMap<>();
        for (ValidatedEntry entry : entries) {
            CapacityUnit capacityUnit = resolveCapacityUnit(entry.terminado);
            PropuestaMpsCalendarRowDTO row = rowsByKey.computeIfAbsent(
                    capacityUnit.rowKey(),
                    ignored -> createCalendarRow(capacityUnit, weekStartDate)
            );

            int dayIndex = (int) (entry.date.toEpochDay() - weekStartDate.toEpochDay());
            PropuestaMpsCalendarCellDTO cell = row.getDays().get(dayIndex);
            PropuestaMpsCalendarBlockDTO block = buildBlock(entry, dayIndex);
            cell.getBlocks().add(block);
            cell.setTotalAsignado(cell.getTotalAsignado() + block.getCantidadAsignada());
            cell.setEstado(resolveEstado(cell.getTotalAsignado(), cell.getCapacidadDiaria()));
        }

        rowsByKey.values().forEach(this::recalculateRow);
        calendar.setRows(new ArrayList<>(rowsByKey.values()));
        return calendar;
    }

    private PropuestaMpsCalendarRowDTO createCalendarRow(CapacityUnit capacityUnit, LocalDate weekStartDate) {
        PropuestaMpsCalendarRowDTO row = new PropuestaMpsCalendarRowDTO();
        row.setRowKey(capacityUnit.rowKey());
        row.setCategoriaId(capacityUnit.categoriaId());
        row.setCategoriaNombre(capacityUnit.categoriaNombre());
        row.setPoolCapacidadId(capacityUnit.poolCapacidadId());
        row.setPoolCapacidadNombre(capacityUnit.poolCapacidadNombre());
        row.setCapacidadDiaria(capacityUnit.capacidadDiaria());
        row.setCapacidadTeoricaSemana(Math.max(capacityUnit.capacidadDiaria(), 0) * 6);
        row.setEstadoSemana(resolveEstado(0, row.getCapacidadTeoricaSemana()));

        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            PropuestaMpsCalendarCellDTO cell = new PropuestaMpsCalendarCellDTO();
            cell.setDayIndex(dayIndex);
            cell.setDate(weekStartDate.plusDays(dayIndex));
            cell.setCapacidadDiaria(capacityUnit.capacidadDiaria());
            cell.setEstado(resolveEstado(0, capacityUnit.capacidadDiaria()));
            row.getDays().add(cell);
        }

        return row;
    }

    private PropuestaMpsCalendarBlockDTO buildBlock(ValidatedEntry entry, int dayIndex) {
        ProductCategoryContext productCategory = resolveProductCategoryContext(entry.terminado);
        PropuestaMpsCalendarBlockDTO block = new PropuestaMpsCalendarBlockDTO();
        block.setBlockId(entry.terminado.getProductoId() + "__direct-" + dayIndex);
        block.setProductoId(entry.terminado.getProductoId());
        block.setProductoNombre(entry.terminado.getNombre());
        block.setCategoriaId(productCategory.categoriaId());
        block.setCategoriaNombre(productCategory.categoriaNombre());
        block.setPoolCapacidadId(productCategory.poolCapacidadId());
        block.setPoolCapacidadNombre(productCategory.poolCapacidadNombre());
        block.setLoteSize(resolveLoteSize(entry.terminado));
        block.setLotesAsignados(entry.lotes);
        block.setCantidadAsignada(entry.unidades);
        block.setWarning(null);
        return block;
    }

    private void recalculateRow(PropuestaMpsCalendarRowDTO row) {
        double totalSemana = row.getDays().stream()
                .mapToDouble(PropuestaMpsCalendarCellDTO::getTotalAsignado)
                .sum();
        row.setTotalAsignadoSemana(totalSemana);
        row.setEstadoSemana(resolveEstado(totalSemana, row.getCapacidadTeoricaSemana()));
    }

    private CapacityUnit resolveCapacityUnit(Terminado terminado) {
        Categoria categoria = terminado.getCategoria();
        PoolCapacidad poolCapacidad = categoria != null ? categoria.getPoolCapacidad() : null;
        if (poolCapacidad != null && poolCapacidad.getId() != null) {
            String poolNombre = poolCapacidad.getNombre() != null && !poolCapacidad.getNombre().isBlank()
                    ? poolCapacidad.getNombre()
                    : "Pool sin nombre";
            return new CapacityUnit(
                    "pool::" + poolCapacidad.getId(),
                    null,
                    null,
                    poolCapacidad.getId(),
                    poolNombre,
                    normalizeCapacidad(poolCapacidad.getCapacidadDiaria())
            );
        }

        if (categoria == null) {
            return new CapacityUnit("sin-categoria", null, "Sin categoria", null, null, 0);
        }

        return new CapacityUnit(
                "categoria::" + categoria.getCategoriaId(),
                categoria.getCategoriaId(),
                categoria.getCategoriaNombre(),
                null,
                null,
                normalizeCapacidad(categoria.getCapacidadProductivaDiaria())
        );
    }

    private ProductCategoryContext resolveProductCategoryContext(Terminado terminado) {
        Categoria categoria = terminado.getCategoria();
        PoolCapacidad poolCapacidad = categoria != null ? categoria.getPoolCapacidad() : null;
        return new ProductCategoryContext(
                categoria != null ? categoria.getCategoriaId() : null,
                categoria != null ? categoria.getCategoriaNombre() : null,
                poolCapacidad != null ? poolCapacidad.getId() : null,
                poolCapacidad != null ? poolCapacidad.getNombre() : null
        );
    }

    private String resolveEstado(double totalAsignado, int capacidad) {
        if (capacidad <= 0) {
            return "sin_configurar";
        }
        if (totalAsignado > capacidad) {
            return "excedida";
        }
        if (Double.compare(totalAsignado, capacidad) == 0) {
            return "al_limite";
        }
        return "disponible";
    }

    private static int resolveLoteSize(Terminado terminado) {
        Categoria categoria = terminado != null ? terminado.getCategoria() : null;
        return categoria != null ? categoria.getLoteSize() : 0;
    }

    private static int resolveTiempoDiasFabricacion(Terminado terminado) {
        Categoria categoria = terminado != null ? terminado.getCategoria() : null;
        return categoria != null ? categoria.getTiempoDiasFabricacion() : 0;
    }

    private int normalizeCapacidad(Integer capacidad) {
        return capacidad != null ? Math.max(capacidad, 0) : 0;
    }

    private double normalizeStock(Double stock) {
        return stock != null ? stock : 0.0;
    }
}
