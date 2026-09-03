package exotic.app.planta.resource.controles;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.controles.dto.BloqueoControlDTO;
import exotic.app.planta.resource.calidad.CalidadControlUnificadoResource;
import exotic.app.planta.resource.produccion.ProcesoControlResource;
import exotic.app.planta.service.controles.ControlBloqueoException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@RestControllerAdvice(assignableTypes = {
        ProcesoControlResource.class,
        CalidadControlUnificadoResource.class,
        ControlCatalogResource.class
})
public class ControlApiExceptionHandler {
    public record ApiError(String title, String message, LocalDateTime timestamp,
                           List<BloqueoControlDTO> bloqueos) {}

    @ExceptionHandler(ControlBloqueoException.class)
    public ResponseEntity<ApiError> bloqueo(ControlBloqueoException ex) {
        return respuesta(HttpStatus.CONFLICT, "Controles requeridos pendientes", ex.getMessage(), ex.getBloqueos());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> solicitudInvalida(Exception ex) {
        String mensaje = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("El payload no es valido.")
                : ex.getMessage();
        return respuesta(HttpStatus.BAD_REQUEST, "Solicitud invalida", mensaje, List.of());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> noEncontrado(NoSuchElementException ex) {
        return respuesta(HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage(), List.of());
    }

    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class,
            ObjectOptimisticLockingFailureException.class, CannotAcquireLockException.class})
    public ResponseEntity<ApiError> conflicto(Exception ex) {
        String mensaje = ex instanceof DataIntegrityViolationException
                ? "La operacion entra en conflicto con el estado actual o con otra solicitud concurrente."
                : ex.getMessage();
        return respuesta(HttpStatus.CONFLICT, "Conflicto de estado", mensaje, List.of());
    }

    private ResponseEntity<ApiError> respuesta(
            HttpStatus status, String title, String message, List<BloqueoControlDTO> bloqueos) {
        return ResponseEntity.status(status)
                .body(new ApiError(title, message, AppTime.now(), bloqueos));
    }
}
