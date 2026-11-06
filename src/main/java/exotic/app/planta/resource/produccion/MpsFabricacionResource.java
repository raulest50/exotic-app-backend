package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.produccion.dto.MpsFabricacionDTOs.*;
import exotic.app.planta.model.produccion.dto.OrdenFabricacionDTOs;
import exotic.app.planta.model.users.*;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.produccion.AreaMpsConsultaService;
import exotic.app.planta.service.produccion.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Locale;
import java.util.List;

/** Las rutas operativas exponen exclusivamente consultas. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MpsFabricacionResource {
    private final MpsFabricacionService service;
    private final AreaMpsConsultaService areaService;
    private final UserRepository userRepo;
    private final MpsFabricacionAprobacionService aprobacionService;
    private final MpsFabricacionObservacionService observacionService;
    private final MpsFabricacionEmisionService emisionService;
    private final MpsFabricacionAprobadoEditService editService;

    @GetMapping("/produccion/mps-of")
    public ProgramaResponse consultar(Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        requireAccess(auth, "PROGRAMACION_MPS_OF", "APROBACION_MPS_OF");
        return service.consultar(weekStartDate, null);
    }

    @PutMapping("/produccion/mps-of/{weekStartDate}")
    public ProgramaResponse guardar(Authentication auth,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @Valid @RequestBody GuardarRequest request) {
        return service.guardar(weekStartDate, request, requireAccess(auth, "PROGRAMACION_MPS_OF"));
    }

    @GetMapping("/produccion/mps-of/ordenes")
    public Page<OrdenResponse> ordenes(Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        requireAccess(auth, "PROGRAMACION_MPS_OF", "APROBACION_MPS_OF");
        return service.ordenes(weekStartDate, page, size, null);
    }

    @GetMapping("/produccion/mps-of/ordenes/{id}")
    public OrdenFabricacionDTOs.Response detalle(Authentication auth, @PathVariable Long id) {
        requireAccess(auth, "PROGRAMACION_MPS_OF", "APROBACION_MPS_OF");
        return service.detalleOrden(id, null);
    }

    @GetMapping("/area-operativa-panel/mps-of")
    public ProgramaResponse consultarOperativo(Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        return service.consultar(weekStartDate, areaService.requireArea(requireUser(auth), true));
    }

    @GetMapping("/area-operativa-panel/mps-of/ordenes")
    public Page<OrdenResponse> ordenesOperativas(Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.ordenes(weekStartDate, page, size, areaService.requireArea(requireUser(auth), true));
    }

    @GetMapping("/area-operativa-panel/mps-of/ordenes/{id}")
    public OrdenFabricacionDTOs.Response detalleOperativo(Authentication auth, @PathVariable Long id) {
        return service.detalleOrden(id, areaService.requireArea(requireUser(auth), true));
    }

    @PostMapping("/produccion/mps-of/{weekStartDate}/aprobar")
    public ProgramaResponse aprobar(Authentication auth,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @Valid @RequestBody VersionRequest request) {
        return aprobacionService.aprobar(weekStartDate, request.getVersion(), requireAccess(auth, "APROBACION_MPS_OF"));
    }

    @PostMapping("/produccion/mps-of/{weekStartDate}/emitir")
    public ProgramaResponse emitir(Authentication auth,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @Valid @RequestBody VersionRequest request) {
        return emisionService.emitir(weekStartDate, request.getVersion(), requireAccess(auth, "APROBACION_MPS_OF"));
    }

    @GetMapping("/produccion/mps-of/{weekStartDate}/observaciones")
    public List<ObservacionResponse> observaciones(Authentication auth,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        requireAccess(auth, "PROGRAMACION_MPS_OF", "APROBACION_MPS_OF");
        return observacionService.listar(weekStartDate);
    }

    @PostMapping("/produccion/mps-of/{weekStartDate}/observaciones")
    public ObservacionResponse crearObservacion(Authentication auth,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @Valid @RequestBody CrearObservacionRequest request) {
        return observacionService.crear(weekStartDate, request, requireAccess(auth, "APROBACION_MPS_OF"));
    }

    @PostMapping("/produccion/mps-of/observaciones/{id}/atender")
    public ObservacionResponse atender(Authentication auth, @PathVariable Long id,
            @Valid @RequestBody AtenderObservacionRequest request) {
        return observacionService.atender(id, request, requireAccess(auth, "PROGRAMACION_MPS_OF"));
    }

    @PostMapping("/produccion/mps-of/observaciones/{id}/cerrar")
    public ObservacionResponse cerrar(Authentication auth, @PathVariable Long id,
            @Valid @RequestBody VersionRequest request) {
        return observacionService.cerrar(id, request, requireAccess(auth, "APROBACION_MPS_OF"));
    }

    @PostMapping("/produccion/mps-of/{weekStartDate}/propuestas")
    public ProgramaResponse agregarTanda(Authentication auth,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @Valid @RequestBody LineaRequest request) {
        return editService.agregar(weekStartDate, request, requireAccess(auth, "PROGRAMACION_MPS_OF"));
    }

    @PutMapping("/produccion/mps-of/{weekStartDate}/propuestas/{id}")
    public ProgramaResponse editarTanda(Authentication auth,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @PathVariable Long id, @Valid @RequestBody LineaRequest request) {
        return editService.editar(weekStartDate, id, request, requireAccess(auth, "PROGRAMACION_MPS_OF"));
    }

    @PostMapping("/produccion/mps-of/{weekStartDate}/propuestas/{id}/cancelar")
    public ProgramaResponse cancelarTanda(Authentication auth,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return editService.cancelar(weekStartDate, id, request.getVersion(), requireAccess(auth, "PROGRAMACION_MPS_OF"));
    }

    private User requireAccess(Authentication auth, String... tabs) {
        User user = requireUser(auth);
        String username = user.getUsername().trim().toLowerCase(Locale.ROOT);
        if ("master".equals(username) || "super_master".equals(username)) return user;
        for (String tab : tabs) {
            if (UserAccessEvaluator.tabNivel(user, ModuloSistema.PRODUCCION, tab).orElse(0) >= 1) return user;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para esta operacion del MPS OF.");
    }

    private User requireUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        return userRepo.findByUsername(auth.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }
}
