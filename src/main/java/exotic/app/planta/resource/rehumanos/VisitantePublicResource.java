package exotic.app.planta.resource.rehumanos;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.Confirmacion;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.CrearRequest;
import exotic.app.planta.service.rehumanos.RegistroVisitanteService;
import exotic.app.planta.service.rehumanos.VisitantesPrivacidadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/visitantes")
@RequiredArgsConstructor
public class VisitantePublicResource {
    private final RegistroVisitanteService service;
    private final VisitantesPrivacidadService privacidadService;

    @GetMapping("/aviso")
    public ResponseEntity<VisitantesPrivacidadService.Aviso> aviso() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(privacidadService.obtenerAviso());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Confirmacion> registrar(@RequestBody CrearRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.registrar(request));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> solicitudInvalida() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(new ErrorResponse(
                "Solicitud invalida", "Revise los campos obligatorios y las autorizaciones. El autorregistro es para mayores de edad. Si el aviso cambió, recargue el formulario."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> registroNoHabilitado() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("Registro no disponible", "El registro está temporalmente fuera de servicio. Diríjase a recepción."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> registroNoDisponible() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("Registro no disponible", "No fue posible registrar la llegada. Intente nuevamente."));
    }
}
