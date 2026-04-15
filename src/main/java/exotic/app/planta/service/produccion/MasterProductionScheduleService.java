package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalItemDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalItemRequestDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalRequestDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalResponseDTO;
import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalSummaryDTO;
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
        return response;
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
