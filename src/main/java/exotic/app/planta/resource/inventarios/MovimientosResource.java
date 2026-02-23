package exotic.app.planta.resource.inventarios;


import exotic.app.planta.model.inventarios.dto.AjusteInventarioDTO;
import exotic.app.planta.model.inventarios.dto.BackflushNoPlanificadoDTO;
import exotic.app.planta.model.inventarios.dto.BackflushMultipleNoPlanificadoDTO;
import exotic.app.planta.model.inventarios.dto.FiltroHistorialTransaccionAlmacenDTO;
import exotic.app.planta.model.inventarios.dto.IngresoOCM_DTA;
import exotic.app.planta.model.inventarios.dto.MovimientoExcelRequestDTO;
import exotic.app.planta.model.inventarios.dto.TransaccionAlmacenResponseDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.dto.ProductoStockDTO;
import exotic.app.planta.service.inventarios.MovimientosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/movimientos")
@RequiredArgsConstructor
@Slf4j
public class MovimientosResource {

    private final MovimientosService movimientoService;

    /**
     * Endpoint para buscar productos con stock disponible.
     * Permite filtrar productos según un término de búsqueda y tipo de búsqueda específico.
     * 
     * @param searchTerm Término de búsqueda para filtrar productos
     * @param tipoBusqueda Tipo de búsqueda (por nombre, código, etc.)
     * @param page Número de página para paginación (base 0)
     * @param size Tamaño de página para paginación
     * @return Página de productos con información de stock
     */
    @GetMapping("/search_products_with_stock")
    public ResponseEntity<Page<ProductoStockDTO>> searchProductsWithStock(
            @RequestParam String searchTerm,
            @RequestParam String tipoBusqueda,
            @RequestParam int page,
            @RequestParam int size
    ) {
        Page<ProductoStockDTO> result = movimientoService.searchProductsWithStock(searchTerm, tipoBusqueda, page, size);
        return ResponseEntity.ok().body(result);
    }

    /**
     * Endpoint para exportar movimientos de inventario a un archivo Excel.
     * Genera un reporte de movimientos según los criterios especificados en el DTO.
     * 
     * @param dto DTO con los parámetros para filtrar los movimientos a exportar
     * @return Archivo Excel con los movimientos filtrados
     */
    @PostMapping("/exportar-movimientos-excel")
    public ResponseEntity<byte[]> exportMovimientosExcel(@RequestBody MovimientoExcelRequestDTO dto) {
        byte[] excel = movimientoService.generateMovimientosExcel(dto);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"movimientos.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    /**
     * Endpoint para obtener los movimientos de inventario de un producto específico.
     * Devuelve una lista paginada de movimientos ordenados por fecha descendente.
     * 
     * @param productoId ID del producto para filtrar los movimientos
     * @param page Número de página para paginación (base 0)
     * @param size Tamaño de página para paginación
     * @return Página de movimientos del producto especificado
     */
    @GetMapping("/get_movimientos_by_producto")
    public ResponseEntity<Page<Movimiento>> getMovimientosByProducto(
            @RequestParam String productoId,
            @RequestParam int page,
            @RequestParam int size
    ) {
        Page<Movimiento> movimientos = movimientoService.getMovimientosByProductoId(productoId, page, size);
        return ResponseEntity.ok().body(movimientos);
    }



    @PostMapping("/ajustes")
    public ResponseEntity<TransaccionAlmacen> createAjusteInventario(@RequestBody AjusteInventarioDTO ajusteInventarioDTO) {
        // Llamar directamente al servicio con el DTO original
        TransaccionAlmacen transaccion = movimientoService.createAjusteInventario(ajusteInventarioDTO);
        return ResponseEntity.created(java.net.URI.create("/movimientos/transaccion/" + transaccion.getTransaccionId()))
                .body(transaccion);
    }

    /**
     * Endpoint para registrar un documento de ingreso de materiales por orden de compra.
     * Permite adjuntar un archivo como soporte documental de la transacción.
     * 
     * @param docIngresoDTO DTO con la información del ingreso de materiales
     * @param file Archivo adjunto como soporte documental (factura, guía de remisión, etc.)
     * @return Respuesta con el resultado de la operación
     */
    @PostMapping(value = "/save_doc_ingreso_oc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createDocIngreso(
            @RequestPart("docIngresoDTA") IngresoOCM_DTA docIngresoDTO,
            @RequestPart("file") MultipartFile file) {
        log.info("Recibiendo solicitud de ingreso OCM. userId: {}, ordenCompraId: {}, file: {}", 
                docIngresoDTO.getUserId(), 
                docIngresoDTO.getOrdenCompraMateriales() != null ? docIngresoDTO.getOrdenCompraMateriales().getOrdenCompraId() : "null",
                file.getOriginalFilename());
        log.debug("Payload completo - IngresoOCM_DTA: userId={}, observaciones={}, transaccionAlmacen presente={}", 
                docIngresoDTO.getUserId(),
                docIngresoDTO.getObservaciones(),
                docIngresoDTO.getTransaccionAlmacen() != null);
        return movimientoService.createDocIngreso(docIngresoDTO, file);
    }



    /**
     * Endpoint para crear un backflush no planificado (sin orden de producción).
     * Verifica la directiva "Permitir Backflush No Planificado" antes de permitir la operación.
     */
    @PostMapping("/backflush-no-planificado")
    public ResponseEntity<?> createBackflushNoPlanificado(@RequestBody BackflushNoPlanificadoDTO backflushDTO) {
        TransaccionAlmacen transaccion = movimientoService.createBackflushNoPlanificado(backflushDTO);
        return ResponseEntity.created(java.net.URI.create("/movimientos/transaccion/" + transaccion.getTransaccionId()))
            .body(transaccion);
    }


    /**
     * Endpoint para crear múltiples backflush no planificados (sin orden de producción).
     * Verifica la directiva "Permitir Backflush No Planificado" antes de permitir la operación.
     * Permite especificar lotes para cada producto terminado.
     */
    @PostMapping("/backflush-multiple-no-planificado")
    public ResponseEntity<?> createBackflushMultipleNoPlanificado(
            @RequestBody BackflushMultipleNoPlanificadoDTO backflushDTO) {
        TransaccionAlmacen transaccion = movimientoService.createBackflushMultipleNoPlanificado(backflushDTO);
        return ResponseEntity.created(java.net.URI.create("/movimientos/transaccion/" + transaccion.getTransaccionId()))
            .body(transaccion);
    }

    /**
     * Busca transacciones de almacen con filtros de tipo de entidad causante y rango de fechas.
     * Retorna DTOs paginados para evitar problemas de serializacion JSON.
     */
    @PostMapping("/historial_transacciones_almacen")
    public ResponseEntity<Page<TransaccionAlmacenResponseDTO>> buscarHistorialTransacciones(
            @RequestBody FiltroHistorialTransaccionAlmacenDTO filtro) {
        Page<TransaccionAlmacenResponseDTO> resultados = movimientoService.buscarHistorialTransacciones(filtro);
        return ResponseEntity.ok(resultados);
    }



}
