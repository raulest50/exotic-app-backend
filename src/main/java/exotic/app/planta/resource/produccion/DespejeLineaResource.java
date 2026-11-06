package exotic.app.planta.resource.produccion;

import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.produccion.dto.DespejeLineaDTOs.*;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.produccion.DespejeLineaService;
import exotic.app.planta.service.produccion.PlantillaDespejeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.NoSuchElementException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/despejes")
@RequiredArgsConstructor
public class DespejeLineaResource {
    private final PlantillaDespejeService plantillas;
    private final DespejeLineaService despejes;
    private final ModuleTabAccessGuard accessGuard;
    private final UserRepository userRepository;

    @GetMapping("/areas/{areaId}/config")
    public Config configuracion(Authentication auth, @PathVariable Integer areaId) {
        configurar(auth);
        return plantillas.configuracion(areaId);
    }
    @PutMapping("/areas/{areaId}/config")
    public Config configurar(Authentication auth, @PathVariable Integer areaId, @RequestBody ConfigRequest request) {
        configurar(auth);
        return plantillas.configurar(areaId, request);
    }
    @GetMapping("/areas/{areaId}/plantillas")
    public List<Plantilla> plantillas(Authentication auth, @PathVariable Integer areaId) {
        configurar(auth);
        return plantillas.listar(areaId);
    }
    @PostMapping("/areas/{areaId}/plantillas")
    public Plantilla guardarPlantilla(Authentication auth, @PathVariable Integer areaId, @RequestBody PlantillaRequest request) {
        return plantillas.guardar(areaId, request, configurar(auth));
    }
    @PostMapping("/plantillas/{id}/publicar")
    public Plantilla publicar(Authentication auth, @PathVariable Long id) {
        configurar(auth);
        return plantillas.publicar(id);
    }
    @PostMapping("/plantillas/{id}/retirar")
    public Plantilla retirar(Authentication auth, @PathVariable Long id) {
        configurar(auth);
        return plantillas.retirar(id);
    }
    @PostMapping("/ejecuciones/preparar")
    public Preparacion preparar(Authentication auth, @RequestBody PrepararRequest request) {
        return despejes.preparar(request, actor(auth));
    }
    @GetMapping("/ejecuciones/{id}")
    public Ejecucion detalle(Authentication auth, @PathVariable Long id) {
        return despejes.detalle(id, actor(auth));
    }
    @PutMapping("/ejecuciones/{id}")
    public Ejecucion guardar(Authentication auth, @PathVariable Long id, @RequestBody GuardarRequest request) {
        return despejes.guardar(id, request, actor(auth));
    }
    @PostMapping("/ejecuciones/{id}/enviar")
    public Ejecucion enviar(Authentication auth, @PathVariable Long id, @RequestBody EnviarRequest request) {
        return despejes.enviar(id, request, actor(auth));
    }
    @PostMapping("/ejecuciones/{id}/revisar")
    public Ejecucion revisar(Authentication auth, @PathVariable Long id, @RequestBody RevisarRequest request) {
        User revisor = accessGuard.requireTabAccessWithoutMasterBypass(auth, ModuloSistema.CALIDAD,
                "REVISION_LIBERACION_LOTES", 2, "No tiene permiso para revisar despejes de línea.");
        return despejes.revisar(id, request, revisor);
    }
    @GetMapping("/ordenes-produccion/{id}")
    public List<Ejecucion> ordenProduccion(Authentication auth, @PathVariable int id) {
        return despejes.consultarOrdenProduccion(id, actor(auth));
    }
    @GetMapping("/ordenes-fabricacion/{id}")
    public List<Ejecucion> ordenFabricacion(Authentication auth, @PathVariable Long id) {
        return despejes.consultarOrdenFabricacion(id, actor(auth));
    }
    private User configurar(Authentication auth) {
        return accessGuard.requireTabAccess(auth, ModuloSistema.SEGUIMIENTO_PRODUCCION,
                "CONSULTA_AREAS_OPERATIVAS", 1, "No tiene permiso para configurar despejes de área.");
    }
    private User actor(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null)
            throw new ResponseStatusException(UNAUTHORIZED, "No autenticado.");
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuario no encontrado."));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalido(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Despeje no válido", error.getMessage()));
    }
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(NoSuchElementException error) {
        return ResponseEntity.status(NOT_FOUND).body(new ErrorResponse("Despeje no encontrado", error.getMessage()));
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> conflictoConcurrente(OptimisticLockingFailureException error) {
        return ResponseEntity.status(CONFLICT).body(new ErrorResponse("Cambio concurrente", "Actualice el formulario antes de guardar."));
    }
}
