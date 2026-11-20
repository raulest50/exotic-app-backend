package exotic.app.planta.resource.rehumanos;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.organizacion.personal.dto.PermisoPersonalDTOs.*;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.rehumanos.PermisoPersonalPdfService;
import exotic.app.planta.service.rehumanos.PermisoPersonalService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import static org.springframework.http.HttpStatus.CONFLICT;

@RestController
@RequestMapping("/integrantes-personal/permisos")
@RequiredArgsConstructor
public class PermisoPersonalResource {
    private final PermisoPersonalService service;
    private final PermisoPersonalPdfService pdf;
    private final ModuleTabAccessGuard guard;

    @GetMapping("/contexto")
    public Contexto contexto(Authentication auth) { return service.contexto(actor(auth)); }

    @GetMapping("/configuracion")
    public Configuracion configuracion(Authentication auth) { return service.configuracion(actor(auth)); }

    @PutMapping("/configuracion/{empleadoId}")
    public Configuracion configurar(Authentication auth, @PathVariable Long empleadoId, @RequestBody ConfigurarRequest request) {
        return service.configurar(empleadoId, request, actor(auth));
    }

    @GetMapping
    public Page<Resumen> listar(Authentication auth, @RequestParam(defaultValue = "MIAS") Scope scope,
                                 @RequestParam(required = false) Estado estado,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.listar(scope, estado, desde, hasta, q, page, size, actor(auth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Detalle crear(Authentication auth, @RequestBody Borrador request) { return service.crear(request, actor(auth)); }

    @GetMapping("/{id}")
    public Detalle detalle(Authentication auth, @PathVariable Long id) { return service.detalle(id, actor(auth)); }

    @PutMapping("/{id}")
    public Detalle guardar(Authentication auth, @PathVariable Long id, @RequestBody Borrador request) {
        return service.guardar(id, request, actor(auth));
    }

    @PostMapping("/{id}/presentar")
    public Detalle presentar(Authentication auth, @PathVariable Long id, @RequestBody PresentarRequest request) {
        return service.presentar(id, request, actor(auth));
    }

    @PostMapping("/{id}/decidir")
    public Detalle decidir(Authentication auth, @PathVariable Long id, @RequestBody DecidirRequest request) {
        return service.decidir(id, request, actor(auth));
    }

    @PostMapping("/{id}/anular")
    public Detalle anular(Authentication auth, @PathVariable Long id, @RequestBody AnularRequest request) {
        return service.anular(id, request, actor(auth));
    }

    @PutMapping(value = "/{id}/soporte", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Detalle guardarSoporte(Authentication auth, @PathVariable Long id, @RequestParam Long version,
                                   @RequestParam MultipartFile archivo) {
        return service.guardarSoporte(id, version, archivo, actor(auth));
    }

    @DeleteMapping("/{id}/soporte")
    public Detalle borrarSoporte(Authentication auth, @PathVariable Long id, @RequestParam Long version) {
        return service.borrarSoporte(id, version, actor(auth));
    }

    @GetMapping("/{id}/soporte")
    public ResponseEntity<byte[]> soporte(Authentication auth, @PathVariable Long id) {
        Archivo archivo = service.soporte(id, actor(auth));
        return descarga(archivo.contenido(), archivo.contentType(), archivo.nombre());
    }

    @GetMapping("/firma-vigente")
    public ResponseEntity<FirmaVigente> firmaVigente(Authentication auth) {
        FirmaVigente firma = service.firmaVigente(actor(auth));
        return firma == null ? ResponseEntity.noContent().build() : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(firma);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> documento(Authentication auth, @PathVariable Long id) {
        DocumentoPdf documento = service.documentoPdf(id, actor(auth));
        return descarga(pdf.generar(documento), MediaType.APPLICATION_PDF_VALUE, documento.detalle().folio() + ".pdf");
    }

    private User actor(Authentication auth) {
        return guard.requireAnyTabAccess(auth, ModuloSistema.PERSONAL_PLANTA,
                Map.of("PERMISOS", 1, "APROBACIONES", 1),
                "No tiene acceso a Gestión Humana / Permisos ni Aprobaciones.");
    }

    private ResponseEntity<byte[]> descarga(byte[] bytes, String type, String nombre) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).contentLength(bytes.length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "sandbox").body(bytes);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> conflicto(OptimisticLockingFailureException error) {
        return ResponseEntity.status(CONFLICT).body(new ErrorResponse("Cambio concurrente", "La solicitud cambió. Recargue antes de continuar."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> operacionRechazada(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("No fue posible completar la operación", error.getReason()));
    }
}
