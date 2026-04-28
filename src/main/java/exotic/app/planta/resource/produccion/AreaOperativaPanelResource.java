package exotic.app.planta.resource.produccion;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.produccion.AreaOperativaPanelDetalleService;
import exotic.app.planta.service.produccion.AreaOperativaPanelDetalleService.AreaOperativaOrdenDetalleDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/area-operativa-panel")
@RequiredArgsConstructor
@Slf4j
public class AreaOperativaPanelResource {

    private final AreaOperativaPanelDetalleService areaOperativaPanelDetalleService;
    private final UserRepository userRepository;

    @GetMapping("/ordenes/{ordenId}/detalle-operativo")
    public ResponseEntity<?> getDetalleOperativoOrden(
            Authentication authentication,
            @PathVariable int ordenId
    ) {
        User user = getCurrentUser(authentication);

        try {
            AreaOperativaOrdenDetalleDTO detalle = areaOperativaPanelDetalleService.getDetalleOperativoOrden(
                    ordenId,
                    user.getId()
            );
            return ResponseEntity.ok(detalle);
        } catch (AccessDeniedException e) {
            log.warn("Acceso denegado al detalle operativo de orden {} para user {}: {}", ordenId, user.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Acceso denegado", e.getMessage()));
        } catch (NoSuchElementException e) {
            log.warn("Detalle operativo no encontrado para orden {}: {}", ordenId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Detalle no encontrado", e.getMessage()));
        }
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }
}
