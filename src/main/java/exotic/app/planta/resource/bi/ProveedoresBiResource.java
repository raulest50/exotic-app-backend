package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.LeadTimeProveedorKpiDTO;
import exotic.app.planta.model.bi.dto.ProveedorMaterialLeadTimeMetricDTO;
import exotic.app.planta.model.bi.dto.LeadTimeProveedorMaterialPageRowDTO;
import exotic.app.planta.model.bi.dto.PuntoReordenEstimadoDTO;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.compras.proveedor.metricas.EstadoLeadTimeProveedorKPI;
import exotic.app.planta.model.compras.proveedor.metricas.LeadTimeProveedorKPI;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.compras.proveedor.metricas.LeadTimeProveedorKPIRepo;
import exotic.app.planta.service.bi.ProveedoresBiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/bi/proveedores")
@RequiredArgsConstructor
@Slf4j
public class ProveedoresBiResource {

    private final ProveedoresBiService proveedoresBiService;
    private final ProveedorRepo proveedorRepo;
    private final LeadTimeProveedorKPIRepo leadTimeProveedorKPIRepo;

    @GetMapping("/lead-time")
    public ResponseEntity<?> calcularLeadTimeProveedorMaterial(
            @RequestParam String proveedorId,
            @RequestParam String materialId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCorte,
            @RequestParam(defaultValue = "365") int ventanaDias
    ) {
        try {
            ProveedorMaterialLeadTimeMetricDTO result = proveedoresBiService.calcularLeadTimeProveedorMaterial(
                    proveedorId,
                    materialId,
                    fechaCorte,
                    ventanaDias
            );
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            log.warn("Entidad no encontrada en lead time BI: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Solicitud invalida para lead time BI: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al calcular lead time proveedor-material", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{proveedorId}/lead-time-kpi")
    public ResponseEntity<?> obtenerLeadTimeProveedorKpi(@PathVariable String proveedorId) {
        try {
            Proveedor proveedor = proveedorRepo.findById(proveedorId)
                    .orElseThrow(() -> new NoSuchElementException("Proveedor no encontrado: " + proveedorId));
            LeadTimeProveedorKPI kpi = leadTimeProveedorKPIRepo.findByProveedor_Pk(proveedor.getPk()).orElse(null);
            return ResponseEntity.ok(toLeadTimeProveedorKpiDTO(proveedor, kpi));
        } catch (NoSuchElementException e) {
            log.warn("Proveedor no encontrado para KPI lead time: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error al consultar KPI lead time proveedor", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/materiales/{materialId}/lead-times")
    public ResponseEntity<?> listarLeadTimesPorMaterial(
            @PathVariable String materialId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCorte,
            @RequestParam(defaultValue = "365") int ventanaDias,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        try {
            Page<LeadTimeProveedorMaterialPageRowDTO> result = proveedoresBiService.listarLeadTimesPorMaterial(
                    materialId,
                    fechaCorte,
                    ventanaDias,
                    page,
                    size,
                    direction
            );
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            log.warn("Material no encontrado para ranking BI: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Solicitud invalida para ranking BI: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al listar lead times por material", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/materiales/{materialId}/reorder-point-estimate")
    public ResponseEntity<?> estimarPuntoReorden(
            @PathVariable String materialId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCorte,
            @RequestParam(defaultValue = "365") int ventanaDias
    ) {
        try {
            PuntoReordenEstimadoDTO result = proveedoresBiService.estimarPuntoReorden(
                    materialId,
                    fechaCorte,
                    ventanaDias
            );
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            log.warn("Material no encontrado para ROP BI: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Solicitud invalida para ROP BI: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al estimar punto de reorden", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private LeadTimeProveedorKpiDTO toLeadTimeProveedorKpiDTO(Proveedor proveedor, LeadTimeProveedorKPI kpi) {
        if (kpi == null) {
            return new LeadTimeProveedorKpiDTO(
                    proveedor.getId(),
                    proveedor.getNombre(),
                    EstadoLeadTimeProveedorKPI.SIN_INFORMACION,
                    null,
                    0,
                    0,
                    null,
                    null,
                    null,
                    "KPI no generado todavía.",
                    null,
                    null
            );
        }

        EstadoLeadTimeProveedorKPI estado = kpi.getEstado() != null
                ? kpi.getEstado()
                : EstadoLeadTimeProveedorKPI.VIGENTE;
        return new LeadTimeProveedorKpiDTO(
                proveedor.getId(),
                proveedor.getNombre(),
                estado,
                kpi.getLeadTimeMedianoDias(),
                kpi.getObservaciones(),
                kpi.getOrdenesConsideradas(),
                kpi.getFechaCorte(),
                kpi.getVentanaDias(),
                kpi.getCalculadoEn(),
                kpi.getMotivoEstado(),
                kpi.getUltimaEvaluacionEn(),
                kpi.getUltimaFechaCorteEvaluada()
        );
    }
}
