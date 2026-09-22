package exotic.app.planta.resource.controles;

import com.fasterxml.jackson.annotation.JsonInclude;
import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.controles.dto.BloqueoControlDTO;
import exotic.app.planta.resource.calidad.CalidadControlUnificadoResource;
import exotic.app.planta.resource.produccion.ProcesoControlResource;
import exotic.app.planta.service.controles.ControlBloqueoException;
import exotic.app.planta.service.controles.CodigoPlanDuplicadoException;
import exotic.app.planta.service.controles.ControlIdempotencyService;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@RestControllerAdvice(assignableTypes = {
        ProcesoControlResource.class,
        CalidadControlUnificadoResource.class,
        ControlCatalogResource.class,
        ControlRutaResource.class
})
public class ControlApiExceptionHandler {
    public record ApiError(String title, String message, LocalDateTime timestamp,
                           List<BloqueoControlDTO> bloqueos,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String field) {
        public ApiError(String title, String message, LocalDateTime timestamp, List<BloqueoControlDTO> bloqueos) {
            this(title, message, timestamp, bloqueos, null, null);
        }
    }

    @ExceptionHandler(CodigoPlanDuplicadoException.class)
    public ResponseEntity<ApiError> codigoDuplicado(CodigoPlanDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "Código en uso", ex.getMessage(), AppTime.now(), List.of(), "PLAN_CODE_ALREADY_EXISTS", "codigo"));
    }

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
        String field = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().findFirst()
                    .map(error -> error.getField()).orElse(null)
                : null;
        return ResponseEntity.badRequest().body(new ApiError(
                "Solicitud invalida", mensaje, AppTime.now(), List.of(), null, field));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> cabeceraRequerida(MissingRequestHeaderException ex) {
        boolean missingRequestKey = ControlIdempotencyService.HEADER.equalsIgnoreCase(ex.getHeaderName());
        String message = missingRequestKey
                ? "No se pudo identificar la solicitud de guardado. Actualice la página e intente nuevamente."
                : "Falta información requerida en la solicitud. Actualice la página e intente nuevamente.";
        return ResponseEntity.badRequest().body(new ApiError(
                "Solicitud incompleta", message, AppTime.now(), List.of(),
                missingRequestKey ? "IDEMPOTENCY_KEY_REQUIRED" : null, null));
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
