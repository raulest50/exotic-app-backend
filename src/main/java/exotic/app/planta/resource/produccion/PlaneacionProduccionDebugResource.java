package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.produccion.dto.PlaneacionExcelDebugResponseDTO;
import exotic.app.planta.model.produccion.dto.PlaneacionTerminadosDebugResponseDTO;
import exotic.app.planta.service.produccion.PlaneacionExcelDebugService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/planeacion_produccion/debug")
@RequiredArgsConstructor
public class PlaneacionProduccionDebugResource {

    private final PlaneacionExcelDebugService planeacionExcelDebugService;

    @PostMapping("/excel-structure")
    public ResponseEntity<?> debugExcelStructure(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clientErrors", required = false) List<String> clientErrors,
            @RequestParam(value = "clientExpectedHeadersVersion", required = false) String clientExpectedHeadersVersion
    ) {
        String username = authentication != null ? authentication.getName() : null;
        if (!isMasterLike(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only master or super_master can use this debug endpoint"));
        }

        PlaneacionExcelDebugResponseDTO response = planeacionExcelDebugService.inspectExcel(
                file,
                username,
                clientErrors,
                clientExpectedHeadersVersion
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/asociacion-terminados")
    public ResponseEntity<?> debugTerminadosAssociation(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clientContext", required = false) String clientContext,
            @RequestParam(value = "clientExpectedHeadersVersion", required = false) String clientExpectedHeadersVersion
    ) {
        String username = authentication != null ? authentication.getName() : null;
        if (!isMasterLike(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only master or super_master can use this debug endpoint"));
        }

        PlaneacionTerminadosDebugResponseDTO response = planeacionExcelDebugService.inspectTerminadosAssociation(
                file,
                username,
                clientContext,
                clientExpectedHeadersVersion
        );
        return ResponseEntity.ok(response);
    }

    private boolean isMasterLike(String username) {
        if (username == null) {
            return false;
        }
        String normalized = username.trim().toLowerCase();
        return "master".equals(normalized) || "super_master".equals(normalized);
    }
}
