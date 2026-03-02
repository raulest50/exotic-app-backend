package exotic.app.planta.resource.inventarios;

import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.ItemDispensadoAveriaDTO;
import exotic.app.planta.model.inventarios.dto.ReporteAveriaDTO;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO;
import exotic.app.planta.service.inventarios.AveriasService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/averias")
@RequiredArgsConstructor
public class AveriasResource {

    private final AveriasService averiasService;

    @GetMapping("/search_orden_by_lote")
    public ResponseEntity<Page<OrdenProduccionDTO>> searchOrdenesProduccionByLote(
            @RequestParam String loteAsignado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fechaCreacion").descending());
        Page<OrdenProduccionDTO> resultados = averiasService.searchOrdenesProduccionByLoteAsignado(loteAsignado, pageable);
        return ResponseEntity.ok(resultados);
    }

    @GetMapping("/orden/{ordenProduccionId}/items-dispensados")
    public ResponseEntity<List<ItemDispensadoAveriaDTO>> getItemsDispensadosParaAveria(
            @PathVariable int ordenProduccionId
    ) {
        List<ItemDispensadoAveriaDTO> items = averiasService.getItemsDispensadosParaAveria(ordenProduccionId);
        return ResponseEntity.ok(items);
    }

    @PostMapping("/registrar")
    public ResponseEntity<TransaccionAlmacen> registrarAveria(@RequestBody ReporteAveriaDTO reporteAveriaDTO) {
        TransaccionAlmacen transaccion = averiasService.crearReporteAveria(reporteAveriaDTO);
        return ResponseEntity
                .created(URI.create("/movimientos/transaccion/" + transaccion.getTransaccionId()))
                .body(transaccion);
    }
}
