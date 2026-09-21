package exotic.app.planta.resource.controles;

import exotic.app.planta.model.controles.dto.ControlRutaResumen;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.controles.ControlRutaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/controles/ruta")
@RequiredArgsConstructor
public class ControlRutaResource {
    private final ControlRutaService service;
    private final ModuleTabAccessGuard accessGuard;

    @GetMapping
    public List<ControlRutaResumen> listar(Authentication authentication,
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) String productoId) {
        accessGuard.requireAnyTabAccess(authentication, Map.of(
                        ModuloSistema.PRODUCCION, Map.of("PARAMETROS_POR_CATEGORIA", 1, "PLANES_CONTROL_PROCESO", 1),
                        ModuloSistema.CALIDAD, Map.of("PLANES_CONTROL_CALIDAD", 1)),
                "No tiene permisos para consultar controles de la ruta.");
        return service.listar(categoriaId, productoId);
    }
}
