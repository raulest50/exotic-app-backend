package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.compras.dto.recepcion.SearchOCMFilterDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.ConsolidadoOCMResponseDTO;
import exotic.app.planta.model.inventarios.dto.LoteConsolidadoDTO;
import exotic.app.planta.model.inventarios.dto.MaterialConsolidadoDTO;
import exotic.app.planta.model.inventarios.dto.MovimientoDetalleDTO;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class IngresoAlmacenService {

    private final OrdenCompraRepo ordenCompraRepo;
    private final ProveedorRepo proveedorRepo;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;

    private static final int ESTADO_PENDIENTE_INGRESO_ALMACEN = 2;

    /**
     * Consulta las órdenes de compra pendientes de recepción en almacén.
     * 
     * Este método además calcula y asigna el porcentaje de materiales recibidos
     * para cada orden, usando una query optimizada en batch que calcula todos
     * los porcentajes en una sola consulta SQL.
     * 
     * El porcentaje calculado se asigna al campo @Transient porcentajeRecibido
     * de cada OrdenCompraMateriales, y estará disponible en la respuesta JSON
     * del endpoint /ingresos_almacen/ocms_pendientes_ingreso
     * 
     * @param filterDTO Filtros de búsqueda (fechas, proveedor)
     * @param page Número de página (0-indexed)
     * @param size Tamaño de la página
     * @return Página de órdenes de compra con el campo porcentajeRecibido calculado
     * 
     * @see OrdenCompraRepo#calcularPorcentajesRecibidos
     */
    public Page<OrdenCompraMateriales> consultarOCMsPendientesRecepcion(
            SearchOCMFilterDTO filterDTO,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fechaEmision").descending());

        LocalDateTime fechaInicio = filterDTO.getFechaInicio();
        LocalDateTime fechaFin = filterDTO.getFechaFin();
        String proveedorId = filterDTO.getProveedorId();

        Page<OrdenCompraMateriales> pageResult;

        // If both dates and proveedor are provided
        if (fechaInicio != null && fechaFin != null && proveedorId != null && !proveedorId.trim().isEmpty()) {
            // Load Proveedor entity by business id (String)
            Proveedor proveedor = proveedorRepo.findById(proveedorId)
                    .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado con id: " + proveedorId));
            
            pageResult = ordenCompraRepo.findByEstadoAndProveedorAndFechaEmisionBetween(
                    ESTADO_PENDIENTE_INGRESO_ALMACEN,
                    proveedor,
                    fechaInicio,
                    fechaFin,
                    pageable
            );
        }
        // If only dates are provided
        else if (fechaInicio != null && fechaFin != null) {
            pageResult = ordenCompraRepo.findByFechaEmisionBetweenAndEstadoIn(
                    fechaInicio,
                    fechaFin,
                    Collections.singletonList(ESTADO_PENDIENTE_INGRESO_ALMACEN),
                    pageable
            );
        }
        // If only proveedor is provided
        else if (proveedorId != null && !proveedorId.trim().isEmpty()) {
            pageResult = ordenCompraRepo.findByProveedorIdAndEstado(
                    proveedorId,
                    ESTADO_PENDIENTE_INGRESO_ALMACEN,
                    pageable
            );
        }
        // If neither is provided (or dates are null)
        else {
            pageResult = ordenCompraRepo.findByEstado(
                    ESTADO_PENDIENTE_INGRESO_ALMACEN,
                    pageable
            );
        }

        // Calcular y asignar porcentajes de recepción en batch para optimizar performance
        // En lugar de hacer N queries (una por orden), hacemos una sola query para todas
        List<OrdenCompraMateriales> ordenes = pageResult.getContent();
        if (!ordenes.isEmpty()) {
            // Extraer los IDs de todas las órdenes de la página
            List<Integer> ordenIds = ordenes.stream()
                    .map(OrdenCompraMateriales::getOrdenCompraId)
                    .collect(Collectors.toList());

            // Ejecutar una sola query SQL para calcular todos los porcentajes
            List<Object[]> resultados = ordenCompraRepo.calcularPorcentajesRecibidos(ordenIds);

            // Crear un mapa para lookup O(1) - más eficiente que buscar en lista
            Map<Integer, Double> porcentajeMap = resultados.stream()
                    .collect(Collectors.toMap(
                            row -> ((Number) row[0]).intValue(),      // ordenCompraId
                            row -> ((Number) row[1]).doubleValue(),   // porcentajeRecibido
                            (v1, v2) -> v1  // Si hay duplicados (no debería pasar), tomar el primero
                    ));

            // Asignar el porcentaje calculado a cada orden
            // Si una orden no está en el mapa (sin items o sin transacciones), se deja como null
            ordenes.forEach(orden -> {
                Double porcentaje = porcentajeMap.getOrDefault(orden.getOrdenCompraId(), 0.0);
                orden.setPorcentajeRecibido(porcentaje);
            });
        }

        return pageResult;
    }

    public List<TransaccionAlmacen> consultarTransaccionesAlmacenDeOCM(
            int ordenCompraId, int page, int size) {
        return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteAndIdEntidadCausante(
                TransaccionAlmacen.TipoEntidadCausante.OCM,
                ordenCompraId
        );
    }

    /**
     * Obtiene los movimientos de una transacción de almacén específica.
     * Carga las relaciones de producto y lote usando fetch join para evitar N+1 queries.
     *
     * @param transaccionId ID de la transacción
     * @return Lista de movimientos con sus detalles
     */
    public List<MovimientoDetalleDTO> obtenerMovimientosPorTransaccion(int transaccionId) {
        // Buscar la transacción con sus movimientos cargados usando fetch join
        TransaccionAlmacen transaccion = transaccionAlmacenHeaderRepo.findByIdWithMovimientos(transaccionId)
                .orElseThrow(() -> new RuntimeException("Transacción no encontrada con ID: " + transaccionId));

        // Cargar movimientos con sus relaciones (producto y lote)
        // Necesitamos hacer fetch join manualmente o usar una consulta específica
        List<Movimiento> movimientos = transaccion.getMovimientosTransaccion();
        if (movimientos == null || movimientos.isEmpty()) {
            return Collections.emptyList();
        }

        // Mapear a DTOs
        return movimientos.stream()
                .map(movimiento -> {
                    MovimientoDetalleDTO dto = new MovimientoDetalleDTO();
                    dto.setMovimientoId(movimiento.getMovimientoId());

                    if (movimiento.getProducto() != null) {
                        dto.setProductoId(movimiento.getProducto().getProductoId());
                        dto.setProductoNombre(movimiento.getProducto().getNombre());
                        dto.setTipoUnidades(movimiento.getProducto().getTipoUnidades());
                    }

                    if (movimiento.getAreaOperativa() != null) {
                        dto.setAreaOperativaId(movimiento.getAreaOperativa().getAreaId());
                        dto.setAreaOperativaNombre(movimiento.getAreaOperativa().getNombre());
                    }

                    dto.setCantidad(movimiento.getCantidad());
                    dto.setTipoMovimiento(movimiento.getTipoMovimiento() != null
                            ? movimiento.getTipoMovimiento().name()
                            : null);
                    dto.setAlmacen(movimiento.getAlmacen() != null
                            ? movimiento.getAlmacen().name()
                            : null);
                    dto.setFechaMovimiento(movimiento.getFechaMovimiento());

                    if (movimiento.getLote() != null) {
                        dto.setBatchNumber(movimiento.getLote().getBatchNumber());
                        dto.setProductionDate(movimiento.getLote().getProductionDate());
                        dto.setExpirationDate(movimiento.getLote().getExpirationDate());
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Obtiene el consolidado de todos los materiales recibidos para una OCM.
     * Agrupa materiales por productoId y suma cantidades, manteniendo información de lotes.
     *
     * @param ordenCompraId ID de la orden de compra
     * @return Consolidado de materiales con sus lotes
     */
    public ConsolidadoOCMResponseDTO obtenerConsolidadoMaterialesPorOCM(int ordenCompraId) {
        // Obtener todas las transacciones de tipo OCM para esta orden con movimientos cargados
        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OCM,
                        ordenCompraId
                );

        if (transacciones.isEmpty()) {
            ConsolidadoOCMResponseDTO response = new ConsolidadoOCMResponseDTO();
            response.setOrdenCompraId(ordenCompraId);
            response.setMateriales(Collections.emptyList());
            response.setTotalTransacciones(0);
            return response;
        }

        // Map para consolidar por productoId
        Map<String, MaterialConsolidadoDTO> consolidadoMap = new HashMap<>();

        // Procesar cada transacción
        for (TransaccionAlmacen transaccion : transacciones) {
            List<Movimiento> movimientos = transaccion.getMovimientosTransaccion();
            if (movimientos == null) {
                continue;
            }

            int transaccionId = transaccion.getTransaccionId();

            for (Movimiento movimiento : movimientos) {
                if (movimiento.getProducto() == null) {
                    continue;
                }

                String productoId = movimiento.getProducto().getProductoId();

                // Obtener o crear el material consolidado
                MaterialConsolidadoDTO material = consolidadoMap.computeIfAbsent(productoId, id -> {
                    MaterialConsolidadoDTO nuevo = new MaterialConsolidadoDTO();
                    nuevo.setProductoId(id);
                    nuevo.setProductoNombre(movimiento.getProducto().getNombre());
                    nuevo.setTipoUnidades(movimiento.getProducto().getTipoUnidades());
                    nuevo.setCantidadTotal(0.0);
                    nuevo.setLotes(new ArrayList<>());
                    return nuevo;
                });

                // Sumar cantidad total
                material.setCantidadTotal(material.getCantidadTotal() + movimiento.getCantidad());

                // Agregar información del lote
                LoteConsolidadoDTO loteDTO = new LoteConsolidadoDTO();
                if (movimiento.getLote() != null) {
                    loteDTO.setBatchNumber(movimiento.getLote().getBatchNumber());
                    loteDTO.setExpirationDate(movimiento.getLote().getExpirationDate());
                }
                loteDTO.setCantidad(movimiento.getCantidad());
                loteDTO.setTransaccionId(transaccionId);

                material.getLotes().add(loteDTO);
            }
        }

        // Crear respuesta
        ConsolidadoOCMResponseDTO response = new ConsolidadoOCMResponseDTO();
        response.setOrdenCompraId(ordenCompraId);
        response.setMateriales(new ArrayList<>(consolidadoMap.values()));
        response.setTotalTransacciones(transacciones.size());

        return response;
    }
}
