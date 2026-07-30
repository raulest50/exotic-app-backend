package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.CargaPuntosReordenDTOs;
import exotic.app.planta.service.commons.CargaPuntosReordenValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

class CargaMasivaPuntosReordenExceptionHandlerTest {

    private final CargaMasivaPuntosReordenExceptionHandler handler =
            new CargaMasivaPuntosReordenExceptionHandler();

    @Test
    void returnsConflictForStaleTemplate() {
        CargaPuntosReordenDTOs.ValidationResponse response = invalidResponse();

        ResponseEntity<CargaPuntosReordenDTOs.ValidationResponse> result =
                handler.handleValidation(new CargaPuntosReordenValidationException(
                        "Plantilla desactualizada",
                        response,
                        true));

        assertEquals(CONFLICT, result.getStatusCode());
        assertEquals(response, result.getBody());
    }

    @Test
    void returnsBadRequestForOtherValidationErrors() {
        ResponseEntity<CargaPuntosReordenDTOs.ValidationResponse> result =
                handler.handleValidation(new CargaPuntosReordenValidationException(
                        "Archivo inválido",
                        invalidResponse(),
                        false));

        assertEquals(BAD_REQUEST, result.getStatusCode());
    }

    private static CargaPuntosReordenDTOs.ValidationResponse invalidResponse() {
        return new CargaPuntosReordenDTOs.ValidationResponse(
                false,
                1,
                0,
                0,
                0,
                1,
                List.of(),
                List.of(new CargaPuntosReordenDTOs.ErrorFila(
                        2,
                        "MAT-001",
                        "punto_reorden_actual",
                        "Conflicto")));
    }
}
