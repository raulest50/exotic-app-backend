package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.CargaPuntosReordenDTOs;
import exotic.app.planta.service.commons.CargaPuntosReordenValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CargaMasivaPuntosReordenResource.class)
public class CargaMasivaPuntosReordenExceptionHandler {

    @ExceptionHandler(CargaPuntosReordenValidationException.class)
    public ResponseEntity<CargaPuntosReordenDTOs.ValidationResponse> handleValidation(
            CargaPuntosReordenValidationException exception
    ) {
        HttpStatus status = exception.isConflict()
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(exception.getResponse());
    }
}
