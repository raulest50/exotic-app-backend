package exotic.app.planta.resource.organigrama;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.organigrama.Cargo;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.organigrama.CargoOrganigramaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/organigrama")
@RequiredArgsConstructor
@Slf4j
public class CargoOrganigramaResource {

    private static final String TAB_ORGANIGRAMA = "ORGANIGRAMA";

    private final CargoOrganigramaService cargoOrganigramaService;
    private final ObjectMapper objectMapper;
    private final ModuleTabAccessGuard accessGuard;

    /**
     * Endpoint para obtener todos los cargos disponibles
     * @return Lista de todos los cargos
     */
    @GetMapping
    public ResponseEntity<List<Cargo>> getAllCargos(Authentication authentication) {
        requireAccess(authentication, 1);
        List<Cargo> cargos = cargoOrganigramaService.getAllCargos();
        return ResponseEntity.ok(cargos);
    }

    /**
     * Endpoint para guardar o actualizar un cargo con su manual de funciones
     * @param cargoJson JSON con los datos del cargo
     * @param manualFuncionesFile Archivo PDF del manual de funciones (opcional)
     * @return El cargo guardado
     */
    @PostMapping(value = "/save_mfunciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> saveCargo(
            Authentication authentication,
            @RequestPart("cargo") String cargoJson,
            @RequestPart(value = "manualFuncionesFile", required = false) MultipartFile manualFuncionesFile
    ) {
        requireAccess(authentication, 2);
        try {
            Cargo cargo = objectMapper.readValue(cargoJson, Cargo.class);
            Cargo saved = cargoOrganigramaService.saveCargoWithManualFunciones(cargo, manualFuncionesFile);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al guardar el cargo: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Error al procesar el archivo del manual de funciones: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al procesar el archivo: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado al guardar el cargo: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al guardar el cargo: " + e.getMessage()));
        }
    }

    /**
     * Endpoint para guardar cambios en el organigrama (elimina todos los cargos y guarda los nuevos)
     * @param cargos Lista de cargos a guardar
     * @return Lista de cargos guardados
     */
    @PostMapping("/save_changes_organigrama")
    public ResponseEntity<Object> saveChangesOrganigrama(
            Authentication authentication,
            @RequestBody List<Cargo> cargos
    ) {
        requireAccess(authentication, 2);
        try {
            List<Cargo> saved = cargoOrganigramaService.saveChangesOrganigrama(cargos);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Error al guardar cambios en el organigrama: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al guardar cambios en el organigrama: " + e.getMessage()));
        }
    }

    /**
     * Endpoint para descargar el manual de funciones de un cargo
     * @param cargoId ID del cargo
     * @return Archivo PDF del manual de funciones
     */
    @GetMapping("/{cargoId}/manual-funciones")
    public ResponseEntity<byte[]> downloadManualFunciones(
            Authentication authentication,
            @PathVariable String cargoId
    ) {
        requireAccess(authentication, 1);
        try {
            // Obtener el cargo
            Cargo cargo = cargoOrganigramaService.getCargoById(cargoId);

            // Verificar si tiene manual de funciones
            if (cargo.getUrlDocManualFunciones() == null || cargo.getUrlDocManualFunciones().isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Leer el archivo
            Path filePath = Paths.get(cargo.getUrlDocManualFunciones());
            if (!Files.exists(filePath)) {
                log.error("El archivo del manual de funciones no existe en la ruta: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            byte[] pdfBytes = Files.readAllBytes(filePath);

            // Configurar headers para la descarga
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "manual_funciones_" + cargo.getTituloCargo() + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("Error al buscar el cargo: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("Error al leer el archivo del manual de funciones: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Error inesperado al descargar el manual de funciones: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
}
