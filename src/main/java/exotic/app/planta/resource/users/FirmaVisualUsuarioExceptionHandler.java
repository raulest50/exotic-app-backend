package exotic.app.planta.resource.users;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice(assignableTypes = FirmaVisualUsuarioResource.class)
public class FirmaVisualUsuarioExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidInput(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Firma visual inválida", exception);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException exception) {
        return problem(HttpStatus.NOT_FOUND, "Firma visual no encontrada", exception);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(IllegalStateException exception) {
        return problem(HttpStatus.CONFLICT, "Conflicto al actualizar la firma visual", exception);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleConcurrentConflict(DataIntegrityViolationException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "La firma visual fue modificada simultáneamente. Recargue la información e intente nuevamente."
        );
        detail.setTitle("Conflicto al actualizar la firma visual");
        return detail;
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
