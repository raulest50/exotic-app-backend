package exotic.app.planta.resource.inventarios;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.inventarios.dto.ReporteHyLRequestDTO;
import exotic.app.planta.model.produccion.dto.CierreProduccionRequestDTO;
import exotic.app.planta.model.produccion.dto.CierreProduccionResponseDTO;
import exotic.app.planta.model.produccion.dto.ReporteProduccionPendientesDTO;
import exotic.app.planta.model.produccion.dto.ReporteProduccionPendientesResumenDTO;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.inventarios.ReporteHyLService;
import exotic.app.planta.service.produccion.CierreProduccionConflictException;
import exotic.app.planta.service.produccion.CierreProduccionService;
import exotic.app.planta.service.produccion.ReporteProduccionLoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Locale;

@RestController
@RequestMapping("/ingresos_terminados_almacen")
@RequiredArgsConstructor
public class IngresoTerminadosAlmacenResource {

    private static final String TAB_INGRESO_PRODUCTO_TERMINADO = "INGRESO_PRODUCTO_TERMINADO";

    private final ReporteHyLService reporteHyLService;
    private final ReporteProduccionLoteService reporteProduccionLoteService;
    private final CierreProduccionService cierreProduccionService;
    private final UserRepository userRepository;

    @GetMapping("/pendientes/resumen")
    public ResponseEntity<ReporteProduccionPendientesResumenDTO> resumirPendientes(
            Authentication authentication) {
        requireTabAccess(authentication, 1);
        return ResponseEntity.ok(reporteProduccionLoteService.resumirPendientes());
    }

    @GetMapping("/pendientes")
    public ResponseEntity<ReporteProduccionPendientesDTO> consultarPendientes(
            Authentication authentication,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fecha) {
        requireTabAccess(authentication, 1);
        return ResponseEntity.ok(reporteProduccionLoteService.consultarPendientes(fecha));
    }

    @PostMapping("/reporte-hyl")
    public ResponseEntity<byte[]> descargarReporteHyL(
            Authentication authentication,
            @RequestBody ReporteHyLRequestDTO request) {
        requireTabAccess(authentication, 1);
        LocalDate fechaEfectiva = request != null && request.getFechaReporte() != null
                ? request.getFechaReporte()
                : AppTime.today();
        byte[] excel = reporteHyLService.generarReporteXls(request);
        String filename = "reporte_hyl_" + fechaEfectiva.toString().replace("-", "") + ".xls";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .body(excel);
    }

    @PostMapping("/cierres")
    public ResponseEntity<CierreProduccionResponseDTO> confirmarCierre(
            Authentication authentication,
            @Valid @RequestBody CierreProduccionRequestDTO request) {
        User actor = requireTabAccess(authentication, 2);
        return ResponseEntity.ok(cierreProduccionService.confirmar(actor, request));
    }

    private User requireTabAccess(Authentication authentication, int minNivel) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if (isMasterLike(user.getUsername())) {
            return user;
        }

        int nivel = UserAccessEvaluator.tabNivel(
                user,
                ModuloSistema.TRANSACCIONES_ALMACEN,
                TAB_INGRESO_PRODUCTO_TERMINADO
        ).orElse(0);
        if (nivel < minNivel) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tiene permisos suficientes para el reporte de producto terminado.");
        }
        return user;
    }

    private boolean isMasterLike(String username) {
        if (username == null) {
            return false;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return "master".equals(normalized) || "super_master".equals(normalized);
    }

    @ExceptionHandler(CierreProduccionConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(CierreProduccionConflictException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Los reportes cambiaron", error.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleBusinessError(RuntimeException error) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("No fue posible procesar el cierre", error.getMessage()));
    }
}
