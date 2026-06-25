package exotic.app.planta.resource.inventarios;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.IngresoMasivoRequestDTO;
import exotic.app.planta.model.inventarios.dto.IngresoMasivoResponseDTO;
import exotic.app.planta.model.inventarios.dto.IngresoTerminadoConsultaResponseDTO;
import exotic.app.planta.model.inventarios.dto.IngresoTerminadoRequestDTO;
import exotic.app.planta.service.inventarios.IngresoTerminadosAlmacenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
     * Temporalmente en desuso: el flujo activo del asistente genera reporte consolidado
     * por terminado sin cierre de OP. Se conserva por posible reintegración del workflow
     * por lote/orden.
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
     * Temporalmente en desuso: el flujo activo del asistente no registra BACKFLUSH ni cierra OPs.
     * Se conserva por posible reintegración del workflow por lote/orden.
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

    /**
     * Descarga una plantilla Excel consolidada con todos los productos terminados.
     * Las columnas editables son cantidad_producida, fecha_produccion y observaciones.
     *
     * El formato por lote/OP queda temporalmente en desuso y se conserva en los endpoints
     * de registro por posible reintegración del workflow.
     *
     * @return Archivo Excel con Content-Disposition attachment
     */
    @GetMapping("/plantilla")
    public ResponseEntity<byte[]> descargarPlantilla() {
        byte[] excel = ingresoTerminadosAlmacenService.generarPlantillaExcel();

        String filename = "plantilla_reporte_produccion_terminados_" + AppTime.today() + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    /**
     * Registra múltiples ingresos de producto terminado de forma masiva.
     * Procesa cada ingreso de forma independiente y retorna un resumen con los resultados.
     * Si algunos ingresos fallan, retorna 207 Multi-Status con el detalle de éxitos y fallos.
     *
     * Temporalmente en desuso: el frontend del asistente no usa este endpoint mientras el
     * flujo sea solo reporte consolidado sin cierre de OPs.
     *
     * @param request DTO con username y lista de ingresos a procesar
     * @return 200 si todos exitosos, 207 si hay errores parciales
     */
    @PostMapping("/registrar-masivo")
    public ResponseEntity<IngresoMasivoResponseDTO> registrarMasivo(
            @RequestBody IngresoMasivoRequestDTO request) {

        IngresoMasivoResponseDTO response = ingresoTerminadosAlmacenService.registrarIngresosMasivos(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            // 207 Multi-Status si hay errores parciales
            return ResponseEntity.status(207).body(response);
        }
    }
}
