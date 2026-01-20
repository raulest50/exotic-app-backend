package exotic.app.planta.resource.inventarios;

import exotic.app.planta.model.inventarios.dto.InventarioExcelRequestDTO;
import exotic.app.planta.model.inventarios.dto.KardexMovimientosPageDTO;
import exotic.app.planta.model.inventarios.dto.KardexMovimientosRequestDTO;
import exotic.app.planta.service.inventarios.InventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventario")
@RequiredArgsConstructor
public class InventarioResource {

    private final InventarioService inventarioService;

    @PostMapping("/exportar-excel")
    public ResponseEntity<byte[]> exportarExcel(@RequestBody InventarioExcelRequestDTO dto) {
        byte[] excel = inventarioService.generateInventoryExcel(dto);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventario.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @PostMapping("/kardex/movimientos")
    public ResponseEntity<?> kardexMovimientos(@RequestBody KardexMovimientosRequestDTO dto) {
        try {
            KardexMovimientosPageDTO resp = inventarioService.getKardexMovimientosPage(dto);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/kardex/exportar-excel")
    public ResponseEntity<?> exportarKardexExcel(@RequestBody KardexMovimientosRequestDTO dto) {
        try {
            byte[] excel = inventarioService.exportKardexExcel(dto);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"kardex.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excel);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
