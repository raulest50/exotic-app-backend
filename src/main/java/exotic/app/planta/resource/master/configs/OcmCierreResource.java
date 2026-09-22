package exotic.app.planta.resource.master.configs;

import exotic.app.planta.model.compras.dto.OcmCierreDTOs.*;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.service.compras.OcmCierreBatchService;
import exotic.app.planta.service.compras.OcmCierreConfigService;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/super-master-directives/ocm-cierre")
@RequiredArgsConstructor
public class OcmCierreResource {
    private final OcmCierreConfigService configuracion;
    private final OcmCierreBatchService operaciones;
    private final MasterDirectiveService directivas;

    @GetMapping("/config")
    public Config consultar() {
        return configuracion.consultar();
    }

    @PutMapping("/config")
    public Config actualizar(@Valid @RequestBody ConfigWrite request, Authentication authentication) {
        exigirAdministrador(authentication);
        return configuracion.actualizar(request);
    }

    @GetMapping("/completas")
    public List<Candidata> previsualizar(Authentication authentication) {
        exigirAdministrador(authentication);
        return operaciones.previsualizar();
    }

    @PostMapping("/cerrar-completas")
    public ResultadoCierre cerrar(@Valid @RequestBody CierreWrite request, Authentication authentication) {
        exigirAdministrador(authentication);
        return operaciones.cerrarSeleccionadas(request.ordenCompraIds(), authentication.getName());
    }

    private void exigirAdministrador(Authentication authentication) {
        String username = authentication != null && authentication.isAuthenticated() && authentication.getName() != null
                ? authentication.getName().trim().toLowerCase(Locale.ROOT) : "";
        if ("super_master".equals(username)) return;
        if ("master".equals(username) && directivas.getBooleanDirectiveValue(
                MasterDirectiveKeys.ENABLE_MASTER_SUPERMASTER_DIRECTIVES_ACCESS, true)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para administrar el cierre de OCM.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> solicitudInvalida(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
    }
}
