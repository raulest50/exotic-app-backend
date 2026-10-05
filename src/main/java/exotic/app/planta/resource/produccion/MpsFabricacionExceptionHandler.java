package exotic.app.planta.resource.produccion;

import exotic.app.planta.dto.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

@RestControllerAdvice(assignableTypes = MpsFabricacionResource.class)
public class MpsFabricacionExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorResponse("MPS OF", e.getReason()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(new ErrorResponse("Acceso denegado", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Solicitud invalida", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).findFirst().orElse("Revise los datos de la propuesta.");
        return ResponseEntity.badRequest().body(new ErrorResponse("Solicitud invalida", message));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> missing(NoSuchElementException e) {
        return ResponseEntity.status(404).body(new ErrorResponse("No encontrado", e.getMessage()));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> conflict(RuntimeException e) {
        return ResponseEntity.status(409).body(new ErrorResponse("No fue posible guardar",
                "La semana o sus referencias cambiaron. Recargue el MPS OF antes de volver a guardar."));
    }
}
