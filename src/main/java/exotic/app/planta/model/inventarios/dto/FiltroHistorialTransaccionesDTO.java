package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO unificado para filtrar transacciones de almacén en el historial.
 * Soporta todos los tipos de entidad causante (OCM, OD, OAA, CM, OP)
 * con filtros específicos para cada tipo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FiltroHistorialTransaccionesDTO {

    /**
     * Tipo de entidad causante (requerido).
     * Corresponde al enum TipoEntidadCausante: OCM, OP, OAA, OD, CM.
     */
    private String tipoEntidadCausante;

    /**
     * Tipo de filtro de fecha:
     * 0 = sin filtro de fecha
     * 1 = rango de fechas (fechaInicio y fechaFin)
     * 2 = fecha específica (fechaEspecifica)
     */
    private Integer tipoFiltroFecha;

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
     * NIT del proveedor para filtrar (opcional).
     * Solo aplica cuando tipoEntidadCausante = OCM.
     */
    private String proveedorId;

    /**
     * Tipo de filtro de ID (opcional):
     * 0 = sin filtro de ID
     * 1 = filtrar por transaccionId
     * 2 = filtrar por ordenProduccionId
     * Solo aplica cuando tipoEntidadCausante = OD.
     */
    private Integer tipoFiltroId;

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
     * ID del producto terminado para filtrar (opcional).
     * Solo aplica cuando tipoEntidadCausante = OD.
     */
    private String productoTerminadoId;

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
