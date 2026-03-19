package exotic.app.planta.resource.inventarios;

import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.IngresoTerminadoConsultaResponseDTO;
import exotic.app.planta.model.inventarios.dto.IngresoTerminadoRequestDTO;
import exotic.app.planta.service.inventarios.IngresoTerminadosAlmacenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/ingresos_terminados_almacen")
@RequiredArgsConstructor
@Slf4j
public class IngresoTerminadosAlmacenResource {

    private final IngresoTerminadosAlmacenService ingresoTerminadosAlmacenService;

    /**
     * Busca una OrdenProduccion activa por su loteAsignado exacto y retorna los datos
     * del producto terminado junto con el tamaño de lote esperado según la categoría.
     *
     * @param loteAsignado Número de lote de la orden de producción
     * @return 200 con IngresoTerminadoConsultaResponseDTO, 404 si no existe, 409 si ya está terminada/cancelada
     */
    @GetMapping("/buscar-op-por-lote")
    public ResponseEntity<IngresoTerminadoConsultaResponseDTO> buscarOpPorLote(
            @RequestParam String loteAsignado) {
        IngresoTerminadoConsultaResponseDTO resultado =
                ingresoTerminadosAlmacenService.buscarOpPorLote(loteAsignado);
        return ResponseEntity.ok(resultado);
    }

    /**
     * Registra el ingreso de producto terminado al almacén general, crea la TransaccionAlmacen
     * con su Movimiento BACKFLUSH y cierra la OrdenProduccion (estadoOrden = 2).
     *
     * @param dto Datos del ingreso: ordenProduccionId, cantidadIngresada, fechaVencimiento, observaciones
     * @return 201 Created con Location apuntando a la transacción creada
     */
    @PostMapping("/registrar")
    public ResponseEntity<TransaccionAlmacen> registrarIngresoTerminado(
            @RequestBody IngresoTerminadoRequestDTO dto) {
        TransaccionAlmacen transaccion = ingresoTerminadosAlmacenService.registrarIngresoTerminado(dto);
        URI location = URI.create("/movimientos/transaccion/" + transaccion.getTransaccionId());
        return ResponseEntity.created(location).body(transaccion);
    }
}
