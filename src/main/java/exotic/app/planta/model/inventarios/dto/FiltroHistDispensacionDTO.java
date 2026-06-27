package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO para filtrar dispensaciones (transacciones de almacén tipo OD).
 * Permite buscar por ID de transacción, ID de orden de producción, lote de producción,
 * producto terminado, y fechas.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FiltroHistDispensacionDTO {
    /**
     * ID de la transacción de almacén (opcional).
     * Se usa cuando tipoFiltroId = 1.
     */
    private Integer transaccionId;

    /**
     * ID de la orden de producción asociada (opcional).
     * Se usa cuando tipoFiltroId = 2.
     */
    private Integer ordenProduccionId;

    /**
     * Lote asignado a la orden de producción (opcional).
     * Se usa cuando tipoFiltroId = 3.
     */
    private String loteAsignado;

    /**
     * ID del producto terminado asociado a la orden de producción (opcional).
     */
    private String productoTerminadoId;

    /**
     * Fecha inicial para búsqueda por rango (opcional).
     * Se usa cuando tipoFiltroFecha = 1.
     */
    private LocalDate fechaInicio;

    /**
     * Fecha final para búsqueda por rango (opcional).
     * Se usa cuando tipoFiltroFecha = 1.
     */
    private LocalDate fechaFin;

    /**
     * Fecha específica para búsqueda (opcional).
     * Se usa cuando tipoFiltroFecha = 2.
     */
    private LocalDate fechaEspecifica;

    /**
     * Tipo de filtro de fecha:
     * 0 = sin filtro de fecha
     * 1 = rango de fechas (fechaInicio y fechaFin)
     * 2 = fecha específica (fechaEspecifica)
     */
    private Integer tipoFiltroFecha;

    /**
     * Tipo de filtro de ID:
     * 0 = sin filtro de ID
     * 1 = filtrar por transaccionId
     * 2 = filtrar por ordenProduccionId
     * 3 = filtrar por loteAsignado de OrdenProduccion
     */
    private Integer tipoFiltroId;

    /**
     * Número de página para paginación (base 0).
     * Por defecto: 0
     */
    private int page = 0;

    /**
     * Tamaño de página para paginación.
     * Por defecto: 10
     */
    private int size = 10;
}
