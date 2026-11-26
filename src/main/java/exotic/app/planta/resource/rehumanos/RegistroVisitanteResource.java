package exotic.app.planta.resource.rehumanos;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.Registro;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.RegistrarSalidaRequest;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.rehumanos.RegistroVisitanteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
                                               @RequestParam(required = false) String nombre) {
        guard.requireTabAccess(authentication, ModuloSistema.PERSONAL_PLANTA, "CONTROL_VISITANTES", 1,
                "No tiene acceso a Gestion Humana / Control de visitantes.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listar(page, size, fecha, horaDesde, horaHasta, nombre));
    }

    @PatchMapping(value = "/{id}/salida", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Registro> registrarSalida(Authentication authentication, @PathVariable Long id,
                                                    @RequestBody RegistrarSalidaRequest request) {
        User actor = guard.requireTabAccess(authentication, ModuloSistema.PERSONAL_PLANTA, "CONTROL_VISITANTES", 2,
                "No tiene permiso para registrar salidas de visitantes.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.registrarSalida(id, request, actor.getId(), actor.getUsername()));
    }

    @GetMapping(value = "/{id}/firma", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> firma(Authentication authentication, @PathVariable Long id) {
        guard.requireTabAccess(authentication, ModuloSistema.PERSONAL_PLANTA, "CONTROL_VISITANTES", 1,
                "No tiene acceso a Gestion Humana / Control de visitantes.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .header("X-Content-Type-Options", "nosniff").body(service.obtenerFirma(id));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> filtrosInvalidos() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(new ErrorResponse(
                "Solicitud invalida", "Revise la fecha (AAAA-MM-DD), las horas (HH:mm, desde no mayor que hasta), "
                + "el nombre (maximo 160 caracteres), el registro y la paginacion (pagina desde 0, tamano de 1 a 50)."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> salidaInvalida() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(new ErrorResponse(
                "Solicitud invalida", "Envie solo fechaSalida como texto ISO 8601 con offset."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> accesoRechazado(ResponseStatusException error) {
        if (error.getStatusCode().value() == 404) return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .cacheControl(CacheControl.noStore()).body(new ErrorResponse(
                        "Registro no disponible", "No existe el registro de visita o su firma no esta disponible."));
        if (error.getStatusCode().value() == 400 || error.getStatusCode().value() == 409) {
            return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore())
                    .body(new ErrorResponse(error.getStatusCode().value() == 409 ? "Salida no disponible" : "Salida invalida",
                            error.getReason()));
        }
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("Acceso no autorizado", "No tiene permiso para esta operacion de visitantes."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> listadoNoDisponible() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("Operacion no disponible", "No fue posible completar la operacion de visitantes."));
    }
}
