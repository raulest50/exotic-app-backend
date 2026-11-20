package exotic.app.planta.resource.compras;

import exotic.app.planta.model.compras.dto.PuntosReordenDTOs.CalculoResponse;
import exotic.app.planta.model.compras.dto.PuntosReordenDTOs.Parametros;
import exotic.app.planta.model.compras.dto.PuntosReordenDTOs.PreparacionResponse;
import exotic.app.planta.model.compras.dto.PuntosReordenDTOs.PrepararRequest;
import exotic.app.planta.model.compras.dto.PuntosReordenDTOs.ResultadoResponse;
import exotic.app.planta.model.compras.dto.PuntosReordenDTOs.TokenRequest;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.compras.PuntosReordenService;
import exotic.app.planta.service.compras.PuntosReordenWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/** Autoriza cálculo, preparación y confirmación sin exponer detalles internos de fallos. */
@RestController
@RequestMapping("/api/compras/puntos-reorden")
@RequiredArgsConstructor
@Slf4j
public class PuntosReordenResource {

    private static final String TAB_ID = "PUNTOS_REORDEN";
    private static final String INTERNAL_ERROR_MESSAGE = "No fue posible procesar los puntos de reorden.";

    private final PuntosReordenService service;
    private final PuntosReordenWorkflowService workflow;
    private final ModuleTabAccessGuard accessGuard;

    @PostMapping("/calcular")
    public CalculoResponse calcular(@Valid @RequestBody Parametros parametros, Authentication authentication) {
        requireAccess(authentication, 1);
        return service.calcular(parametros);
    }

    @PostMapping("/preparar")
    public PreparacionResponse preparar(@Valid @RequestBody PrepararRequest request, Authentication authentication) {
        User user = requireAccess(authentication, 2);
        return workflow.preparar(request, user);
    }

    @PostMapping("/preparaciones/{id}/continuar")
    public PreparacionResponse continuar(
            @PathVariable UUID id,
            @Valid @RequestBody TokenRequest request,
            Authentication authentication
    ) {
        User user = requireAccess(authentication, 2);
        return workflow.continuar(id, request.token(), user);
    }

    @PostMapping("/preparaciones/{id}/confirmar")
    public ResultadoResponse confirmar(
            @PathVariable UUID id,
            @Valid @RequestBody TokenRequest request,
            Authentication authentication
    ) {
        User user = requireAccess(authentication, 2);
        return workflow.confirmar(id, request.token(), user);
    }

    @DeleteMapping("/preparaciones/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id, Authentication authentication) {
        User user = requireAccess(authentication, 2);
        workflow.cancelar(id, user);
        return ResponseEntity.noContent().build();
    }

    private User requireAccess(Authentication authentication, int nivel) {
        return accessGuard.requireTabAccess(
                authentication, ModuloSistema.COMPRAS, TAB_ID, nivel,
                "No tiene permiso para esta operación de puntos de reorden."
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException exception) {
        if (!exception.getStatusCode().is4xxClientError()) {
            return handleUnexpected(exception);
        }
        String message = exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "message", message == null || message.isBlank() ? "No fue posible procesar la solicitud." : message
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Los parámetros de la solicitud no son válidos."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("Los parámetros de la solicitud no son válidos.");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "El formato de la solicitud no es válido."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception exception) {
        log.error("PuntosReorden: fallo interno al procesar una solicitud.", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", INTERNAL_ERROR_MESSAGE));
    }
}
