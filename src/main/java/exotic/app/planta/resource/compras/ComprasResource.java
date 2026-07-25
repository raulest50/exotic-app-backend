package exotic.app.planta.resource.compras;

import exotic.app.planta.model.compras.FacturaCompra;
import exotic.app.planta.model.compras.ItemFacturaCompra;
import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.dto.UpdateEstadoOrdenCompraRequest;
import exotic.app.planta.model.compras.dto.search.SearchOrdenCompraRequest;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.compras.ComprasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/compras")
@RequiredArgsConstructor
@Slf4j
public class ComprasResource {

    private static final String TAB_REPORTES_ORDENES_COMPRA = "REPORTES_ORDENES_COMPRA";

    /**
     * Compras
     */
    private final ComprasService compraService;
    private final UserRepository userRepository;


    @GetMapping("/byProveedorAndDate")
    public ResponseEntity<Page<FacturaCompra>> getComprasByProveedorAndDate(
            @RequestParam String proveedorId,
            @RequestParam String date1,
            @RequestParam String date2,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        Page<FacturaCompra> compras = compraService.getComprasByProveedorAndDate(proveedorId, date1, date2, page, size);
        return ResponseEntity.ok(compras);
    }

    @GetMapping("/{compraId}/items")
    public ResponseEntity<List<ItemFacturaCompra>> getItemsCompra(@PathVariable int compraId) {
        List<ItemFacturaCompra> items = compraService.getItemsByCompraId(compraId);
        return ResponseEntity.ok(items);
    }


    /**
     * Ordenes de Compra
     */
    @PostMapping("/save_orden_compra")
    public ResponseEntity<OrdenCompraMateriales> saveOrdenCompra(
            @Valid @RequestBody OrdenCompraMateriales ordenCompraMateriales,
            Authentication authentication
    ) {
        User usuarioCreador = requireAuthenticatedUser(authentication);
        OrdenCompraMateriales savedOrdenCompraMateriales = compraService.saveOrdenCompra(
                ordenCompraMateriales,
                usuarioCreador
        );
        return ResponseEntity.created(URI.create("/compras/save_orden_compra/" + savedOrdenCompraMateriales.getOrdenCompraId()))
                .body(savedOrdenCompraMateriales);
    }


    /**
     * GET endpoint to search OrdenCompraMateriales by date range and estados.
     * Example: /compras/ordenes?date1=2025-02-01&date2=2025-02-10&estados=0,1,2&page=0&size=10
     */
    @GetMapping("/search_ordenes_by_date_estado")
    public ResponseEntity<Page<OrdenCompraMateriales>> getOrdenesCompra(@Valid @ModelAttribute SearchOrdenCompraRequest request) {
        Page<OrdenCompraMateriales> ordenes = compraService.getOrdenesCompraByDateAndEstado(
                request.getDate1(),
                request.getDate2(),
                request.getEstados(),
                request.getPage(),
                request.getSize(),
                request.getProveedorId()
        );
        return ResponseEntity.ok(ordenes);
    }

    @PutMapping("/orden_compra/{ordenCompraId}/cancel")
    public ResponseEntity<OrdenCompraMateriales> cancelOrdenCompra(@PathVariable int ordenCompraId) {
        OrdenCompraMateriales updated = compraService.cancelOrdenCompra(ordenCompraId);
        return ResponseEntity.ok(updated);
    }


    @PutMapping(value = "/orden_compra/{ordenCompraId}/updateEstado", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateEstadoOrdenCompra(
            @PathVariable int ordenCompraId,
            @RequestPart("request") UpdateEstadoOrdenCompraRequest request,
            @RequestPart(value = "OCMpdf", required = false) MultipartFile pdfAttachment,
            Authentication authentication
    ) {
        User usuarioActor = requireAuthenticatedUser(authentication);
        if (request.getNewEstado() == 1) {
            requireReleaseAccess(usuarioActor);
        }

        try {
            // Si se proporciona un archivo, asignarlo al request
            if (pdfAttachment != null) {
                request.setOCMpdf(pdfAttachment);
            }

            OrdenCompraMateriales updated = compraService.updateEstadoOrdenCompra(
                    ordenCompraId,
                    request,
                    usuarioActor
            );
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            // Devolver un error con el mensaje específico
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private User requireAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED, "No autenticado");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuario no encontrado"));
    }

    private void requireReleaseAccess(User user) {
        if (isMasterLike(user.getUsername())) {
            return;
        }

        int nivel = UserAccessEvaluator
                .tabNivel(user, ModuloSistema.COMPRAS, TAB_REPORTES_ORDENES_COMPRA)
                .orElse(0);
        if (nivel < 2) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Se requiere nivel 2 o superior en Reportes Ordenes de Compra para liberar la orden."
            );
        }
    }

    private static boolean isMasterLike(String username) {
        return "master".equalsIgnoreCase(username) || "super_master".equalsIgnoreCase(username);
    }


    @GetMapping("/orden_by_id")
    public ResponseEntity<OrdenCompraMateriales> getOrdenCompraByOrdenCompraId
            (@RequestParam Integer ordenCompraId, @RequestParam(defaultValue = "2") int estado) {
        try {
            OrdenCompraMateriales orden = compraService.getOrdenCompraByOrdenCompraIdAndEstado(ordenCompraId, estado);
            return ResponseEntity.ok(orden);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }


    @PutMapping("/update_orden_compra/{ordenCompraId}")
    public ResponseEntity<?> updateOrdenCompra(
            @PathVariable int ordenCompraId,
            @Valid @RequestBody OrdenCompraMateriales ordenCompraMateriales) {
        try {
            // Asegurarse de que el ID en el path coincida con el ID en el objeto
            if (ordenCompraId != ordenCompraMateriales.getOrdenCompraId()) {
                return ResponseEntity.badRequest().body(Map.of("error", 
                    "El ID en la URL no coincide con el ID en el objeto de la orden de compra"));
            }

            OrdenCompraMateriales updated = compraService.updateOrdenCompra(ordenCompraId, ordenCompraMateriales);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    @PutMapping("/orden_compra/{ordenCompraId}/close")
    public ResponseEntity<?> closeOrdenCompra(@PathVariable int ordenCompraId) {
        try {
            OrdenCompraMateriales ordenCerrada = compraService.closeOrdenCompra(ordenCompraId);
            return ResponseEntity.ok(ordenCerrada);
        } catch (RuntimeException e) {
            log.error("Error al cerrar orden de compra ID {}: {}", ordenCompraId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

}
