package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.FuenteDemandaCobertura;
import exotic.app.planta.service.bi.InformeGlobalFechaResolver;
import exotic.app.planta.service.bi.inventario.AjustesInventarioDetalleService;
import exotic.app.planta.service.bi.inventario.AlertasInventarioDetalleService;
import exotic.app.planta.service.bi.inventario.BusquedaStockMaterialService;
import exotic.app.planta.service.bi.inventario.CoberturaMaterialesService;
import exotic.app.planta.service.bi.inventario.InformeInventarioService;
import exotic.app.planta.service.bi.inventario.InformeInventarioDetalleService;
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
    private final InformeInventarioDetalleService detailService;
    private final BusquedaStockMaterialService searchService;
    private final CoberturaMaterialesService coverageService;
    private final AjustesInventarioDetalleService adjustmentDetailService;
    private final AlertasInventarioDetalleService alertDetailService;

    @GetMapping
    public ResponseEntity<?> reporte(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        try {
            var rango = InformeGlobalFechaResolver.resolve(fecha, fechaDesde, fechaHasta);
            return ResponseEntity.ok(reportService.getReport(
                    rango.fechaDesde(),
                    rango.fechaHasta()));
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

    @GetMapping("/ocm-pendientes")
    public ResponseEntity<?> ocmPendientes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            return ResponseEntity.ok(detailService.getPendingPurchaseOrders(page, size));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @GetMapping("/op-material-directo")
    public ResponseEntity<?> materialDirectoOp(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            return ResponseEntity.ok(detailService.getOpenProductionOrderMaterial(page, size));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @GetMapping("/cobertura")
    public ResponseEntity<?> cobertura(
            @RequestParam(defaultValue = "90") int ventanaDias,
            @RequestParam(defaultValue = "SOLO_DISPENSACIONES")
            FuenteDemandaCobertura fuenteDemanda,
            @RequestParam(defaultValue = "TODOS") String horizonte,
            @RequestParam(defaultValue = "TODOS") String grupo,
            @RequestParam(required = false) String unidad,
            @RequestParam(defaultValue = "AGOTAMIENTO") String orden,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            return ResponseEntity.ok(coverageService.calculate(
                    ventanaDias,
                    fuenteDemanda,
                    horizonte,
                    grupo,
                    unidad,
                    orden,
                    buscar,
                    page,
                    size));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @GetMapping("/ajustes-materiales")
    public ResponseEntity<?> ajustesMateriales(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String grupo,
            @RequestParam(defaultValue = "TODOS") String tipo,
            @RequestParam(defaultValue = "IMPACTO") String orden,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        try {
            var rango = InformeGlobalFechaResolver.resolve(fecha, fechaDesde, fechaHasta);
            return ResponseEntity.ok(adjustmentDetailService.getMaterials(
                    rango.fechaDesde(),
                    rango.fechaHasta(),
                    grupo,
                    tipo,
                    orden,
                    buscar,
                    page,
                    size));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @GetMapping("/alertas-materiales")
    public ResponseEntity<?> alertasMateriales(
            @RequestParam(defaultValue = "TODAS") String tipo,
            @RequestParam(defaultValue = "TODOS") String grupo,
            @RequestParam(required = false) String unidad,
            @RequestParam(defaultValue = "PRIORIDAD") String orden,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            return ResponseEntity.ok(alertDetailService.getAlerts(
                    tipo,
                    grupo,
                    unidad,
                    orden,
                    buscar,
                    page,
                    size));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    private static ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
