package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.produccion.dto.MpsFabricacionDTOs.*;
import exotic.app.planta.model.produccion.dto.OrdenFabricacionDTOs;
import exotic.app.planta.model.users.*;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.produccion.AreaMpsConsultaService;
import exotic.app.planta.service.produccion.MpsFabricacionService;
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

/** Las rutas operativas exponen exclusivamente consultas. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MpsFabricacionResource {
    private final MpsFabricacionService service;
    private final AreaMpsConsultaService areaService;
    private final UserRepository userRepo;

    @GetMapping("/produccion/mps-of")
    public ProgramaResponse consultar(Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        requirePlanner(auth, 1);
        return service.consultar(weekStartDate, null);
    }

    @PutMapping("/produccion/mps-of/{weekStartDate}")
    public ProgramaResponse guardar(Authentication auth,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @Valid @RequestBody GuardarRequest request) {
        return service.guardar(weekStartDate, request, requirePlanner(auth, 2));
    }

    @GetMapping("/produccion/mps-of/ordenes")
    public Page<OrdenResponse> ordenes(Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        requirePlanner(auth, 1);
        return service.ordenes(weekStartDate, page, size, null);
    }

    @GetMapping("/produccion/mps-of/ordenes/{id}")
    public OrdenFabricacionDTOs.Response detalle(Authentication auth, @PathVariable Long id) {
        requirePlanner(auth, 1);
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

    private User requirePlanner(Authentication auth, int nivel) {
        User user = requireUser(auth);
        String username = user.getUsername().trim().toLowerCase(Locale.ROOT);
        if ("master".equals(username) || "super_master".equals(username)) return user;
        if (UserAccessEvaluator.tabNivel(user, ModuloSistema.PRODUCCION, "CREAR_ORDEN_FABRICACION").orElse(0) < nivel) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para esta operacion del MPS OF.");
        }
        return user;
    }

    private User requireUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        return userRepo.findByUsername(auth.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }
}
