package exotic.app.planta.resource.productos.exceptions;

import exotic.app.planta.config.AppTime;
import lombok.Getter;
import java.time.LocalDateTime;

/**
 * Clase que contiene las excepciones personalizadas para el manejo de errores
 * relacionados con la entidad Familia y una clase de respuesta de error estandarizada.
 */
public class FamiliaExceptions {

    /**
     * Clase para respuestas de error estandarizadas
     */
    @Getter
    public static class ErrorResponse {
        private final boolean success;
        private final String message;
        private final LocalDateTime timestamp;

        public ErrorResponse(String message) {
            this.success = false;
            this.message = message;
            this.timestamp = AppTime.now();
        }
    }

    /**
     * Excepción base para errores de validación
     */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    /**
     * Excepción para ID duplicado
     */
    public static class DuplicateIdException extends ValidationException {
        public DuplicateIdException(String message) {
            super(message);
        }
    }

    /**
     * Excepción para nombre duplicado
     */
    public static class DuplicateNameException extends ValidationException {
        public DuplicateNameException(String message) {
            super(message);
        }
    }

    /**
     * Excepción para campos vacíos
     */
    public static class EmptyFieldException extends ValidationException {
        public EmptyFieldException(String message) {
            super(message);
        }
    }
}
