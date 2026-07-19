package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.CargaCostosDTOs;
import exotic.app.planta.service.commons.CargaCostosStateException;
import exotic.app.planta.service.commons.CargaCostosTokenException;
import exotic.app.planta.service.commons.CargaCostosValidationException;
import exotic.app.planta.service.productos.CostoVersionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.NoSuchElementException;

@RestControllerAdvice(assignableTypes = CargaMasivaCostosResource.class)
public class CargaMasivaCostosExceptionHandler {

    @ExceptionHandler(CargaCostosValidationException.class)
    public ResponseEntity<CargaCostosDTOs.ErrorResponse> handleValidation(
            CargaCostosValidationException ex
    ) {
        return ResponseEntity.unprocessableEntity().body(new CargaCostosDTOs.ErrorResponse(
                "ARCHIVO_INVALIDO",
                ex.getMessage(),
                ex.getErrores(),
                ex.getAdvertencias(),
                null));
    }

    @ExceptionHandler(CargaCostosTokenException.class)
    public ResponseEntity<CargaCostosDTOs.ErrorResponse> handleToken(CargaCostosTokenException ex) {
        HttpStatus status = ex.isBloqueado() ? HttpStatus.LOCKED : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(new CargaCostosDTOs.ErrorResponse(
                ex.isBloqueado() ? "PREPARACION_BLOQUEADA" : "TOKEN_INCORRECTO",
                ex.getMessage(),
                List.of(),
                List.of(),
                ex.getIntentosRestantes()));
    }

    @ExceptionHandler(CostoVersionConflictException.class)
    public ResponseEntity<CargaCostosDTOs.ErrorResponse> handleCostConflict(
            CostoVersionConflictException ex
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                CargaCostosDTOs.ErrorResponse.simple(
                        "COSTO_MODIFICADO",
                        ex.getMessage() + ". Prepare nuevamente el archivo"));
    }

    @ExceptionHandler(CargaCostosStateException.class)
    public ResponseEntity<CargaCostosDTOs.ErrorResponse> handleState(CargaCostosStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                CargaCostosDTOs.ErrorResponse.simple(ex.getCodigo(), ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<CargaCostosDTOs.ErrorResponse> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                CargaCostosDTOs.ErrorResponse.simple("PREPARACION_NO_ENCONTRADA", ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<CargaCostosDTOs.ErrorResponse> handleBadRequest(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getAllErrors().stream()
                        .findFirst().map(error -> error.getDefaultMessage()).orElse("Solicitud invalida")
                : ex.getMessage();
        return ResponseEntity.badRequest().body(
                CargaCostosDTOs.ErrorResponse.simple("SOLICITUD_INVALIDA", message));
    }
}
