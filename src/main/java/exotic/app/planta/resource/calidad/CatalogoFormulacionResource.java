package exotic.app.planta.resource.calidad;

import exotic.app.planta.model.producto.dto.CatalogoFormulacionDTO.Formulacion;
import exotic.app.planta.model.producto.dto.CatalogoFormulacionDTO.ProductoResumen;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.productos.CatalogoFormulacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calidad/catalogo-productos")
@RequiredArgsConstructor
public class CatalogoFormulacionResource {
    public static final String TAB_CATALOGO = "CATALOGO_FORMULACION";
    private final CatalogoFormulacionService service;
    private final ModuleTabAccessGuard accessGuard;

    @GetMapping
    public ResponseEntity<Page<ProductoResumen>> buscar(
            Authentication authentication,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireAccess(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.buscar(search, tipo, page, size));
    }

    @GetMapping("/{productoId}/formulacion")
    public ResponseEntity<Formulacion> detalle(Authentication authentication, @PathVariable String productoId) {
        requireAccess(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.detalle(productoId));
    }

    private void requireAccess(Authentication authentication) {
        accessGuard.requireTabAccess(authentication, ModuloSistema.CALIDAD, TAB_CATALOGO, 1,
                "No tiene permiso para consultar Catálogo y formulación.");
    }
}
