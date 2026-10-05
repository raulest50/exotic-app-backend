package exotic.app.planta.resource.productos.procesos;

import exotic.app.planta.dto.AreaOperativaResponseDTO;
import exotic.app.planta.dto.AreaProduccionDTO;
import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.dto.SearchAreaOperativaDTO;
import exotic.app.planta.dto.SearchAreaProduccionDTO;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.productos.procesos.AreaProduccionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/areas-produccion")
@RequiredArgsConstructor
@Slf4j
public class AreaProduccionResource {

    private final AreaProduccionService areaProduccionService;
    private final UserRepository userRepository;

    @PostMapping("/crear")
    public ResponseEntity<?> createAreaProduccion(Authentication authentication, @Valid @RequestBody AreaProduccionDTO areaProduccionDTO) {
        requireMpsConfigurationPermission(authentication, areaProduccionDTO, "CREAR_AREA_PRODUCCION");
        log.info("REST request para crear una nueva area de produccion: {}", areaProduccionDTO.getNombre());

        try {
            AreaOperativaResponseDTO result = areaProduccionService.createAreaProduccionFromDTO(areaProduccionDTO);
            return ResponseEntity
                    .created(URI.create("/api/areas-produccion/" + result.getAreaId()))
                    .body(result);
        } catch (IllegalArgumentException e) {
            log.error("Error al crear area de produccion: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(new ErrorResponse("Error al crear area", e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado al crear area de produccion", e);
            return ResponseEntity
                    .internalServerError()
                    .body(new ErrorResponse("Error interno del servidor", "Ocurrio un error inesperado"));
        }
    }

    @PutMapping("/{areaId}")
    public ResponseEntity<?> updateAreaProduccion(
            Authentication authentication,
            @PathVariable Integer areaId,
            @Valid @RequestBody AreaProduccionDTO dto) {

        requireMpsConfigurationPermission(authentication, dto, "CONSULTA_AREAS_OPERATIVAS");

        log.info("REST request para actualizar area de produccion con ID: {}", areaId);

        try {
            AreaOperativaResponseDTO result = areaProduccionService.updateAreaProduccion(areaId, dto);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Error al actualizar area de produccion: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(new ErrorResponse("Error al actualizar area", e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado al actualizar area de produccion", e);
            return ResponseEntity
                    .internalServerError()
                    .body(new ErrorResponse("Error interno del servidor", "Ocurrio un error inesperado"));
        }
    }

    @PostMapping("/search")
    public ResponseEntity<Page<AreaOperativaResponseDTO>> searchAreas(
            @RequestBody SearchAreaOperativaDTO searchDTO,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("REST request para buscar areas operativas - tipo: {}", searchDTO.getSearchType());

        try {
            PageRequest pageable = PageRequest.of(page, size);
            Page<AreaOperativaResponseDTO> result = areaProduccionService.searchAreas(searchDTO, pageable);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error al buscar areas operativas", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void requireMpsConfigurationPermission(Authentication auth, AreaProduccionDTO dto, String tab) {
        if (dto.getVisibilidadMps() == null && dto.getAlcanceMps() == null) return;
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        var user = userRepository.findByUsername(auth.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if ("master".equalsIgnoreCase(user.getUsername()) || "super_master".equalsIgnoreCase(user.getUsername())) return;
        int nivel = UserAccessEvaluator.tabNivel(user, ModuloSistema.SEGUIMIENTO_PRODUCCION, tab)
                .orElse(UserAccessEvaluator.tabNivel(user, ModuloSistema.SEGUIMIENTO_PRODUCCION, "MAIN").orElse(0));
        if (nivel < 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para configurar el MPS de esta area.");
        }
    }

    @PostMapping("/search_by_name")
    public ResponseEntity<List<AreaOperativaResponseDTO>> searchAreasByName(
            @RequestBody SearchAreaProduccionDTO searchDTO,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("REST request para buscar areas de produccion por nombre: {}", searchDTO.getNombre());

        try {
            PageRequest pageable = PageRequest.of(page, size);
            List<AreaOperativaResponseDTO> areas = areaProduccionService.searchAreasByName(searchDTO, pageable);
            return ResponseEntity.ok(areas);
        } catch (Exception e) {
            log.error("Error al buscar areas de produccion", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
