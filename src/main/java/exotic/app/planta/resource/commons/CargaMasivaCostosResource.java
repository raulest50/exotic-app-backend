package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.CargaCostosDTOs;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.commons.CargaMasivaCostosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/carga-masiva-costos/preparaciones")
@RequiredArgsConstructor
public class CargaMasivaCostosResource {
    private static final String TAB_ID = "CARGAS_MASIVAS";

    private final CargaMasivaCostosService service;
    private final UserRepository userRepository;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CargaCostosDTOs.PreparacionResponse> preparar(
            @RequestParam("file") MultipartFile file,
            @RequestParam("motivo") String motivo,
            Authentication authentication
    ) {
        CargaCostosDTOs.PreparacionResponse response = service.preparar(
                file, motivo, requireAccess(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{loteId}/items")
    public ResponseEntity<CargaCostosDTOs.ItemsPageResponse> listarItems(
            @PathVariable UUID loteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service.listarItems(
                loteId, requireAccess(authentication), page, size));
    }

    @PostMapping("/{loteId}/token")
    public ResponseEntity<CargaCostosDTOs.TokenResponse> generarToken(
            @PathVariable UUID loteId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service.generarToken(loteId, requireAccess(authentication)));
    }

    @PostMapping("/{loteId}/confirmacion")
    public ResponseEntity<CargaCostosDTOs.ConfirmacionResponse> confirmar(
            @PathVariable UUID loteId,
            @Valid @RequestBody CargaCostosDTOs.ConfirmacionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service.confirmar(
                loteId, request.token(), requireAccess(authentication)));
    }

    @DeleteMapping("/{loteId}")
    public ResponseEntity<Void> cancelar(
            @PathVariable UUID loteId,
            Authentication authentication
    ) {
        service.cancelar(loteId, requireAccess(authentication));
        return ResponseEntity.noContent().build();
    }

    private User requireAccess(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        boolean allowed = UserAccessEvaluator.tabNivel(user, ModuloSistema.OPERACIONES_CRITICAS_BD, TAB_ID)
                .orElse(0) >= 1;
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para cargas masivas");
        }
        return user;
    }
}
