package exotic.app.planta.resource.organigrama;

import exotic.app.planta.model.organigrama.dto.CargoOrganigramaResponse;
import exotic.app.planta.model.organigrama.dto.GuardarOrganigramaRequest;
import exotic.app.planta.model.organigrama.dto.ManualFuncionesUrlRequest;
import exotic.app.planta.model.organigrama.dto.OrganigramaSnapshotResponse;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.organigrama.CargoOrganigramaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/organigrama")
@RequiredArgsConstructor
public class CargoOrganigramaResource {

    private static final String TAB_ORGANIGRAMA = "ORGANIGRAMA";

    private final CargoOrganigramaService cargoOrganigramaService;
    private final ModuleTabAccessGuard accessGuard;

    @GetMapping
    public ResponseEntity<OrganigramaSnapshotResponse> getSnapshot(Authentication authentication) {
        requireAccess(authentication, 1);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(cargoOrganigramaService.getSnapshot());
    }

    @PutMapping
    public OrganigramaSnapshotResponse saveSnapshot(
            Authentication authentication,
            @Valid @RequestBody GuardarOrganigramaRequest request
    ) {
        requireAccess(authentication, 2);
        return cargoOrganigramaService.saveSnapshot(request, authentication.getName());
    }

    @PutMapping(value = "/cargos/{cargoId}/manual-funciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CargoOrganigramaResponse uploadManualFunciones(
            Authentication authentication,
            @PathVariable String cargoId,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        requireAccess(authentication, 2);
        return cargoOrganigramaService.uploadManualFunciones(cargoId, file);
    }

    @PutMapping("/cargos/{cargoId}/manual-funciones-url")
    public CargoOrganigramaResponse setManualFuncionesUrl(
            Authentication authentication,
            @PathVariable String cargoId,
            @Valid @RequestBody ManualFuncionesUrlRequest request
    ) {
        requireAccess(authentication, 2);
        return cargoOrganigramaService.setManualFuncionesUrl(cargoId, request.getUrl());
    }

    @DeleteMapping("/cargos/{cargoId}/manual-funciones")
    public CargoOrganigramaResponse clearManualFunciones(
            Authentication authentication,
            @PathVariable String cargoId
    ) {
        requireAccess(authentication, 2);
        return cargoOrganigramaService.clearManualFunciones(cargoId);
    }

    @GetMapping("/cargos/{cargoId}/manual-funciones")
    public ResponseEntity<byte[]> downloadManualFunciones(
            Authentication authentication,
            @PathVariable String cargoId
    ) throws IOException {
        requireAccess(authentication, 1);
        CargoOrganigramaService.ManualDownload manual = cargoOrganigramaService.getManualFunciones(cargoId);
        if (manual.isRedirect()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(manual.redirectUri())
                    .build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDispositionSupport.attachment(manual.filename())
                )
                .header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                .body(manual.content());
    }

    private void requireAccess(Authentication authentication, int minNivel) {
        accessGuard.requireTabAccess(
                authentication,
                ModuloSistema.ORGANIGRAMA,
                TAB_ORGANIGRAMA,
                minNivel,
                "No tiene permisos suficientes para administrar el organigrama."
        );
    }

    private static final class ContentDispositionSupport {
        private ContentDispositionSupport() {
        }

        private static String attachment(String filename) {
            return org.springframework.http.ContentDisposition.attachment()
                    .filename(filename)
                    .build()
                    .toString();
        }
    }
}
