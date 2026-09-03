package exotic.app.planta.resource.produccion;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.produccion.dto.BatchRecordDTOs;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.produccion.BatchRecordPdfService;
import exotic.app.planta.service.produccion.BatchRecordService;
import exotic.app.planta.service.controles.ControlIdempotencyService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/produccion/batch-records")
@RequiredArgsConstructor
public class BatchRecordResource {

    private static final String TAB_PRODUCCION = "CONSULTAR_BATCH_RECORD";
    private static final String TAB_CALIDAD = "REVISION_LIBERACION_LOTES";

    private final BatchRecordService batchRecordService;
    private final BatchRecordPdfService pdfService;
    private final UserRepository userRepository;
    private final ControlIdempotencyService idempotencyService;

    @GetMapping
    public Page<BatchRecordDTOs.ListItem> buscar(
            Authentication authentication,
            @RequestParam(required = false) Integer ordenProduccionId,
            @RequestParam(required = false) String lote,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        requireViewAccess(authentication);
        return batchRecordService.buscar(ordenProduccionId, lote, page, size);
    }

    @GetMapping("/{id}")
    public BatchRecordDTOs.Detail detalle(
            Authentication authentication,
            @PathVariable Long id
    ) {
        requireViewAccess(authentication);
        return batchRecordService.detalle(id);
    }

    @GetMapping("/{id}/revisiones")
    public List<BatchRecordDTOs.Revision> revisiones(
            Authentication authentication,
            @PathVariable Long id
    ) {
        requireViewAccess(authentication);
        return batchRecordService.revisiones(id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) Integer revision,
            @RequestParam(defaultValue = "false") boolean actual,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        requireViewAccess(authentication);
        BatchRecordPdfService.PdfResult result = pdfService.generar(id, revision, actual);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                (download ? "attachment" : "inline")
                        + "; filename=\"" + result.nombreArchivo() + "\"");
        headers.setCacheControl("no-store, max-age=0");
        return new ResponseEntity<>(result.contenido(), headers, HttpStatus.OK);
    }

    @PostMapping("/{id}/enviar-calidad")
    public BatchRecordDTOs.Detail enviarCalidad(
            Authentication authentication,
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @RequestHeader(ControlIdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody BatchRecordDTOs.EnvioCalidadRequest request
    ) {
        User actor = requireProductionAccess(authentication, 2);
        return idempotencyService.ejecutar(
                actor, "ENVIO_BATCH_RECORD_CALIDAD", "batch-record/" + id,
                idempotencyKey, request, BatchRecordDTOs.Detail.class, () -> {
                    batchRecordService.enviarARevisionCalidad(
                            id, actor, request.getMotivo(), servletRequest.getRemoteAddr(),
                            servletRequest.getHeader("User-Agent"));
                    return batchRecordService.detalle(id);
                });
    }

    @GetMapping("/{id}/prevalidacion-envio")
    public BatchRecordDTOs.PrevalidacionEnvio prevalidarEnvio(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean reenvio
    ) {
        requireProductionAccess(authentication, 1);
        return batchRecordService.prevalidarEnvio(id, reenvio);
    }

    @PostMapping("/{id}/reenviar-calidad")
    public BatchRecordDTOs.Detail reenviarCalidad(
            Authentication authentication,
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @RequestHeader(ControlIdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody BatchRecordDTOs.EnvioCalidadRequest request
    ) {
        User actor = requireProductionAccess(authentication, 2);
        return idempotencyService.ejecutar(
                actor, "REENVIO_BATCH_RECORD_CALIDAD", "batch-record/" + id,
                idempotencyKey, request, BatchRecordDTOs.Detail.class, () -> {
                    batchRecordService.reenviarARevisionCalidad(
                            id, actor, request.getMotivo(), servletRequest.getRemoteAddr(),
                            servletRequest.getHeader("User-Agent"));
                    return batchRecordService.detalle(id);
                });
    }

    @PostMapping("/{id}/secciones-correccion/{seccionId}/atender")
    public BatchRecordDTOs.Detail atenderSeccionCorreccion(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long seccionId,
            @RequestHeader(ControlIdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody BatchRecordDTOs.AtenderSeccionCorreccionRequest request
    ) {
        User actor = requireProductionAccess(authentication, 2);
        return idempotencyService.ejecutar(
                actor, "ATENCION_SECCION_CORRECCION", "batch-record/" + id + "/seccion/" + seccionId,
                idempotencyKey, request, BatchRecordDTOs.Detail.class, () -> {
                    batchRecordService.atenderSeccionCorreccion(
                            id, seccionId, actor, request.getJustificacion());
                    return batchRecordService.detalle(id);
                });
    }

    private User requireViewAccess(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if (isMasterLike(user.getUsername())) return user;
        boolean produccion = UserAccessEvaluator.tabNivel(
                user, ModuloSistema.PRODUCCION, TAB_PRODUCCION).orElse(0) >= 1;
        boolean calidad = UserAccessEvaluator.tabNivel(
                user, ModuloSistema.CALIDAD, TAB_CALIDAD).orElse(0) >= 1;
        if (!produccion && !calidad) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No tiene acceso a expedientes digitales.");
        }
        return user;
    }

    private User requireProductionAccess(Authentication authentication, int minNivel) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        int nivel = UserAccessEvaluator.tabNivel(
                user, ModuloSistema.PRODUCCION, TAB_PRODUCCION).orElse(0);
        if (nivel < minNivel) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tiene el nivel requerido para enviar expedientes a Calidad.");
        }
        return user;
    }

    private boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return "master".equals(normalized) || "super_master".equals(normalized);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Solicitud inválida", error.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No encontrado", error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Conflicto de estado", error.getMessage()));
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentChange(
            ConcurrencyFailureException error
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "Conflicto de concurrencia",
                        "El expediente cambió mientras se procesaba la acción; actualice e intente de nuevo."));
    }
}
