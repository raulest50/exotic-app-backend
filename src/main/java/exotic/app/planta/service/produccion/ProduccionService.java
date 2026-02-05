package exotic.app.planta.service.produccion;


import exotic.app.planta.model.ventas.Vendedor;
import exotic.app.planta.repo.ventas.VendedorRepository;
import org.springframework.transaction.annotation.Transactional;
import exotic.app.planta.model.contabilidad.AsientoContable;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.LoteRecomendadoDTO;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.procesos.AreaProduccion;
import exotic.app.planta.model.producto.manufacturing.procesos.nodo.ProcesoProduccionNode;
import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.OrdenSeguimiento;
import exotic.app.planta.model.produccion.dto.DispensacionDTO;
import exotic.app.planta.model.produccion.dto.DispensacionFormularioDTO;
import exotic.app.planta.model.produccion.dto.InventarioEnTransitoDTO;
import exotic.app.planta.model.produccion.dto.InsumoDTO;
import exotic.app.planta.model.produccion.dto.ODP_Data4PDF;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO_save;
import exotic.app.planta.model.produccion.dto.OrdenSeguimientoDTO;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.OrdenSeguimientoRepo;
import exotic.app.planta.service.contabilidad.ContabilidadService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class ProduccionService {


    private final OrdenProduccionRepo ordenProduccionRepo;
    private final TerminadoRepo terminadoRepo;
    private final TransaccionAlmacenRepo movmientoRepo;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;
    private final ContabilidadService contabilidadService;

    private final ProductoRepo productoRepo;

    private final LoteRepo loteRepo;
    //private final UserRepository userRepository;
    private final VendedorRepository vendedorRepository;

    @Autowired
    private final OrdenSeguimientoRepo ordenSeguimientoRepo;

    @Autowired
    private TransaccionAlmacenRepo transaccionAlmacenRepo;


    @Transactional(rollbackFor = Exception.class)
    public OrdenProduccion saveOrdenProduccion(OrdenProduccionDTO_save ordenProduccionDTO) {
        Producto producto = productoRepo.findById(ordenProduccionDTO.getProductoId())
            .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado con ID: " + ordenProduccionDTO.getProductoId()));

        Long vendedorResponsableId = ordenProduccionDTO.getVendedorResponsableId();
        if (vendedorResponsableId == null) {
            throw new IllegalArgumentException("El ID del responsable es obligatorio para registrar la orden de producción.");
        }

        Vendedor vendedorResponsable = vendedorRepository.findById(vendedorResponsableId)
            .orElseThrow(() -> new IllegalArgumentException("Responsable no encontrado con ID: " + vendedorResponsableId));

        OrdenProduccion ordenProduccion = new OrdenProduccion(producto, ordenProduccionDTO.getObservaciones(), ordenProduccionDTO.getCantidadProducir());
        ordenProduccion.setFechaLanzamiento(ordenProduccionDTO.getFechaLanzamiento());
        ordenProduccion.setFechaFinalPlanificada(ordenProduccionDTO.getFechaFinalPlanificada());
        ordenProduccion.setNumeroPedidoComercial(ordenProduccionDTO.getNumeroPedidoComercial());
        ordenProduccion.setAreaOperativa(ordenProduccionDTO.getAreaOperativa());
        ordenProduccion.setDepartamentoOperativo(ordenProduccionDTO.getDepartamentoOperativo());
        ordenProduccion.setVendedorResponsable(vendedorResponsable);

        OrdenProduccion savedOrden = ordenProduccionRepo.save(ordenProduccion);

        if (ordenProduccionDTO.getLoteBatchNumber() != null && !ordenProduccionDTO.getLoteBatchNumber().isBlank()) {
            Lote lote = new Lote();
            lote.setBatchNumber(ordenProduccionDTO.getLoteBatchNumber());
            lote.setOrdenProduccion(savedOrden);
            loteRepo.save(lote);
            savedOrden.setLoteAsignado(lote.getBatchNumber());
            ordenProduccionRepo.save(savedOrden);
        }

        List<OrdenSeguimiento> ordenesSeguimiento = savedOrden.getOrdenesSeguimiento();
        if (ordenesSeguimiento != null && !ordenesSeguimiento.isEmpty()) {
            // Create Movimiento entries for each Insumo
            for (OrdenSeguimiento ordenSeguimiento : ordenesSeguimiento) {
                Insumo insumo = ordenSeguimiento.getInsumo();
                Movimiento movimientoReal = new Movimiento();
                movimientoReal.setCantidad(-insumo.getCantidadRequerida()); // Negative cantidad
                movimientoReal.setProducto(insumo.getProducto());
                movimientoReal.setTipoMovimiento(Movimiento.TipoMovimiento.CONSUMO);
                //movimiento.setObservaciones("Consumo para Orden de Producción ID: " + savedOrden.getOrdenId());
                transaccionAlmacenRepo.save(movimientoReal);
            }
        }

        return savedOrden;
    }

    /**
     * Genera el siguiente número de lote para un producto terminado.
     * Patrón: prefijoLote + "-" + número de 7 dígitos + "-" + año 2 dígitos (ej. TRK-0000001-26).
     * El secuencial se calcula sobre lotes existentes del mismo producto y año.
     *
     * @param productoId ID del producto terminado
     * @return Número de lote generado, o null si el producto no es terminado o no tiene prefijoLote
     */
    @Transactional(readOnly = true)
    public String obtenerSiguienteNumeroLote(String productoId) {
        Optional<Terminado> terminadoOpt = terminadoRepo.findById(productoId);
        if (terminadoOpt.isEmpty()) {
            throw new IllegalArgumentException("Producto no encontrado o no es un producto terminado: " + productoId);
        }
        Terminado terminado = terminadoOpt.get();
        String prefijo = terminado.getPrefijoLote();
        if (prefijo == null || prefijo.isBlank()) {
            throw new IllegalArgumentException("El producto terminado no tiene prefijo de lote definido: " + productoId);
        }
        prefijo = prefijo.trim();
        int year2 = Year.now().getValue() % 100;
        String yearStr = String.format("%02d", year2);

        List<Lote> lotes = loteRepo.findByOrdenProduccion_Producto_ProductoId(productoId);
        Pattern regex = Pattern.compile("^" + Pattern.quote(prefijo) + "-(\\d+)-(\\d{2})$");
        int max = 0;
        for (Lote l : lotes) {
            String bn = l.getBatchNumber();
            if (bn == null) continue;
            Matcher m = regex.matcher(bn);
            if (m.matches()) {
                int y = Integer.parseInt(m.group(2));
                if (y == year2) {
                    int n = Integer.parseInt(m.group(1));
                    if (n > max) max = n;
                }
            }
        }
        int next = max + 1;
        return prefijo + "-" + String.format("%07d", next) + "-" + yearStr;
    }

    public Page<OrdenProduccionDTO> searchOrdenesProduccionByDateRangeAndEstadoOrden(
            LocalDateTime startDate,
            LocalDateTime endDate,
            int estadoOrden,
            String productoId,
            Pageable pageable
    ) {
        Page<OrdenProduccion> page = ordenProduccionRepo.findByFechaCreacionBetweenAndEstadoOrden(
                startDate,
                endDate,
                estadoOrden,
                productoId,
                pageable
        );
        // Initialize necessary associations
        page.getContent().forEach(orden -> {
            Hibernate.initialize(orden.getOrdenesSeguimiento());
            Hibernate.initialize(orden.getProducto());
        });

        // Map entities to DTOs
        List<OrdenProduccionDTO> dtoList = page.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, page.getTotalElements());
    }

    /**
     * Obtiene todas las órdenes de producción que no están terminadas (2) ni canceladas (-1).
     *
     * @param pageable Información de paginación
     * @return Página de DTOs de órdenes de producción
     */
    public Page<OrdenProduccionDTO> getOrdenesProduccionOpenOrInProgress(Pageable pageable) {
        Page<OrdenProduccion> ordenesPage = ordenProduccionRepo.findByEstadoOrdenOpenOrInProgress(pageable);
        // No need to initialize associations as EntityGraph is used in the repository method
        return ordenesPage.map(this::convertToDto);
    }

    /**
     * Obtiene una orden de producción por ID si no está terminada (2) ni cancelada (-1).
     *
     * @param ordenId ID de la orden de producción
     * @return DTO de la orden de producción si existe y está en estado válido, null en caso contrario
     */
    public OrdenProduccionDTO getOrdenProduccionByIdForDispensacion(Integer ordenId) {
        if (ordenId == null) {
            return null;
        }

        Optional<OrdenProduccion> ordenOpt = ordenProduccionRepo.findById(ordenId);
        if (ordenOpt.isEmpty()) {
            return null;
        }

        OrdenProduccion orden = ordenOpt.get();
        int estadoOrden = orden.getEstadoOrden();

        // Solo retornar si no está terminada (2) ni cancelada (-1)
        if (estadoOrden == 2 || estadoOrden == -1) {
            return null;
        }

        return convertToDto(orden);
    }

    // Helper method to map OrdenProduccion to OrdenProduccionDTO
    private OrdenProduccionDTO convertToDto(OrdenProduccion orden) {
        OrdenProduccionDTO dto = new OrdenProduccionDTO();
        dto.setOrdenId(orden.getOrdenId());
        dto.setProductoId(orden.getProducto().getProductoId());
        dto.setProductoNombre(orden.getProducto().getNombre());
        dto.setFechaInicio(orden.getFechaInicio());
        dto.setFechaCreacion(orden.getFechaCreacion());
        dto.setFechaLanzamiento(orden.getFechaLanzamiento());
        dto.setFechaFinalPlanificada(orden.getFechaFinalPlanificada());
        dto.setEstadoOrden(orden.getEstadoOrden());
        dto.setObservaciones(orden.getObservaciones());
        dto.setCantidadProducir(orden.getCantidadProducir());
        dto.setNumeroPedidoComercial(orden.getNumeroPedidoComercial());
        dto.setAreaOperativa(orden.getAreaOperativa());
        dto.setDepartamentoOperativo(orden.getDepartamentoOperativo());
        if (orden.getVendedorResponsable() != null) {
            dto.setResponsableId(orden.getVendedorResponsable().getCedula());
        }

        List<OrdenSeguimientoDTO> seguimientoDTOs = orden.getOrdenesSeguimiento().stream()
                .map(this::convertSeguimientoToDto)
                .collect(Collectors.toList());
        dto.setOrdenesSeguimiento(seguimientoDTOs);

        return dto;
    }

    // Helper method to map OrdenSeguimiento to OrdenSeguimientoDTO
    private OrdenSeguimientoDTO convertSeguimientoToDto(OrdenSeguimiento seguimiento) {
        OrdenSeguimientoDTO dto = new OrdenSeguimientoDTO();
        dto.setSeguimientoId(seguimiento.getSeguimientoId());
        dto.setInsumoNombre(seguimiento.getInsumo().getProducto().getNombre());
        dto.setCantidadRequerida(seguimiento.getInsumo().getCantidadRequerida());
        dto.setEstado(seguimiento.getEstado());
        return dto;
    }




    public Page<InventarioEnTransitoDTO> getInventarioEnTransito(Pageable pageable) {
        // Fetch all Ordenes de Producción with estadoOrden = 0
        List<OrdenProduccion> ordenesProduccion = ordenProduccionRepo.findByEstadoOrden(0);

        // Initialize necessary associations
        for (OrdenProduccion orden : ordenesProduccion) {
            Hibernate.initialize(orden.getOrdenesSeguimiento());
            for (OrdenSeguimiento seguimiento : orden.getOrdenesSeguimiento()) {
                Hibernate.initialize(seguimiento.getInsumo());
                Hibernate.initialize(seguimiento.getInsumo().getProducto());
            }
        }

        // Map to hold Producto ID and corresponding InventarioEnTransitoDTO
        Map<String, InventarioEnTransitoDTO> inventarioMap = new HashMap<>();

        // Process each Orden de Producción
        for (OrdenProduccion orden : ordenesProduccion) {
            int ordenProduccionId = orden.getOrdenId();
            for (OrdenSeguimiento seguimiento : orden.getOrdenesSeguimiento()) {
                Insumo insumo = seguimiento.getInsumo();
                Producto producto = insumo.getProducto();
                String productoId = producto.getProductoId();
                String productoNombre = producto.getNombre();
                double cantidadRequerida = insumo.getCantidadRequerida();

                InventarioEnTransitoDTO inventarioDTO = inventarioMap.get(productoId);
                if (inventarioDTO == null) {
                    inventarioDTO = new InventarioEnTransitoDTO();
                    inventarioDTO.setProductoId(productoId);
                    inventarioDTO.setProductoNombre(productoNombre);
                    inventarioDTO.setCantidadTotal(cantidadRequerida);
                    inventarioDTO.setOrdenesProduccionIds(new ArrayList<>());
                    inventarioDTO.getOrdenesProduccionIds().add(ordenProduccionId);
                    inventarioMap.put(productoId, inventarioDTO);
                } else {
                    inventarioDTO.setCantidadTotal(inventarioDTO.getCantidadTotal() + cantidadRequerida);
                    if (!inventarioDTO.getOrdenesProduccionIds().contains(ordenProduccionId)) {
                        inventarioDTO.getOrdenesProduccionIds().add(ordenProduccionId);
                    }
                }
            }
        }

        // Convert map values to a list
        List<InventarioEnTransitoDTO> inventarioList = new ArrayList<>(inventarioMap.values());

        // Apply sorting if needed (e.g., by productoNombre)
        inventarioList.sort(Comparator.comparing(InventarioEnTransitoDTO::getProductoNombre));

        // Implement pagination manually
        int total = inventarioList.size();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), total);
        List<InventarioEnTransitoDTO> output;
        if (start <= end) {
            output = inventarioList.subList(start, end);
        } else {
            output = new ArrayList<>();
        }

        return new PageImpl<>(output, pageable, total);
    }



    /**
     * Update the estado of an OrdenSeguimiento.
     */
    @Transactional
    public OrdenSeguimientoDTO updateEstadoOrdenSeguimiento(int seguimientoId, int estado) {
        ordenSeguimientoRepo.updateEstadoById(seguimientoId, estado);

        // Fetch updated OrdenSeguimiento
        OrdenSeguimiento ordenSeguimiento = ordenSeguimientoRepo.findById(seguimientoId).orElseThrow(() -> new RuntimeException("OrdenSeguimiento not found"));

        // Return updated DTO
        return convertSeguimientoToDto(ordenSeguimiento);
    }

    /**
     * Update the estadoOrden of an OrdenProduccion and register Movimiento.
     * For completed production orders (estado = 2), also creates an accounting entry.
     */
    @Transactional
    public OrdenProduccionDTO updateEstadoOrdenProduccion(int ordenId, int estadoOrden) {
        ordenProduccionRepo.updateEstadoOrdenById(ordenId, estadoOrden);

        // Fetch updated OrdenProduccion
        OrdenProduccion ordenProduccion = ordenProduccionRepo.findById(ordenId).orElseThrow(() -> new RuntimeException("OrdenProduccion not found"));

        // Solo crear transacción de almacén si el estado es TERMINADA (2)
        if (estadoOrden == 2) {
            // Register Movimiento for the produced Producto
            Movimiento movimientoReal = new Movimiento();
            movimientoReal.setCantidad(ordenProduccion.getProducto().getCantidadUnidad()); // Adjust as per your business logic
            movimientoReal.setProducto(ordenProduccion.getProducto());
            movimientoReal.setTipoMovimiento(Movimiento.TipoMovimiento.BACKFLUSH);
            movimientoReal.setAlmacen(Movimiento.Almacen.GENERAL);

            // Create a transaction for this movement
            TransaccionAlmacen transaccion = new TransaccionAlmacen();
            transaccion.setTipoEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.OP);
            transaccion.setIdEntidadCausante(ordenId);
            transaccion.setObservaciones("Producción finalizada para Orden ID: " + ordenId);

            // Add the movement to the transaction
            List<Movimiento> movimientos = new ArrayList<>();
            movimientos.add(movimientoReal);
            transaccion.setMovimientosTransaccion(movimientos);
            movimientoReal.setTransaccionAlmacen(transaccion);

            // Save the transaction
            transaccionAlmacenHeaderRepo.save(transaccion);

            // Create accounting entry for BACKFLUSH
            try {
                // Calculate the total amount based on the product cost
                BigDecimal montoTotal = BigDecimal.valueOf(ordenProduccion.getProducto().getCosto() * 
                                                          ordenProduccion.getProducto().getCantidadUnidad());

                // Register the accounting entry
                AsientoContable asiento = contabilidadService.registrarAsientoBackflush(transaccion, ordenProduccion, montoTotal);

                // Update the transaction with the accounting entry reference
                transaccion.setAsientoContable(asiento);
                transaccion.setEstadoContable(TransaccionAlmacen.EstadoContable.CONTABILIZADA);
                transaccionAlmacenHeaderRepo.save(transaccion);

                log.info("Asiento contable registrado con ID: " + asiento.getId() + " para la OP: " + ordenId);
            } catch (Exception e) {
                log.error("Error al registrar asiento contable para OP " + ordenId + ": " + e.getMessage(), e);
                // We don't interrupt the main flow if accounting fails
            }
        }

        return convertToDto(ordenProduccion);
    }

    /**
     * Cancela una orden de producción si se encuentra en estado abierto (0).
     *
     * @param ordenId identificador de la orden a cancelar
     * @return DTO actualizado de la orden cancelada
     */
    @Transactional
    public OrdenProduccionDTO cancelarOrdenProduccion(int ordenId) {
        OrdenProduccion ordenProduccion = ordenProduccionRepo.findById(ordenId)
            .orElseThrow(() -> new IllegalArgumentException("Orden de producción no encontrada con ID: " + ordenId));

        if (!isOrdenProduccionCancelable(ordenProduccion)) {
            throw new IllegalStateException("Solo se pueden cancelar órdenes en estado abierto (0). Estado actual: " + ordenProduccion.getEstadoOrden());
        }

        ordenProduccion.setEstadoOrden(-1);
        if (ordenProduccion.getFechaFinal() == null) {
            ordenProduccion.setFechaFinal(LocalDateTime.now());
        }

        ordenProduccionRepo.save(ordenProduccion);
        return convertToDto(ordenProduccion);
    }

    public boolean isOrdenProduccionCancelable(int ordenId) {
        OrdenProduccion ordenProduccion = ordenProduccionRepo.findById(ordenId)
            .orElseThrow(() -> new IllegalArgumentException("Orden de producción no encontrada con ID: " + ordenId));

        return isOrdenProduccionCancelable(ordenProduccion);
    }

    private boolean isOrdenProduccionCancelable(OrdenProduccion ordenProduccion) {
        return ordenProduccion.getEstadoOrden() == 0;
    }

    /**
     * Get the inputs (insumos) needed for a production order.
     * Includes recommended batches for each input, prioritizing those with closest expiration date.
     */
    public List<InsumoDTO> getInsumosOrdenProduccion(int ordenId) {
        OrdenProduccion ordenProduccion = ordenProduccionRepo.findById(ordenId)
            .orElseThrow(() -> new RuntimeException("Orden de producción no encontrada con ID: " + ordenId));

        return ordenProduccion.getOrdenesSeguimiento().stream()
            .map(ordenSeguimiento -> {
                Insumo insumo = ordenSeguimiento.getInsumo();
                InsumoDTO insumoDTO = new InsumoDTO();
                insumoDTO.setProductoId(insumo.getProducto().getProductoId());
                insumoDTO.setNombreProducto(insumo.getProducto().getNombre());
                insumoDTO.setCantidadRequerida(insumo.getCantidadRequerida());
                insumoDTO.setEstadoSeguimiento(ordenSeguimiento.getEstado());
                insumoDTO.setSeguimientoId(ordenSeguimiento.getSeguimientoId());
                insumoDTO.setLotesRecomendados(new ArrayList<>());

                // Obtener lotes con stock disponible para este producto
                List<Object[]> lotesConStock;
                try {
                    // Intentar primero con la consulta JPQL
                    lotesConStock = transaccionAlmacenRepo.findLotesWithStockByProductoIdOrderByExpirationDate(
                        insumo.getProducto().getProductoId());
                } catch (Exception e) {
                    // Si falla, usar la consulta SQL nativa como alternativa
                    log.warn("Error al ejecutar consulta JPQL para lotes, usando SQL nativo: " + e.getMessage());
                    lotesConStock = transaccionAlmacenRepo.findLotesWithStockByProductoIdNative(
                        insumo.getProducto().getProductoId());
                }

                // Mapear a DTOs y calcular cuánto tomar de cada lote
                List<LoteRecomendadoDTO> lotesRecomendados = new ArrayList<>();
                double cantidadRestante = insumo.getCantidadRequerida();

                for (Object[] result : lotesConStock) {
                    if (cantidadRestante <= 0) {
                        break;
                    }

                    try {
                        Lote lote;
                        Double stockDisponible;

                        // Intentar procesar como resultado de consulta JPQL
                        if (result[0] instanceof Lote) {
                            lote = (Lote) result[0];
                            stockDisponible = (Double) result[1];
                        } 
                        // Procesar como resultado de consulta SQL nativa
                        else {
                            // Para SQL nativo, necesitamos buscar el lote por ID
                            Long loteId = null;

                            // El primer elemento podría ser un número (ID del lote)
                            if (result[0] instanceof Number) {
                                loteId = ((Number) result[0]).longValue();
                            } 
                            // O podría ser un mapa con los valores de las columnas
                            else if (result[0] instanceof Map) {
                                Map<String, Object> map = (Map<String, Object>) result[0];
                                loteId = ((Number) map.get("id")).longValue();
                            }

                            // Si no pudimos obtener el ID del lote, continuamos con el siguiente
                            if (loteId == null) {
                                log.warn("No se pudo obtener el ID del lote del resultado de la consulta");
                                continue;
                            }

                            // Buscar el lote por ID
                            Optional<Lote> optionalLote = loteRepo.findById(loteId);
                            if (!optionalLote.isPresent()) {
                                log.warn("No se encontró el lote con ID: " + loteId);
                                continue;
                            }

                            lote = optionalLote.get();

                            // El stock disponible podría estar en diferentes posiciones según la consulta
                            if (result.length > 1 && result[1] instanceof Number) {
                                stockDisponible = ((Number) result[1]).doubleValue();
                            } else {
                                log.warn("No se pudo obtener el stock disponible del resultado de la consulta");
                                continue;
                            }
                        }

                        double cantidadAUsar = Math.min(stockDisponible, cantidadRestante);

                        LoteRecomendadoDTO loteRecomendado = new LoteRecomendadoDTO();
                        loteRecomendado.setLoteId(lote.getId());
                        loteRecomendado.setBatchNumber(lote.getBatchNumber());
                        loteRecomendado.setProductionDate(lote.getProductionDate());
                        loteRecomendado.setExpirationDate(lote.getExpirationDate());
                        loteRecomendado.setCantidadDisponible(stockDisponible);  // Cantidad total disponible en el lote
                        loteRecomendado.setCantidadRecomendada(cantidadAUsar);   // Cantidad recomendada a tomar

                        lotesRecomendados.add(loteRecomendado);

                        cantidadRestante -= cantidadAUsar;
                    } catch (Exception e) {
                        log.error("Error al procesar lote recomendado: " + e.getMessage(), e);
                        // Continuamos con el siguiente lote
                    }
                }

                insumoDTO.setLotesRecomendados(lotesRecomendados);

                return insumoDTO;
            })
            .collect(Collectors.toList());
    }
    /**
     * Obtiene un formulario de dispensación para una orden de producción.
     * Incluye todos los materiales necesarios y los lotes recomendados para cada uno,
     * priorizando los lotes más próximos a vencer.
     * 
     * @param ordenId ID de la orden de producción
     * @return DTO con la información del formulario de dispensación
     */
    public DispensacionFormularioDTO getFormularioDispensacion(int ordenId) {
        OrdenProduccion ordenProduccion = ordenProduccionRepo.findById(ordenId)
            .orElseThrow(() -> new RuntimeException("Orden de producción no encontrada con ID: " + ordenId));

        // Crear el DTO del formulario
        DispensacionFormularioDTO formulario = new DispensacionFormularioDTO();
        formulario.setOrdenProduccionId(ordenId);
        formulario.setProductoNombre(ordenProduccion.getProducto().getNombre());

        // Obtener las dispensaciones para cada insumo
        List<DispensacionDTO> dispensaciones = ordenProduccion.getOrdenesSeguimiento().stream()
            .map(ordenSeguimiento -> {
                Insumo insumo = ordenSeguimiento.getInsumo();
                DispensacionDTO dispensacionDTO = new DispensacionDTO();
                dispensacionDTO.setProductoId(insumo.getProducto().getProductoId());
                dispensacionDTO.setNombreProducto(insumo.getProducto().getNombre());
                dispensacionDTO.setCantidadRequerida(insumo.getCantidadRequerida());
                dispensacionDTO.setEstadoSeguimiento(ordenSeguimiento.getEstado());
                dispensacionDTO.setSeguimientoId(ordenSeguimiento.getSeguimientoId());
                dispensacionDTO.setLotesRecomendados(new ArrayList<>());

                // Obtener lotes con stock disponible para este producto
                List<Object[]> lotesConStock;
                try {
                    // Intentar primero con la consulta JPQL
                    lotesConStock = transaccionAlmacenRepo.findLotesWithStockByProductoIdOrderByExpirationDate(
                        insumo.getProducto().getProductoId());
                } catch (Exception e) {
                    // Si falla, usar la consulta SQL nativa como alternativa
                    log.warn("Error al ejecutar consulta JPQL para lotes, usando SQL nativo: " + e.getMessage());
                    lotesConStock = transaccionAlmacenRepo.findLotesWithStockByProductoIdNative(
                        insumo.getProducto().getProductoId());
                }

                // Mapear a DTOs y calcular cuánto tomar de cada lote
                List<LoteRecomendadoDTO> lotesRecomendados = new ArrayList<>();
                double cantidadRestante = insumo.getCantidadRequerida();

                for (Object[] result : lotesConStock) {
                    if (cantidadRestante <= 0) {
                        break;
                    }

                    try {
                        Lote lote;
                        Double stockDisponible;

                        // Intentar procesar como resultado de consulta JPQL
                        if (result[0] instanceof Lote) {
                            lote = (Lote) result[0];
                            stockDisponible = (Double) result[1];
                        } 
                        // Procesar como resultado de consulta SQL nativa
                        else {
                            // Para SQL nativo, necesitamos buscar el lote por ID
                            Long loteId = null;

                            // El primer elemento podría ser un número (ID del lote)
                            if (result[0] instanceof Number) {
                                loteId = ((Number) result[0]).longValue();
                            } 
                            // O podría ser un mapa con los valores de las columnas
                            else if (result[0] instanceof Map) {
                                Map<String, Object> map = (Map<String, Object>) result[0];
                                loteId = ((Number) map.get("id")).longValue();
                            }

                            // Si no pudimos obtener el ID del lote, continuamos con el siguiente
                            if (loteId == null) {
                                log.warn("No se pudo obtener el ID del lote del resultado de la consulta");
                                continue;
                            }

                            // Buscar el lote por ID
                            Optional<Lote> optionalLote = loteRepo.findById(loteId);
                            if (!optionalLote.isPresent()) {
                                log.warn("No se encontró el lote con ID: " + loteId);
                                continue;
                            }

                            lote = optionalLote.get();

                            // El stock disponible podría estar en diferentes posiciones según la consulta
                            if (result.length > 1 && result[1] instanceof Number) {
                                stockDisponible = ((Number) result[1]).doubleValue();
                            } else {
                                log.warn("No se pudo obtener el stock disponible del resultado de la consulta");
                                continue;
                            }
                        }

                        double cantidadAUsar = Math.min(stockDisponible, cantidadRestante);

                        LoteRecomendadoDTO loteRecomendado = new LoteRecomendadoDTO();
                        loteRecomendado.setLoteId(lote.getId());
                        loteRecomendado.setBatchNumber(lote.getBatchNumber());
                        loteRecomendado.setProductionDate(lote.getProductionDate());
                        loteRecomendado.setExpirationDate(lote.getExpirationDate());
                        loteRecomendado.setCantidadDisponible(stockDisponible);  // Cantidad total disponible en el lote
                        loteRecomendado.setCantidadRecomendada(cantidadAUsar);   // Cantidad recomendada a tomar

                        lotesRecomendados.add(loteRecomendado);

                        cantidadRestante -= cantidadAUsar;
                    } catch (Exception e) {
                        log.error("Error al procesar lote recomendado: " + e.getMessage(), e);
                        // Continuamos con el siguiente lote
                    }
                }

                dispensacionDTO.setLotesRecomendados(lotesRecomendados);

                return dispensacionDTO;
            })
            .collect(Collectors.toList());

        formulario.setDispensaciones(dispensaciones);

        return formulario;
    }
    /**
     * Obtiene los datos necesarios para generar un PDF de un producto terminado.
     * Incluye el producto terminado, la lista de materiales, la lista de semiterminados
     * y la lista de áreas de producción ordenadas (con el área del terminado al final).
     * 
     * @param terminadoId ID del producto terminado
     * @return Objeto ODP_Data4PDF con la información necesaria
     */
    public ODP_Data4PDF getTerminadoData4PDF(String terminadoId) {
        ODP_Data4PDF data = new ODP_Data4PDF();

        // Obtener el producto terminado
        Terminado terminado = terminadoRepo.findById(terminadoId)
            .orElseThrow(() -> new RuntimeException("Producto terminado no encontrado con ID: " + terminadoId));

        data.setTerminado(terminado);

        // Separar insumos en materiales y semiterminados
        List<Material> materials = new ArrayList<>();
        List<SemiTerminado> semiterminados = new ArrayList<>();

        for (Insumo insumo : terminado.getInsumos()) {
            Producto producto = insumo.getProducto();
            if (producto instanceof Material) {
                materials.add((Material) producto);
            } else if (producto instanceof SemiTerminado) {
                semiterminados.add((SemiTerminado) producto);
            }
        }

        data.setMaterials(materials);
        data.setSemiterminados(semiterminados);

        // Obtener y ordenar áreas de producción (con el área del terminado al final)
        data.setAreasProduccion(getAreasProduccionOrdenadas(terminado));

        return data;
    }

    /**
     * Obtiene la lista de áreas de producción asociadas a un producto terminado
     * y todos sus semiterminados, colocando el área del terminado al final.
     * 
     * @param terminado El producto terminado
     * @return Lista ordenada de áreas de producción
     */
    private List<AreaProduccion> getAreasProduccionOrdenadas(Terminado terminado) {
        List<AreaProduccion> areasProduccion = new ArrayList<>();
        AreaProduccion areaTerminado = null;

        // Obtener el área de producción del terminado
        if (terminado.getProcesoProduccionCompleto() != null && 
            terminado.getProcesoProduccionCompleto().getAreaProduccion() != null) {
            areaTerminado = terminado.getProcesoProduccionCompleto().getAreaProduccion();
        }

        // Recolectar áreas de producción de los semiterminados
        for (Insumo insumo : terminado.getInsumos()) {
            Producto producto = insumo.getProducto();
            if (producto instanceof SemiTerminado) {
                SemiTerminado semiterminado = (SemiTerminado) producto;

                if (semiterminado.getProcesoProduccionCompleto() != null && 
                    semiterminado.getProcesoProduccionCompleto().getAreaProduccion() != null) {

                    AreaProduccion areaSemiterminado = semiterminado.getProcesoProduccionCompleto().getAreaProduccion();

                    // Evitar duplicados
                    if (!areasProduccion.contains(areaSemiterminado) && 
                        (areaTerminado == null || !areaSemiterminado.equals(areaTerminado))) {
                        areasProduccion.add(areaSemiterminado);
                    }
                }
            }
        }

        // Añadir el área del terminado al final si existe
        if (areaTerminado != null) {
            areasProduccion.add(areaTerminado);
        }

        return areasProduccion;
    }

    /**
     * Obtiene la lista de nombres de procesos asociados a un producto terminado
     * y todos sus semiterminados, extrayendo los nombres específicamente de cada 
     * nodo en la lista ProcesoProduccionNode.
     * 
     * @param terminadoId ID del producto terminado
     * @return Lista de nombres de procesos
     */
    public List<String> getProcesoNombres(String terminadoId) {
        Set<String> nombresProcesos = new HashSet<>();

        // Obtener el producto terminado
        Terminado terminado = terminadoRepo.findById(terminadoId)
            .orElseThrow(() -> new RuntimeException("Producto terminado no encontrado con ID: " + terminadoId));

        // Añadir procesos del producto terminado
        if (terminado.getProcesoProduccionCompleto() != null && 
            terminado.getProcesoProduccionCompleto().getProcesosProduccion() != null) {

            for (ProcesoProduccionNode nodo : terminado.getProcesoProduccionCompleto().getProcesosProduccion()) {
                if (nodo.getProcesoProduccion() != null) {
                    // Extraer el nombre del proceso específicamente del nodo
                    nombresProcesos.add(nodo.getProcesoProduccion().getNombre());
                }
            }
        }

        // Añadir procesos de cada semiterminado
        for (Insumo insumo : terminado.getInsumos()) {
            Producto producto = insumo.getProducto();
            if (producto instanceof SemiTerminado) {
                SemiTerminado semiterminado = (SemiTerminado) producto;

                if (semiterminado.getProcesoProduccionCompleto() != null && 
                    semiterminado.getProcesoProduccionCompleto().getProcesosProduccion() != null) {

                    for (ProcesoProduccionNode nodo : semiterminado.getProcesoProduccionCompleto().getProcesosProduccion()) {
                        if (nodo.getProcesoProduccion() != null) {
                            // Extraer el nombre del proceso específicamente del nodo
                            nombresProcesos.add(nodo.getProcesoProduccion().getNombre());
                        }
                    }
                }
            }
        }

        return new ArrayList<>(nombresProcesos);
    }
}
