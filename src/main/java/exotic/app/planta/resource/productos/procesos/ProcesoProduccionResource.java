package exotic.app.planta.resource.productos.procesos;

import jakarta.validation.Valid;
import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.dto.ProcesoProduccionDTO;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersionResponse;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoService;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/procesos-produccion")
@RequiredArgsConstructor
@Slf4j
public class ProcesoProduccionResource {

    private final ProcesoProduccionService procesoProduccionService;
    private final ProcesoProduccionDocumentoService procesoProduccionDocumentoService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> createProcesoProduccion(@Valid @RequestBody ProcesoProduccionDTO procesoProduccionDTO) {
        log.info("REST request para crear un nuevo proceso de producción: {}", procesoProduccionDTO.getNombre());

        try {
            ProcesoProduccion result = procesoProduccionService.createProcesoProduccionFromDTO(procesoProduccionDTO);
            return ResponseEntity
                    .created(URI.create("/api/procesos-produccion/" + result.getProcesoId()))
                    .body(result);
        } catch (IllegalArgumentException e) {
            log.error("Error al crear proceso de producción: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(new ErrorResponse("Error al crear proceso", e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado al crear proceso de producción", e);
            return ResponseEntity
                    .internalServerError()
                    .body(new ErrorResponse("Error interno del servidor", "Ocurrió un error inesperado"));
        }
    }

    @GetMapping("/paginados")
    public ResponseEntity<Page<ProcesoProduccion>> getProcesosProduccionPaginados(Pageable pageable) {
        log.info("REST request para obtener procesos de producción paginados");
        Page<ProcesoProduccion> result = procesoProduccionService.getProcesosProduccionPaginados(pageable);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/update_proc_produccion/{id}")
    public ResponseEntity<?> updateProcesoProduccion(
            @PathVariable Integer id,
            @Valid @RequestBody ProcesoProduccionDTO procesoProduccionDTO) {
        log.info("REST request para actualizar proceso de producción con ID: {}", id);

        // Verificar que el ID en el path coincide con el ID en el DTO (si está presente)
        if (procesoProduccionDTO.getProcesoId() != null && !procesoProduccionDTO.getProcesoId().equals(id)) {
            return ResponseEntity
                    .badRequest()
                    .body(new ErrorResponse("Error de validación", 
                            "El ID en la URL (" + id + ") no coincide con el ID en el cuerpo de la solicitud (" 
                            + procesoProduccionDTO.getProcesoId() + ")"));
        }

        // Verificar que el proceso existe
        if (!procesoProduccionService.getProcesoProduccionById(id).isPresent()) {
            return ResponseEntity
                    .status(404)
                    .body(new ErrorResponse("Recurso no encontrado", 
                            "No se encontró el proceso de producción con ID: " + id));
        }

        try {
            ProcesoProduccion result = procesoProduccionService.updateProcesoProduccionFromDTO(id, procesoProduccionDTO);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Error al actualizar proceso de producción: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(new ErrorResponse("Error al actualizar proceso", e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado al actualizar proceso de producción", e);
            return ResponseEntity
                    .internalServerError()
                    .body(new ErrorResponse("Error interno del servidor", "Ocurrió un error inesperado"));
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteProcesoProduccion(@PathVariable Integer id) {
        log.info("REST request para eliminar proceso de producción con ID: {}", id);

        if (!procesoProduccionService.getProcesoProduccionById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }

        try {
            procesoProduccionService.deleteProcesoProduccion(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            log.warn("No se puede eliminar el proceso de producción con ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("No se puede eliminar el proceso de producción", e.getMessage()));
        }
    }

    /**
     * Verifica si un proceso de producción puede ser eliminado
     * 
     * @param id El ID del proceso de producción a verificar
     * @return Respuesta con información sobre si el proceso es eliminable
     */
    @GetMapping("/is-deletable/{id}")
    public ResponseEntity<?> isProcesoProduccionDeletable(@PathVariable Integer id) {
        log.info("REST request para verificar si el proceso de producción con ID: {} es eliminable", id);

        Map<String, Object> result = procesoProduccionService.isProcesoProduccionDeletable(id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/documentos/versiones")
    public ResponseEntity<List<ProcesoProduccionDocumentoVersionResponse>> getDocumentoVersiones(
            Authentication authentication,
            @PathVariable Integer id
    ) {
        requireProductosAccess(authentication, 2);
        try {
            return ResponseEntity.ok(procesoProduccionDocumentoService.getVersiones(id));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @PostMapping(
            value = "/{id}/documentos/versiones",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> crearDocumentoVersion(
            Authentication authentication,
            @PathVariable Integer id,
            @RequestPart("archivo") MultipartFile archivo,
            @RequestPart(value = "motivoCambio", required = false) String motivoCambio
    ) {
        User actor = requireProductosAccess(authentication, 2);
        try {
            ProcesoProduccionDocumentoVersionResponse created = procesoProduccionDocumentoService
                    .crearNuevaVersion(id, archivo, motivoCambio, actor.getUsername());
            return ResponseEntity
                    .created(URI.create(
                            "/api/procesos-produccion/" + id
                                    + "/documentos/versiones/" + created.id()
                    ))
                    .body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Documento no valido", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Recurso no encontrado", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("No se pudo guardar el documento del proceso {}", id, e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Error de almacenamiento", e.getMessage()));
        }
    }

    @GetMapping("/{id}/documentos/versiones/{versionId}/archivo")
    public ResponseEntity<?> descargarDocumentoVersion(
            Authentication authentication,
            @PathVariable Integer id,
            @PathVariable Long versionId
    ) {
        requireProductosAccess(authentication, 2);
        try {
            ProcesoProduccionDocumentoService.DescargaDocumento descarga =
                    procesoProduccionDocumentoService.getDescarga(id, versionId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(descarga.contentType()));
            headers.setContentLength(descarga.contentLength());
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(descarga.fileName(), StandardCharsets.UTF_8)
                    .build());
            headers.set("X-Content-Type-Options", "nosniff");
            headers.setCacheControl(CacheControl.noStore().getHeaderValue());
            return new ResponseEntity<Resource>(descarga.resource(), headers, HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Recurso no encontrado", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("No se pudo descargar la version {} del proceso {}", versionId, id, e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Archivo no disponible", e.getMessage()));
        }
    }

    private User requireProductosAccess(Authentication authentication, int minNivel) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED, "No autenticado");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuario no encontrado"));
        if (isMasterLike(user.getUsername())) {
            return user;
        }

        int nivel = UserAccessEvaluator.maxNivelForModulo(user, ModuloSistema.PRODUCTOS).orElse(0);
        if (nivel < minNivel) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "No tiene permisos para administrar documentos de procesos de produccion."
            );
        }
        return user;
    }

    private static boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.toLowerCase();
        return "master".equals(normalized) || "super_master".equals(normalized);
    }
}
