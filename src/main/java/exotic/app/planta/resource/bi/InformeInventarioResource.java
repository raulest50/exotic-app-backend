package exotic.app.planta.resource.bi;

import exotic.app.planta.service.bi.InformeGlobalFechaResolver;
import exotic.app.planta.service.bi.inventario.BusquedaStockMaterialService;
import exotic.app.planta.service.bi.inventario.CoberturaMaterialesService;
import exotic.app.planta.service.bi.inventario.InformeInventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/bi/informes-globales/almacen")
@RequiredArgsConstructor
public class InformeInventarioResource {
    private final InformeInventarioService reportService;
    private final BusquedaStockMaterialService searchService;
    private final CoberturaMaterialesService coverageService;

    @GetMapping
    public ResponseEntity<?> reporte(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "30") int ventanaTendenciaDias) {
        try {
            var rango = InformeGlobalFechaResolver.resolve(fecha, fechaDesde, fechaHasta);
            return ResponseEntity.ok(reportService.getReport(
                    rango.fechaDesde(),
                    rango.fechaHasta(),
                    ventanaTendenciaDias));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @GetMapping("/stock-materiales")
    public ResponseEntity<?> buscar(@RequestParam String buscar) {
        try {
            return ResponseEntity.ok(searchService.search(buscar));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @GetMapping("/cobertura")
    public ResponseEntity<?> cobertura(@RequestParam(defaultValue = "90") int ventanaDias) {
        try {
            return ResponseEntity.ok(coverageService.calculate(ventanaDias));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    private static ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
