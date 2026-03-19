package exotic.app.planta.resource.commons;

import exotic.app.planta.service.commons.ExportacionMaterialService;
import exotic.app.planta.service.commons.ExportacionProveedorService;
import exotic.app.planta.service.commons.ExportacionTerminadoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exportacion-datos")
@RequiredArgsConstructor
public class ExportacionDatosResource {

    private final ExportacionMaterialService exportacionMaterialService;
    private final ExportacionTerminadoService exportacionTerminadoService;
    private final ExportacionProveedorService exportacionProveedorService;

    @GetMapping("/materiales/excel")
    public ResponseEntity<byte[]> exportarMaterialesExcel() {
        byte[] excel = exportacionMaterialService.exportarMaterialesExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exportacion_materiales.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/terminados/excel")
    public ResponseEntity<byte[]> exportarTerminadosExcel() {
        byte[] excel = exportacionTerminadoService.exportarTerminadosExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exportacion_terminados.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/terminados/json-con-insumos")
    public ResponseEntity<byte[]> exportarTerminadosJsonConInsumos() {
        byte[] json = exportacionTerminadoService.exportarTerminadosJsonConInsumos();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exportacion_terminados_con_insumos.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @GetMapping("/proveedores/json-con-contactos")
    public ResponseEntity<byte[]> exportarProveedoresJsonConContactos() {
        byte[] json = exportacionProveedorService.exportarProveedoresJsonConContactos();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exportacion_proveedores.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }
}
