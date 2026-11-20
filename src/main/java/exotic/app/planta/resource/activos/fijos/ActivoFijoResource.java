package exotic.app.planta.resource.activos.fijos;

import exotic.app.planta.model.activos.fijos.dto.DTO_SearchActivoFijo;
import exotic.app.planta.model.activos.fijos.ActivoFijo;
import exotic.app.planta.model.activos.fijos.dto.IncorporacionActivoDto;
import exotic.app.planta.model.activos.fijos.compras.OrdenCompraActivo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import exotic.app.planta.service.activos.fijos.ActivoFijoService;

import java.util.List;

/**
 * Controlador REST para la gestión de activos fijos.
 * Proporciona endpoints para administrar incorporaciones, depreciaciones
 * y otros aspectos relacionados con los activos fijos.
 * 
 * Nota: La gestión de órdenes de compra de activos fijos ha sido movida a {@link OCAFResource}
 */
@RestController
@RequestMapping("/api/activos-fijos")
@RequiredArgsConstructor
@Slf4j
public class ActivoFijoResource {

    private final ActivoFijoService activoFijoService;

    public record CodigoLoteRequest(@jakarta.validation.constraints.Size(max = 255) String codigoLoteFabricacion) {}
    public record CodigoLoteResponse(String id, String codigoLoteFabricacion) {}

    @PutMapping("/{id}/codigo-lote-fabricacion")
    public CodigoLoteResponse actualizarCodigoLoteFabricacion(
            @PathVariable String id, @jakarta.validation.Valid @RequestBody CodigoLoteRequest request) {
        ActivoFijo activo = activoFijoService.actualizarCodigoLoteFabricacion(id, request.codigoLoteFabricacion());
        return new CodigoLoteResponse(activo.getId(), activo.getCodigoLoteFabricacion());
    }

    /**
     * Obtiene todos los activos fijos paginados.
     * 
     * @param pageable Configuración de paginación
     * @return Página de activos fijos
     */
    @GetMapping
    public ResponseEntity<Page<ActivoFijo>> findAll(Pageable pageable) {
        return ResponseEntity.ok(activoFijoService.findAll(pageable));
    }

    /**
     * Obtiene un activo fijo por su ID.
     * 
     * @param id ID del activo fijo
     * @return Activo fijo si existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<ActivoFijo> findById(@PathVariable String id) {
        return activoFijoService.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activo fijo no encontrado"));
    }

    /**
     * Crea un nuevo activo fijo.
     * 
     * @param activoFijo Activo fijo a crear
     * @return Activo fijo creado
     */
    @PostMapping
    public ResponseEntity<ActivoFijo> create(@RequestBody ActivoFijo activoFijo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activoFijoService.save(activoFijo));
    }

    /**
     * Endpoint para procesar una incorporación de activos fijos con soporte de MultipartFile.
     */
    @PostMapping(value = "/incorporar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> incorporarActivos(
            @RequestPart("incorporacionDto") IncorporacionActivoDto dto,
            @RequestPart(value = "ordenCompraActivo", required = false) OrdenCompraActivo ordenCompraActivo,
            @RequestPart(value = "documentoSoporte", required = false) MultipartFile documentoSoporte) throws Exception {
        var header = activoFijoService.procesarIncorporacion(dto, ordenCompraActivo, documentoSoporte);
        return ResponseEntity.ok(java.util.Map.of("incorporacionId", header.getIncorporacionId()));
    }

    /**
     * Actualiza un activo fijo existente.
     * 
     * @param id ID del activo fijo a actualizar
     * @param activoFijo Datos actualizados del activo fijo
     * @return Activo fijo actualizado
     */
    @PutMapping("/{id}")
    public ResponseEntity<ActivoFijo> update(@PathVariable String id, @RequestBody ActivoFijo activoFijo) {
        if (!activoFijoService.findById(id).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Activo fijo no encontrado");
        }
        activoFijo.setId(id);
        return ResponseEntity.ok(activoFijoService.save(activoFijo));
    }

    /**
     * Elimina un activo fijo.
     * 
     * @param id ID del activo fijo a eliminar
     * @return Respuesta sin contenido
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!activoFijoService.findById(id).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Activo fijo no encontrado");
        }
        activoFijoService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Busca activos fijos por ubicación.
     * 
     * @param ubicacion Ubicación a buscar
     * @return Lista de activos fijos en la ubicación especificada
     */
    @GetMapping("/por-ubicacion/{ubicacion}")
    public ResponseEntity<List<ActivoFijo>> findByUbicacion(@PathVariable String ubicacion) {
        return ResponseEntity.ok(activoFijoService.findByUbicacion(ubicacion));
    }

    /**
     * Busca activos fijos por responsable.
     * 
     * @param responsableId ID del responsable
     * @return Lista de activos fijos asignados al responsable
     */
    @GetMapping("/por-responsable/{responsableId}")
    public ResponseEntity<List<ActivoFijo>> findByResponsable(@PathVariable long responsableId) {
        return ResponseEntity.ok(activoFijoService.findByResponsable(responsableId));
    }

    /**
     * Busca activos fijos por tipo.
     * 
     * @param tipoActivo Tipo de activo
     * @return Lista de activos fijos del tipo especificado
     */
    @GetMapping("/por-tipo/{tipoActivo}")
    public ResponseEntity<List<ActivoFijo>> findByTipoActivo(@PathVariable ActivoFijo.TipoActivo tipoActivo) {
        return ResponseEntity.ok(activoFijoService.findByTipoActivo(tipoActivo));
    }

    /**
     * Asigna un responsable a un activo fijo.
     * 
     * @param activoId ID del activo fijo
     * @param responsableId ID del responsable
     * @return Activo fijo actualizado
     */
    @PutMapping("/{activoId}/asignar-responsable/{responsableId}")
    public ResponseEntity<ActivoFijo> asignarResponsable(
            @PathVariable String activoId,
            @PathVariable long responsableId) {
        return activoFijoService.asignarResponsable(activoId, responsableId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Activo fijo o responsable no encontrado"));
    }

    /**
     * Actualiza la ubicación de un activo fijo.
     * 
     * @param activoId ID del activo fijo
     * @param ubicacion Nueva ubicación
     * @return Activo fijo actualizado
     */
    @PutMapping("/{activoId}/actualizar-ubicacion")
    public ResponseEntity<ActivoFijo> actualizarUbicacion(
            @PathVariable String activoId,
            @RequestParam String ubicacion) {
        return activoFijoService.actualizarUbicacion(activoId, ubicacion)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Activo fijo no encontrado"));
    }

    /**
     * Busca activos fijos según los criterios especificados.
     * 
     * @param searchDTO DTO con los criterios de búsqueda
     * @param pageable Configuración de paginación
     * @return Página de activos fijos que cumplen con los criterios
     */
    @PostMapping("/search")
    public ResponseEntity<Page<ActivoFijo>> search(
            @RequestBody DTO_SearchActivoFijo searchDTO,
            Pageable pageable) {
        log.info("Realizando búsqueda de activos fijos con criterios: {}", searchDTO);
        return ResponseEntity.ok(activoFijoService.search(searchDTO, pageable));
    }
}
