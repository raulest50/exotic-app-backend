package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalItemDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalItemRequestDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarBlockDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarCellDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarDayDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsCalendarRowDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalCalendarDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalResponseDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalSummaryDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsUnscheduledBlockDTO;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
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
@Transactional(readOnly = true)
public class MasterProductionScheduleService {

    private final TerminadoRepo terminadoRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;

    public PropuestaMpsSemanalResponseDTO calcularPropuestaSemanal(PropuestaMpsSemanalRequestDTO request) {
        if (request == null || request.getWeekStartDate() == null) {
            throw new IllegalArgumentException("weekStartDate es obligatorio.");
        }

        LocalDate weekStartDate = request.getWeekStartDate();
        if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStartDate debe corresponder a un lunes.");
        }

        LocalDate weekEndDate = weekStartDate.plusDays(5);
        List<PropuestaMpsSemanalItemRequestDTO> consolidatedItems = consolidateItems(request.getItems());

        List<String> productoIds = consolidatedItems.stream()
                .map(PropuestaMpsSemanalItemRequestDTO::getProductoId)
                .toList();

        Map<String, Terminado> terminadosMap = productoIds.isEmpty()
                ? Map.of()
                : terminadoRepo.findByProductoIdIn(productoIds).stream()
                        .collect(Collectors.toMap(Terminado::getProductoId, Function.identity()));

        List<PropuestaMpsSemanalItemDTO> responseItems = new ArrayList<>();
        PropuestaMpsSemanalSummaryDTO summary = new PropuestaMpsSemanalSummaryDTO();

        for (PropuestaMpsSemanalItemRequestDTO inputItem : consolidatedItems) {
            PropuestaMpsSemanalItemDTO item = buildItemProposal(inputItem, weekStartDate, weekEndDate, terminadosMap.get(inputItem.getProductoId()));
            responseItems.add(item);

            summary.setTotalTerminadosEvaluados(summary.getTotalTerminadosEvaluados() + 1);
            if (item.isPlanificable()) {
                summary.setTotalPlanificables(summary.getTotalPlanificables() + 1);
            }
            if (item.getWarning() != null && item.getWarning().contains("lote size")) {
                summary.setTotalNoPlanificablesPorFaltaLoteSize(summary.getTotalNoPlanificablesPorFaltaLoteSize() + 1);
            }
            summary.setTotalLotesPropuestos(summary.getTotalLotesPropuestos() + item.getLotesPropuestos());
            summary.setTotalUnidadesPropuestas(summary.getTotalUnidadesPropuestas() + item.getCantidadPropuesta());
        }

        PropuestaMpsSemanalResponseDTO response = new PropuestaMpsSemanalResponseDTO();
        response.setWeekStartDate(weekStartDate);
        response.setWeekEndDate(weekEndDate);
        response.setSummary(summary);
        response.setItems(responseItems);
        response.setCalendar(buildCalendar(responseItems, terminadosMap, weekStartDate));
        return response;
    }

    private PropuestaMpsSemanalCalendarDTO buildCalendar(
            List<PropuestaMpsSemanalItemDTO> items,
            Map<String, Terminado> terminadosMap,
            LocalDate weekStartDate
    ) {
        PropuestaMpsSemanalCalendarDTO calendar = new PropuestaMpsSemanalCalendarDTO();
        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            PropuestaMpsCalendarDayDTO day = new PropuestaMpsCalendarDayDTO();
            day.setDayIndex(dayIndex);
            day.setDate(weekStartDate.plusDays(dayIndex));
            calendar.getDays().add(day);
        }

        LinkedHashMap<String, PropuestaMpsCalendarRowDTO> rowsMap = new LinkedHashMap<>();
        for (PropuestaMpsSemanalItemDTO item : items) {
            Terminado terminado = terminadosMap.get(item.getProductoId());
            Categoria categoria = terminado != null ? terminado.getCategoria() : null;
            Integer categoriaId = categoria != null ? categoria.getCategoriaId() : null;
            String categoriaNombre = categoria != null ? categoria.getCategoriaNombre() : "Sin categoria";
            int capacidadDiaria = categoria != null ? categoria.getCapacidadProductivaDiaria() : 0;
            String rowKey = categoriaId != null ? String.valueOf(categoriaId) : "sin-categoria";

            rowsMap.computeIfAbsent(rowKey, key -> createCalendarRow(categoriaId, categoriaNombre, capacidadDiaria, weekStartDate));
            scheduleItemIntoCalendar(calendar, rowsMap.get(rowKey), item, categoriaId, categoriaNombre, capacidadDiaria);
        }

        rowsMap.values().forEach(this::recalculateRowState);
        calendar.setRows(new ArrayList<>(rowsMap.values()));
        return calendar;
    }

    private PropuestaMpsCalendarRowDTO createCalendarRow(
            Integer categoriaId,
            String categoriaNombre,
            int capacidadDiaria,
            LocalDate weekStartDate
    ) {
        PropuestaMpsCalendarRowDTO row = new PropuestaMpsCalendarRowDTO();
        row.setCategoriaId(categoriaId);
        row.setCategoriaNombre(categoriaNombre);
        row.setCapacidadDiaria(capacidadDiaria);
        row.setCapacidadTeoricaSemana(Math.max(capacidadDiaria, 0) * 6);

        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            PropuestaMpsCalendarCellDTO cell = new PropuestaMpsCalendarCellDTO();
            cell.setDayIndex(dayIndex);
            cell.setDate(weekStartDate.plusDays(dayIndex));
            cell.setCapacidadDiaria(capacidadDiaria);
            cell.setEstado(resolveEstado(0, capacidadDiaria));
            row.getDays().add(cell);
        }

        row.setEstadoSemana(resolveEstado(0, row.getCapacidadTeoricaSemana()));
        return row;
    }

    private void scheduleItemIntoCalendar(
            PropuestaMpsSemanalCalendarDTO calendar,
            PropuestaMpsCalendarRowDTO row,
            PropuestaMpsSemanalItemDTO item,
            Integer categoriaId,
            String categoriaNombre,
            int capacidadDiaria
    ) {
        if (!item.isPlanificable()) {
            calendar.getUnscheduled().add(createUnscheduledBlock(item, categoriaId, categoriaNombre, resolveUnscheduledReason(item)));
            return;
        }

        if (item.getLotesPropuestos() <= 0) {
            return;
        }

        if (capacidadDiaria <= 0) {
            calendar.getUnscheduled().add(createUnscheduledBlock(item, categoriaId, categoriaNombre, "Sin capacidad configurada"));
            return;
        }

        if (item.getLoteSize() <= 0) {
            calendar.getUnscheduled().add(createUnscheduledBlock(item, categoriaId, categoriaNombre, "Sin lote size configurado"));
            return;
        }

        if (item.getLoteSize() > capacidadDiaria) {
            calendar.getUnscheduled().add(createUnscheduledBlock(item, categoriaId, categoriaNombre, "Lote mayor que capacidad diaria"));
            return;
        }

        int lotesRestantes = item.getLotesPropuestos();
        for (PropuestaMpsCalendarCellDTO cell : row.getDays()) {
            int capacidadRestanteDia = Math.max(cell.getCapacidadDiaria() - (int) Math.round(cell.getTotalAsignado()), 0);
            int maxLotesEnDia = item.getLoteSize() > 0 ? capacidadRestanteDia / item.getLoteSize() : 0;
            int lotesAsignados = Math.min(lotesRestantes, maxLotesEnDia);
            if (lotesAsignados <= 0) {
                continue;
            }

            PropuestaMpsCalendarBlockDTO block = new PropuestaMpsCalendarBlockDTO();
            block.setBlockId(buildBlockId(item.getProductoId(), cell.getDayIndex()));
            block.setProductoId(item.getProductoId());
            block.setProductoNombre(item.getProductoNombre());
            block.setCategoriaId(categoriaId);
            block.setCategoriaNombre(categoriaNombre);
            block.setLoteSize(item.getLoteSize());
            block.setLotesAsignados(lotesAsignados);
            block.setCantidadAsignada(lotesAsignados * item.getLoteSize());
            block.setWarning(item.getWarning());

            mergeBlockIntoCell(cell, block);
            lotesRestantes -= lotesAsignados;
            if (lotesRestantes <= 0) {
                break;
            }
        }

        if (lotesRestantes > 0) {
            calendar.getUnscheduled().add(createUnscheduledBlock(
                    item,
                    categoriaId,
                    categoriaNombre,
                    "Sin capacidad restante en la semana",
                    lotesRestantes
            ));
        }
    }

    private void mergeBlockIntoCell(PropuestaMpsCalendarCellDTO cell, PropuestaMpsCalendarBlockDTO incomingBlock) {
        PropuestaMpsCalendarBlockDTO existing = cell.getBlocks().stream()
                .filter(block -> block.getProductoId().equals(incomingBlock.getProductoId()))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            cell.getBlocks().add(incomingBlock);
        } else {
            existing.setLotesAsignados(existing.getLotesAsignados() + incomingBlock.getLotesAsignados());
            existing.setCantidadAsignada(existing.getCantidadAsignada() + incomingBlock.getCantidadAsignada());
            existing.setWarning(appendWarning(existing.getWarning(), incomingBlock.getWarning()));
        }

        double totalAsignado = cell.getBlocks().stream()
                .mapToDouble(PropuestaMpsCalendarBlockDTO::getCantidadAsignada)
                .sum();
        cell.setTotalAsignado(totalAsignado);
        cell.setEstado(resolveEstado(totalAsignado, cell.getCapacidadDiaria()));
    }

    private PropuestaMpsUnscheduledBlockDTO createUnscheduledBlock(
            PropuestaMpsSemanalItemDTO item,
            Integer categoriaId,
            String categoriaNombre,
            String reason
    ) {
        return createUnscheduledBlock(item, categoriaId, categoriaNombre, reason, item.getLotesPropuestos());
    }

    private PropuestaMpsUnscheduledBlockDTO createUnscheduledBlock(
            PropuestaMpsSemanalItemDTO item,
            Integer categoriaId,
            String categoriaNombre,
            String reason,
            int lotesAsignados
    ) {
        PropuestaMpsUnscheduledBlockDTO block = new PropuestaMpsUnscheduledBlockDTO();
        block.setBlockId(buildBlockId(item.getProductoId(), "unscheduled-" + reason.replace(" ", "-").toLowerCase()));
        block.setProductoId(item.getProductoId());
        block.setProductoNombre(item.getProductoNombre());
        block.setCategoriaId(categoriaId);
        block.setCategoriaNombre(categoriaNombre);
        block.setLoteSize(item.getLoteSize());
        block.setLotesAsignados(Math.max(lotesAsignados, 0));
        block.setCantidadAsignada(Math.max(lotesAsignados, 0) * item.getLoteSize());
        block.setWarning(item.getWarning());
        block.setReason(reason);
        return block;
    }

    private void recalculateRowState(PropuestaMpsCalendarRowDTO row) {
        double totalSemana = row.getDays().stream()
                .mapToDouble(PropuestaMpsCalendarCellDTO::getTotalAsignado)
                .sum();
        row.setTotalAsignadoSemana(totalSemana);
        row.setEstadoSemana(resolveEstado(totalSemana, row.getCapacidadTeoricaSemana()));
        row.getDays().forEach(cell -> cell.setEstado(resolveEstado(cell.getTotalAsignado(), cell.getCapacidadDiaria())));
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

    private String resolveUnscheduledReason(PropuestaMpsSemanalItemDTO item) {
        if (item.getWarning() != null && item.getWarning().contains("lote size")) {
            return "Sin lote size configurado";
        }
        if (!item.isPlanificable()) {
            return "No planificable";
        }
        return "Sin capacidad restante en la semana";
    }

    private String buildBlockId(String productoId, Object suffix) {
        return productoId + "__" + suffix;
    }

    private List<PropuestaMpsSemanalItemRequestDTO> consolidateItems(List<PropuestaMpsSemanalItemRequestDTO> inputItems) {
        if (inputItems == null || inputItems.isEmpty()) {
            return List.of();
        }

        Map<String, PropuestaMpsSemanalItemRequestDTO> grouped = new LinkedHashMap<>();
        for (PropuestaMpsSemanalItemRequestDTO item : inputItems) {
            if (item == null || item.getProductoId() == null || item.getProductoId().isBlank()) {
                continue;
            }
            if (item.getNecesidadManual() <= 0) {
                continue;
            }

            String productoId = item.getProductoId().trim();
            PropuestaMpsSemanalItemRequestDTO existing = grouped.get(productoId);
            if (existing == null) {
                PropuestaMpsSemanalItemRequestDTO copy = new PropuestaMpsSemanalItemRequestDTO();
                copy.setProductoId(productoId);
                copy.setNecesidadManual(item.getNecesidadManual());
                copy.setPorcentajeParticipacion(item.getPorcentajeParticipacion());
                copy.setCantidadVendida(item.getCantidadVendida());
                copy.setValorTotal(item.getValorTotal());
                grouped.put(copy.getProductoId(), copy);
            } else {
                existing.setNecesidadManual(existing.getNecesidadManual() + item.getNecesidadManual());
                existing.setPorcentajeParticipacion(existing.getPorcentajeParticipacion() + item.getPorcentajeParticipacion());
                existing.setCantidadVendida(existing.getCantidadVendida() + item.getCantidadVendida());
                existing.setValorTotal(existing.getValorTotal() + item.getValorTotal());
            }
        }

        return new ArrayList<>(grouped.values());
    }

    private PropuestaMpsSemanalItemDTO buildItemProposal(
            PropuestaMpsSemanalItemRequestDTO inputItem,
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            Terminado terminado
    ) {
        PropuestaMpsSemanalItemDTO item = new PropuestaMpsSemanalItemDTO();
        item.setProductoId(inputItem.getProductoId());
        item.setNecesidadManual(inputItem.getNecesidadManual());
        item.setPorcentajeParticipacion(inputItem.getPorcentajeParticipacion());
        item.setCantidadVendida(inputItem.getCantidadVendida());
        item.setValorTotal(inputItem.getValorTotal());

        if (terminado == null) {
            item.setProductoNombre("Producto no encontrado");
            item.setPlanificable(false);
            item.setWarning("Producto no encontrado o no es un terminado.");
            return item;
        }

        item.setProductoNombre(terminado.getNombre());
        item.setFechaLanzamientoSugerida(weekStartDate);

        Categoria categoria = terminado.getCategoria();
        int loteSize = categoria != null ? categoria.getLoteSize() : 0;
        int tiempoDiasFabricacion = categoria != null ? categoria.getTiempoDiasFabricacion() : 0;
        item.setCategoriaNombre(categoria != null ? categoria.getCategoriaNombre() : null);
        item.setLoteSize(loteSize);
        item.setTiempoDiasFabricacion(tiempoDiasFabricacion);

        double stockActual = normalizeStock(transaccionAlmacenRepo.findTotalCantidadByProductoId(terminado.getProductoId()));
        double necesidadNeta = Math.max(inputItem.getNecesidadManual() - stockActual, 0);
        LocalDate fechaFinalPlanificadaSugerida = weekStartDate.plusDays(Math.max(tiempoDiasFabricacion, 0));
        boolean desbordaSemana = fechaFinalPlanificadaSugerida.isAfter(weekEndDate);

        item.setStockActual(stockActual);
        item.setNecesidadNeta(necesidadNeta);
        item.setFechaFinalPlanificadaSugerida(fechaFinalPlanificadaSugerida);
        item.setDesbordaSemana(desbordaSemana);

        if (loteSize <= 0) {
            item.setPlanificable(false);
            item.setWarning(appendWarning(item.getWarning(), "La categoria no tiene lote size configurado."));
            if (desbordaSemana) {
                item.setWarning(appendWarning(item.getWarning(), "La fecha final sugerida desborda la semana lunes-sabado."));
            }
            return item;
        }

        item.setPlanificable(true);

        if (necesidadNeta == 0) {
            if (desbordaSemana) {
                item.setWarning(appendWarning(item.getWarning(), "La fecha final sugerida desborda la semana lunes-sabado."));
            }
            return item;
        }

        int lotesPropuestos = (int) Math.round(necesidadNeta / loteSize);
        if (lotesPropuestos < 1) {
            lotesPropuestos = 1;
        }

        double cantidadPropuesta = lotesPropuestos * loteSize;
        item.setLotesPropuestos(lotesPropuestos);
        item.setCantidadPropuesta(cantidadPropuesta);
        item.setDeltaVsNecesidad(cantidadPropuesta - necesidadNeta);

        if (desbordaSemana) {
            item.setWarning(appendWarning(item.getWarning(), "La fecha final sugerida desborda la semana lunes-sabado."));
        }

        return item;
    }

    private double normalizeStock(Double stock) {
        return stock != null ? stock : 0.0;
    }

    private String appendWarning(String currentWarning, String newWarning) {
        if (currentWarning == null || currentWarning.isBlank()) {
            return newWarning;
        }
        return currentWarning + " | " + newWarning;
    }
}
