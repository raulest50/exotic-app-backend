package exotic.app.planta.resource.inventarios;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.dto.recepcion.SearchOCMFilterDTO;
import exotic.app.planta.model.inventarios.dto.ConsolidadoOCMResponseDTO;
import exotic.app.planta.model.inventarios.dto.MovimientoDetalleDTO;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.service.inventarios.IngresoAlmacenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;

import java.util.Map;

@RestController
@RequestMapping("/ingresos_almacen")
@RequiredArgsConstructor
public class IngresoAlmacenResource {

    private final IngresoAlmacenService ingresoAlmacenService;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;

    @GetMapping("ocms_pendientes_ingreso")
    public ResponseEntity<Page<OrdenCompraMateriales>> ConsultarOCMsPendientesRecepcion(
            @RequestParam int page,
            @RequestParam int size,
            @ModelAttribute SearchOCMFilterDTO filterDTO
    ) {
        Page<OrdenCompraMateriales> pageResult = ingresoAlmacenService.consultarOCMsPendientesRecepcion(
                filterDTO,
                page,
                size
        );
        return ResponseEntity.ok(pageResult);
    }


    /**
     * Este metodo es para listar todas las transacciones de almacen relacionadas con una OCM.
     * @return
     */
    @GetMapping("/consultar_transin_de_ocm")
    public ResponseEntity<?> ConsultarTransaccionesAlmacenDeOCM(
            @RequestParam int page,
            @RequestParam int size,
            @RequestParam int ordenCompraId
    ){
        try {
            List<TransaccionAlmacen> transacciones = ingresoAlmacenService.consultarTransaccionesAlmacenDeOCM(
                    ordenCompraId, page, size
            );
            return ResponseEntity.ok(transacciones);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


    /**
     * Para asociar un ingreso de materiales, o transaccion de almacen de ingreso, a una OCM.
     * @param ordenCompraMateriales
     * @return
     */
    @PostMapping("/registrar_tran_ingreso_ocm")
    public ResponseEntity<?> RegistrarTransaccionAlmacenIngresoOCM(
            @RequestBody OrdenCompraMateriales ordenCompraMateriales

    ){
        UnsupportedOperationException unsupportedOperationException = new UnsupportedOperationException();
        return ResponseEntity.status(405).body(unsupportedOperationException.getMessage());
    }

    /**
     * Obtiene los movimientos/items de una transacción de almacén específica.
     * Incluye información detallada del producto y lote asociados.
     *
     * @param transaccionId ID de la transacción de almacén
     * @return Lista de movimientos con sus detalles
     */
    @GetMapping("/transaccion/{transaccionId}/movimientos")
    public ResponseEntity<List<MovimientoDetalleDTO>> obtenerMovimientosPorTransaccion(
            @PathVariable int transaccionId) {
        try {
            List<MovimientoDetalleDTO> movimientos = ingresoAlmacenService.obtenerMovimientosPorTransaccion(transaccionId);
            return ResponseEntity.ok(movimientos);
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Obtiene el consolidado de todos los materiales recibidos para una OCM.
     * Agrupa materiales por producto y suma cantidades, manteniendo información de lotes.
     *
     * @param ordenCompraId ID de la orden de compra
     * @return Consolidado de materiales con sus lotes
     */
    @GetMapping("/ocm/{ordenCompraId}/consolidado-materiales")
    public ResponseEntity<ConsolidadoOCMResponseDTO> obtenerConsolidadoMaterialesPorOCM(
            @PathVariable int ordenCompraId) {
        try {
            ConsolidadoOCMResponseDTO consolidado = ingresoAlmacenService.obtenerConsolidadoMaterialesPorOCM(ordenCompraId);
            return ResponseEntity.ok(consolidado);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

}
