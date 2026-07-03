package exotic.app.planta.resource.calidad;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.calidad.EstadoControlProcesoPlantilla;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.EjecucionDetalleResponse;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.EjecucionListItemResponse;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.EjecucionRequest;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.LoteProduccionResumen;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.PlantillaRequest;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.PlantillaResponse;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.PrepararEjecucionResponse;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.calidad.CalidadControlProcesoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/calidad")
@RequiredArgsConstructor
public class CalidadControlProcesoResource {

    private static final String TAB_VERSIONADO = "VERSIONADO_CONTROL_PROCESO";
    private static final String TAB_DILIGENCIAR = "DILIGENCIAR_CONTROL_PROCESO";
    private static final String TAB_HISTORIAL = "HISTORIAL_CONTROL_PROCESO";

    private final CalidadControlProcesoService service;
    private final UserRepository userRepository;

    @GetMapping("/plantillas")
    public List<PlantillaResponse> listarPlantillas(
            Authentication authentication,
            @RequestParam(required = false) Integer areaId,
            @RequestParam(required = false) EstadoControlProcesoPlantilla estado
    ) {
        requireTabAccess(authentication, TAB_VERSIONADO);
        return service.listarPlantillas(areaId, estado);
    }

    @PostMapping("/plantillas")
    public PlantillaResponse guardarBorrador(
            Authentication authentication,
            @RequestBody PlantillaRequest request
    ) {
        requireTabAccess(authentication, TAB_VERSIONADO);
        return service.guardarBorrador(request);
    }

    @PostMapping("/plantillas/{id}/publicar")
    public PlantillaResponse publicarPlantilla(
            Authentication authentication,
            @PathVariable Long id
    ) {
        requireTabAccess(authentication, TAB_VERSIONADO);
        return service.publicarPlantilla(id);
    }

    @PostMapping("/plantillas/{id}/retirar")
    public PlantillaResponse retirarPlantilla(
            Authentication authentication,
            @PathVariable Long id
    ) {
        requireTabAccess(authentication, TAB_VERSIONADO);
        return service.retirarPlantilla(id);
    }

    @GetMapping("/plantillas/vigente")
    public PlantillaResponse plantillaVigente(
            Authentication authentication,
            @RequestParam Integer areaId
    ) {
        requireTabAccess(authentication, TAB_DILIGENCIAR);
        return service.plantillaVigente(areaId);
    }

    @GetMapping("/lotes-produccion/search")
    public List<LoteProduccionResumen> buscarLotesProduccion(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "20") int size
    ) {
        requireTabAccess(authentication, TAB_DILIGENCIAR);
        return service.buscarLotesProduccion(search, size);
    }

    @GetMapping("/ejecuciones/preparar")
    public PrepararEjecucionResponse prepararEjecucion(
            Authentication authentication,
            @RequestParam Integer areaId,
            @RequestParam Long loteId
    ) {
        requireTabAccess(authentication, TAB_DILIGENCIAR);
        return service.prepararEjecucion(areaId, loteId);
    }

    @PostMapping("/ejecuciones")
    public EjecucionDetalleResponse guardarEjecucion(
            Authentication authentication,
            @RequestBody EjecucionRequest request
    ) {
        User user = requireTabAccess(authentication, TAB_DILIGENCIAR);
        return service.guardarEjecucion(user, request);
    }

    @GetMapping("/ejecuciones")
    public Page<EjecucionListItemResponse> buscarEjecuciones(
            Authentication authentication,
            @RequestParam(required = false) Integer areaId,
            @RequestParam(required = false) Long loteId,
            @RequestParam(required = false) String producto,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        requireTabAccess(authentication, TAB_HISTORIAL);
        return service.buscarEjecuciones(areaId, loteId, producto, fechaDesde, fechaHasta, page, size);
    }

    @GetMapping("/ejecuciones/{id}")
    public EjecucionDetalleResponse detalleEjecucion(
            Authentication authentication,
            @PathVariable Long id
    ) {
        requireTabAccess(authentication, TAB_HISTORIAL);
        return service.detalleEjecucion(id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Solicitud invalida", ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No encontrado", ex.getMessage()));
    }

    private User requireTabAccess(Authentication authentication, String... tabIds) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

        if (isMasterLike(user.getUsername())) {
            return user;
        }

        for (String tabId : tabIds) {
            boolean hasTabAccess = UserAccessEvaluator.tabNivel(user, ModuloSistema.CALIDAD, tabId)
                    .orElse(0) >= 1;
            if (hasTabAccess) {
                return user;
            }
        }

        boolean hasLegacyMainAccess = UserAccessEvaluator.tabNivel(user, ModuloSistema.CALIDAD, "MAIN")
                .orElse(0) >= 1;
        if (hasLegacyMainAccess) {
            return user;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para esta operacion de calidad.");
    }

    private boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return "master".equals(normalized) || "super_master".equals(normalized);
    }
}
