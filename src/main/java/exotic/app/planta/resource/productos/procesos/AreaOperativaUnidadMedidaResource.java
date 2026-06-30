package exotic.app.planta.resource.productos.procesos;

import exotic.app.planta.dto.ConversionUnidadAreaOperativaRequestDTO;
import exotic.app.planta.dto.ConversionUnidadAreaOperativaResponseDTO;
import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.dto.UnidadMedidaAreaOperativaDTO;
import exotic.app.planta.dto.UnidadMedidaAreaOperativaRequestDTO;
import exotic.app.planta.service.productos.procesos.AreaOperativaUnidadMedidaService;
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
public class AreaOperativaUnidadMedidaResource {

    private final AreaOperativaUnidadMedidaService areaOperativaUnidadMedidaService;

    @GetMapping("/{areaId}/unidades")
    public ResponseEntity<?> listarUnidades(@PathVariable Integer areaId) {
        try {
            List<UnidadMedidaAreaOperativaDTO> unidades = areaOperativaUnidadMedidaService.listarUnidades(areaId);
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
            return ResponseEntity.ok(areaOperativaUnidadMedidaService.crearUnidad(areaId, request));
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
            return ResponseEntity.ok(areaOperativaUnidadMedidaService.actualizarUnidad(areaId, unidadId, request));
        } catch (IllegalArgumentException e) {
            return badRequest("Error al actualizar unidad", e);
        }
    }

    @DeleteMapping("/{areaId}/unidades/{unidadId}")
    public ResponseEntity<?> eliminarUnidad(
            @PathVariable Integer areaId,
            @PathVariable Long unidadId
    ) {
        try {
            areaOperativaUnidadMedidaService.eliminarUnidad(areaId, unidadId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return badRequest("Error al eliminar unidad", e);
        }
    }

    @PostMapping("/conversion-unidades")
    public ResponseEntity<?> convertir(@Valid @RequestBody ConversionUnidadAreaOperativaRequestDTO request) {
        try {
            ConversionUnidadAreaOperativaResponseDTO response = areaOperativaUnidadMedidaService.convertir(request);
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
