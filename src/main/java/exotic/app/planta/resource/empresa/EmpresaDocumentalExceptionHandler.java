package exotic.app.planta.resource.empresa;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice(assignableTypes = {
        EmpresaIdentidadDocumentalResource.class,
        EmpresaIdentidadLegalResource.class,
        EmpresaLogoDocumentalResource.class
})
public class EmpresaDocumentalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidInput(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Datos documentales invalidos", exception);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException exception) {
        return problem(HttpStatus.NOT_FOUND, "Version documental no encontrada", exception);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleUnavailable(IllegalStateException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Configuracion documental no disponible", exception);
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
