package exotic.app.planta.resource.rehumanos;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.Registro;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.rehumanos.RegistroVisitanteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;

@RestController
@RequestMapping("/gestion-humana/visitantes")
@RequiredArgsConstructor
public class RegistroVisitanteResource {
    private final RegistroVisitanteService service;
    private final ModuleTabAccessGuard guard;

    @GetMapping
    public ResponseEntity<Page<Registro>> listar(Authentication authentication,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                               @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm", fallbackPatterns = {"HH:mm"}) LocalTime horaDesde,
                                               @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm", fallbackPatterns = {"HH:mm"}) LocalTime horaHasta,
                                               @RequestParam(required = false) String nombre,
                                               @RequestParam(defaultValue = "DESC") String orden) {
        guard.requireTabAccess(authentication, ModuloSistema.PERSONAL_PLANTA, "CONTROL_VISITANTES", 1,
                "No tiene acceso a Gestion Humana / Control de visitantes.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listar(page, size, fecha, horaDesde, horaHasta, nombre, orden));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> filtrosInvalidos() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(new ErrorResponse(
                "Solicitud invalida", "Revise la fecha (AAAA-MM-DD), las horas (HH:mm, desde no mayor que hasta), "
                + "el nombre (maximo 160 caracteres), el orden (ASC o DESC) y la paginacion (pagina desde 0, tamano de 1 a 50)."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> accesoRechazado(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("Acceso no autorizado", "No tiene acceso al listado de visitantes."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> listadoNoDisponible() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("Listado no disponible", "No fue posible consultar los visitantes."));
    }
}
