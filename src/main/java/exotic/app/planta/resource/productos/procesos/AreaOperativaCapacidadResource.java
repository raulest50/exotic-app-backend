package exotic.app.planta.resource.productos.procesos;

import exotic.app.planta.dto.CapacidadAreaOperativaDTO;
import exotic.app.planta.dto.CapacidadAreaOperativaRequestDTO;
import exotic.app.planta.dto.ConversionUnidadAreaOperativaRequestDTO;
import exotic.app.planta.dto.ConversionUnidadAreaOperativaResponseDTO;
import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.dto.UnidadMedidaAreaOperativaDTO;
import exotic.app.planta.dto.UnidadMedidaAreaOperativaRequestDTO;
import exotic.app.planta.service.productos.procesos.AreaOperativaCapacidadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/areas-produccion")
@RequiredArgsConstructor
@Slf4j
public class AreaOperativaCapacidadResource {

    private final AreaOperativaCapacidadService areaOperativaCapacidadService;

    @GetMapping("/{areaId}/unidades")
    public ResponseEntity<?> listarUnidades(@PathVariable Integer areaId) {
        try {
            List<UnidadMedidaAreaOperativaDTO> unidades = areaOperativaCapacidadService.listarUnidades(areaId);
            return ResponseEntity.ok(unidades);
        } catch (IllegalArgumentException e) {
            return badRequest("Error al listar unidades", e);
        }
    }

    @PostMapping("/{areaId}/unidades")
    public ResponseEntity<?> crearUnidad(
            @PathVariable Integer areaId,
            @Valid @RequestBody UnidadMedidaAreaOperativaRequestDTO request
    ) {
        try {
            return ResponseEntity.ok(areaOperativaCapacidadService.crearUnidad(areaId, request));
        } catch (IllegalArgumentException e) {
            return badRequest("Error al crear unidad", e);
        }
    }

    @PutMapping("/{areaId}/unidades/{unidadId}")
    public ResponseEntity<?> actualizarUnidad(
            @PathVariable Integer areaId,
            @PathVariable Long unidadId,
            @Valid @RequestBody UnidadMedidaAreaOperativaRequestDTO request
    ) {
        try {
            return ResponseEntity.ok(areaOperativaCapacidadService.actualizarUnidad(areaId, unidadId, request));
        } catch (IllegalArgumentException e) {
            return badRequest("Error al actualizar unidad", e);
        }
    }

    @DeleteMapping("/{areaId}/unidades/{unidadId}")
    public ResponseEntity<?> desactivarUnidad(
            @PathVariable Integer areaId,
            @PathVariable Long unidadId
    ) {
        try {
            areaOperativaCapacidadService.desactivarUnidad(areaId, unidadId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return badRequest("Error al desactivar unidad", e);
        }
    }

    @GetMapping("/{areaId}/capacidades")
    public ResponseEntity<?> listarCapacidades(@PathVariable Integer areaId) {
        try {
            List<CapacidadAreaOperativaDTO> capacidades = areaOperativaCapacidadService.listarCapacidades(areaId);
            return ResponseEntity.ok(capacidades);
        } catch (IllegalArgumentException e) {
            return badRequest("Error al listar capacidades", e);
        }
    }

    @PostMapping("/{areaId}/capacidades")
    public ResponseEntity<?> crearCapacidad(
            @PathVariable Integer areaId,
            @Valid @RequestBody CapacidadAreaOperativaRequestDTO request
    ) {
        try {
            return ResponseEntity.ok(areaOperativaCapacidadService.crearCapacidad(areaId, request));
        } catch (IllegalArgumentException e) {
            return badRequest("Error al crear capacidad", e);
        }
    }

    @PutMapping("/{areaId}/capacidades/{capacidadId}")
    public ResponseEntity<?> actualizarCapacidad(
            @PathVariable Integer areaId,
            @PathVariable Long capacidadId,
            @Valid @RequestBody CapacidadAreaOperativaRequestDTO request
    ) {
        try {
            return ResponseEntity.ok(areaOperativaCapacidadService.actualizarCapacidad(areaId, capacidadId, request));
        } catch (IllegalArgumentException e) {
            return badRequest("Error al actualizar capacidad", e);
        }
    }

    @DeleteMapping("/{areaId}/capacidades/{capacidadId}")
    public ResponseEntity<?> desactivarCapacidad(
            @PathVariable Integer areaId,
            @PathVariable Long capacidadId
    ) {
        try {
            areaOperativaCapacidadService.desactivarCapacidad(areaId, capacidadId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return badRequest("Error al desactivar capacidad", e);
        }
    }

    @PostMapping("/conversion-unidades")
    public ResponseEntity<?> convertir(@Valid @RequestBody ConversionUnidadAreaOperativaRequestDTO request) {
        try {
            ConversionUnidadAreaOperativaResponseDTO response = areaOperativaCapacidadService.convertir(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest("Error al convertir unidades", e);
        }
    }

    private ResponseEntity<ErrorResponse> badRequest(String title, IllegalArgumentException e) {
        log.warn("{}: {}", title, e.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse(title, e.getMessage()));
    }
}
