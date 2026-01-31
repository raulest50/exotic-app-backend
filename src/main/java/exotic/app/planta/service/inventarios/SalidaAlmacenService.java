package exotic.app.planta.service.inventarios;


import exotic.app.planta.model.contabilidad.AsientoContable;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.*;
import exotic.app.planta.model.producto.manufacturing.packaging.dto.CasePackResponseDTO;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.packaging.CasePack;
import exotic.app.planta.model.producto.manufacturing.packaging.InsumoEmpaque;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.OrdenSeguimiento;
import exotic.app.planta.model.produccion.dto.DispensacionFormularioDTO;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.OrdenSeguimientoRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.model.producto.dto.InsumoWithStockDTO;
import exotic.app.planta.service.contabilidad.ContabilidadService;
import exotic.app.planta.service.produccion.ProduccionService;
import exotic.app.planta.service.productos.ProductoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalidaAlmacenService {

    private final OrdenProduccionRepo ordenProduccionRepo;
    private final OrdenSeguimientoRepo ordenSeguimientoRepo;
    private final ProductoRepo productoRepo;
    private final LoteRepo loteRepo;
    private final UserRepository userRepository;
    private final ContabilidadService contabilidadService;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;
    private final ProduccionService produccionService;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final ProductoService productoService;

    /**
     * Obtiene el formulario sugerido de dispensación delegando al servicio de producción.
     *
     * @param ordenProduccionId identificador de la orden de producción
     * @return formulario con materiales y lotes recomendados
     */
    public DispensacionFormularioDTO getFormularioDispensacion(int ordenProduccionId) {
        return produccionService.getFormularioDispensacion(ordenProduccionId);
    }


    /**
     * Creates a dispensation transaction for a production order.
     * This method handles the dispensation of materials from the warehouse to execute production orders.
     *
     * @param dispensacionDTO The DTO containing the dispensation information
     * @return The created transaction
     */
    @Transactional
    public TransaccionAlmacen createDispensacion(DispensacionDTO dispensacionDTO) {
        // Obtain the production order
        OrdenProduccion ordenProduccion = ordenProduccionRepo.findById(dispensacionDTO.getOrdenProduccionId())
                .orElseThrow(() -> new RuntimeException("Orden de producción no encontrada con ID: " + dispensacionDTO.getOrdenProduccionId()));

        // Validar que la orden no esté en estado TERMINADA (2) o CANCELADA (-1)
        if (ordenProduccion.getEstadoOrden() == 2 || ordenProduccion.getEstadoOrden() == -1) {
            throw new IllegalStateException("No se puede realizar dispensación para una orden " + 
                (ordenProduccion.getEstadoOrden() == 2 ? "TERMINADA" : "CANCELADA") + 
                ". Estado actual: " + ordenProduccion.getEstadoOrden());
        }

        // Create the warehouse transaction
        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setTipoEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.OD);
        transaccion.setIdEntidadCausante(ordenProduccion.getOrdenId());
        transaccion.setObservaciones(dispensacionDTO.getObservaciones());

        // Asignar usuarios responsables si se proporcionan
        if (dispensacionDTO.getUsuarioRealizadorIds() != null && !dispensacionDTO.getUsuarioRealizadorIds().isEmpty()) {
            List<Long> usuarioIds = dispensacionDTO.getUsuarioRealizadorIds().stream()
                    .map(Integer::longValue)
                    .collect(Collectors.toList());
            List<User> usuariosRealizadores = userRepository.findAllById(usuarioIds);
            if (usuariosRealizadores.size() != dispensacionDTO.getUsuarioRealizadorIds().size()) {
                log.warn("Algunos usuarios realizadores no fueron encontrados. Esperados: {}, Encontrados: {}", 
                        dispensacionDTO.getUsuarioRealizadorIds().size(), usuariosRealizadores.size());
            }
            transaccion.setUsuariosResponsables(usuariosRealizadores);

            // Para compatibilidad, usar el primer usuario realizador como usuarioAprobador
            if (!usuariosRealizadores.isEmpty()) {
                transaccion.setUsuarioAprobador(usuariosRealizadores.get(0));
            }
        } else {
            // Si no hay usuarios realizadores, usar el usuarioId para compatibilidad
            User user = userRepository.findById(Long.valueOf(dispensacionDTO.getUsuarioId()))
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + dispensacionDTO.getUsuarioId()));
            transaccion.setUsuarioAprobador(user);
        }

        // Asignar usuario aprobador si se proporciona
        if (dispensacionDTO.getUsuarioAprobadorId() != null) {
            User usuarioAprobador = userRepository.findById(Long.valueOf(dispensacionDTO.getUsuarioAprobadorId()))
                    .orElseThrow(() -> new RuntimeException("Usuario aprobador no encontrado con ID: " + dispensacionDTO.getUsuarioAprobadorId()));
            transaccion.setUsuarioAprobador(usuarioAprobador);
        }

        // Create the movements
        List<Movimiento> movimientos = new ArrayList<>();
        for (DispensacionItemDTO item : dispensacionDTO.getItems()) {
            // Get the product - from seguimiento if available, otherwise from lote or direct lookup
            Producto producto = null;
            OrdenSeguimiento seguimiento = null;

            // Try to get the seguimiento if seguimientoId is provided and valid (> 0)
            if (item.getSeguimientoId() > 0) {
                Optional<OrdenSeguimiento> seguimientoOpt = ordenSeguimientoRepo.findById(item.getSeguimientoId());
                if (seguimientoOpt.isPresent()) {
                    seguimiento = seguimientoOpt.get();
                    producto = seguimiento.getInsumo().getProducto();
                } else {
                    log.warn("Seguimiento no encontrado con ID: {}. Se buscará el producto por otros medios.", item.getSeguimientoId());
                }
            }

            // If producto is still null, try to get it from productoId (when seguimientoId is 0)
            if (producto == null && (item.getSeguimientoId() == 0 || item.getSeguimientoId() < 0)) {
                if (item.getProductoId() != null && !item.getProductoId().isEmpty()) {
                    Optional<Producto> productoOpt = productoRepo.findById(item.getProductoId());
                    if (productoOpt.isPresent()) {
                        producto = productoOpt.get();
                    } else {
                        throw new RuntimeException("Producto no encontrado con ID: " + item.getProductoId());
                    }
                } else {
                    throw new RuntimeException("No se pudo determinar el producto para el item. Se requiere seguimientoId válido (> 0) o productoId válido cuando seguimientoId es 0.");
                }
            }

            // If still null, we need to throw an error
            if (producto == null) {
                throw new RuntimeException("No se pudo determinar el producto para el item. Se requiere seguimientoId válido (> 0) o productoId válido.");
            }

            // Create the movement
            Movimiento movimiento = new Movimiento();
            movimiento.setCantidad(-item.getCantidad()); // Negative because it's an output
            movimiento.setProducto(producto);
            movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.CONSUMO);
            movimiento.setAlmacen(Movimiento.Almacen.GENERAL);
            movimiento.setTransaccionAlmacen(transaccion);

            // If a batch is specified, associate it
            if (item.getLoteId() != null) {
                Lote lote = loteRepo.findById(Long.valueOf(item.getLoteId()))
                        .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + item.getLoteId()));
                movimiento.setLote(lote);
            }

            movimientos.add(movimiento);

            // Update the tracking status if necessary and seguimiento exists
            if (seguimiento != null && item.isCompletarSeguimiento()) {
                seguimiento.setEstado(1); // Finished
                seguimiento.setFechaFinalizacion(LocalDateTime.now());
                ordenSeguimientoRepo.save(seguimiento);
            }
        }

        transaccion.setMovimientosTransaccion(movimientos);

        // Save the transaction
        TransaccionAlmacen transaccionGuardada = transaccionAlmacenHeaderRepo.save(transaccion);

        // Logica de cambio de estado por dispensacion de materiales
        int estadoActual = ordenProduccion.getEstadoOrden();
        int nuevoEstado;

        if (estadoActual == 0) {
            // Si es la primera dispensación (estado 0), cambiar a estado 11
            nuevoEstado = 11;
        } else if (estadoActual >= 11) {
            // Si ya hubo dispensaciones previas, incrementar el estado para indicar ajustes
            nuevoEstado = estadoActual + 1;
        } else {
            // En otros casos (estados negativos o no esperados), no cambiar el estado
            nuevoEstado = estadoActual;
            log.warn("Estado de orden de producción no esperado para dispensación: {}. No se cambiará el estado.", estadoActual);
        }

        // Solo actualizar si hay cambio de estado
        if (nuevoEstado != estadoActual) {
            produccionService.updateEstadoOrdenProduccion(ordenProduccion.getOrdenId(), nuevoEstado);
            log.info("Actualizado estado de orden de producción {} de {} a {}", ordenProduccion.getOrdenId(), estadoActual, nuevoEstado);
        }

        // Logica contable dispensacion
        // Pendiente implementación de lógica contable para dispensaciones

        return transaccionGuardada;
    }


    /**
     * Recomienda lotes para dispensación de un producto específico.
     * Utiliza la lógica de selección de lotes por fecha de vencimiento (FEFO).
     *
     * @param productoId ID del producto a dispensar
     * @param cantidadRequerida Cantidad total requerida
     * @return DTO con los items recomendados para dispensación
     */
    public DispensacionNoPlanificadaDTO recomendarLotesParaDispensacion(String productoId, double cantidadRequerida) {
        // Verificar que el producto existe
        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + productoId));

        // Obtener lotes con stock disponible para este producto
        List<Object[]> lotesConStock;
        try {
            // Intentar primero con la consulta JPQL
            lotesConStock = transaccionAlmacenRepo.findLotesWithStockByProductoIdOrderByExpirationDate(productoId);
        } catch (Exception e) {
            // Si falla, usar la consulta SQL nativa como alternativa
            log.warn("Error al ejecutar consulta JPQL para lotes, usando SQL nativo: " + e.getMessage());
            lotesConStock = transaccionAlmacenRepo.findLotesWithStockByProductoIdNative(productoId);
        }

        // Crear el DTO de respuesta
        DispensacionNoPlanificadaDTO dispensacionDTO = new DispensacionNoPlanificadaDTO();
        dispensacionDTO.setObservaciones("Recomendación automática de lotes para " + producto.getNombre());
        dispensacionDTO.setItems(new ArrayList<>());

        // Calcular cuánto tomar de cada lote
        double cantidadRestante = cantidadRequerida;

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

                // Crear un item de dispensación para este lote
                DispensacionNoPlanificadaItemDTO item = new DispensacionNoPlanificadaItemDTO();
                item.setProductoId(productoId);
                item.setCantidad(cantidadAUsar);
                item.setLoteId(lote.getId().intValue());

                dispensacionDTO.getItems().add(item);

                cantidadRestante -= cantidadAUsar;
            } catch (Exception e) {
                log.error("Error al procesar lote recomendado: " + e.getMessage(), e);
                // Continuamos con el siguiente lote
            }
        }

        // Verificar si se pudo satisfacer toda la cantidad requerida
        if (cantidadRestante > 0) {
            log.warn("No hay suficiente stock para satisfacer la cantidad requerida. Faltante: " + cantidadRestante);
            dispensacionDTO.setObservaciones(dispensacionDTO.getObservaciones() +
                    ". ADVERTENCIA: Stock insuficiente. Faltante: " + cantidadRestante);
        }

        return dispensacionDTO;
    }


    /**
     * Obtiene los lotes disponibles para un producto específico.
     * Incluye información de fecha de vencimiento y cantidad disponible para cada lote.
     *
     * @param productoId ID del producto
     * @return DTO con la información de lotes disponibles
     */
    public LoteDisponibleResponseDTO getLotesDisponiblesByProductoId(String productoId) {
        // Verificar que el producto existe
        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + productoId));

        // Obtener lotes con stock disponible para este producto
        List<Object[]> lotesConStock;
        try {
            // Intentar primero con la consulta JPQL
            lotesConStock = transaccionAlmacenRepo.findLotesWithStockByProductoIdOrderByExpirationDate(productoId);
        } catch (Exception e) {
            // Si falla, usar la consulta SQL nativa como alternativa
            log.warn("Error al ejecutar consulta JPQL para lotes, usando SQL nativo: " + e.getMessage());
            lotesConStock = transaccionAlmacenRepo.findLotesWithStockByProductoIdNative(productoId);
        }

        // Crear el DTO de respuesta
        LoteDisponibleResponseDTO responseDTO = new LoteDisponibleResponseDTO();
        responseDTO.setProductoId(productoId);
        responseDTO.setNombreProducto(producto.getNombre());

        List<LoteRecomendadoDTO> lotesDisponibles = new ArrayList<>();

        // Procesar cada lote con stock
        for (Object[] result : lotesConStock) {
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

                // Crear DTO para este lote
                LoteRecomendadoDTO loteDTO = new LoteRecomendadoDTO();
                loteDTO.setLoteId(lote.getId());
                loteDTO.setBatchNumber(lote.getBatchNumber());
                loteDTO.setProductionDate(lote.getProductionDate());
                loteDTO.setExpirationDate(lote.getExpirationDate());
                loteDTO.setCantidadDisponible(stockDisponible);
                loteDTO.setCantidadRecomendada(0); // No estamos recomendando cantidades

                lotesDisponibles.add(loteDTO);
            } catch (Exception e) {
                log.error("Error al procesar lote disponible: " + e.getMessage(), e);
                // Continuamos con el siguiente lote
            }
        }

        responseDTO.setLotesDisponibles(lotesDisponibles);
        return responseDTO;
    }

    /**
     * Obtiene los lotes disponibles para un producto específico con paginación.
     * Incluye información de fecha de vencimiento y cantidad disponible para cada lote.
     * Solo retorna lotes con stock disponible mayor a 0.
     *
     * @param productoId ID del producto
     * @param page Número de página (base 0)
     * @param size Tamaño de página
     * @return DTO paginado con la información de lotes disponibles
     */
    public LoteDisponiblePageResponseDTO getLotesDisponiblesByProductoIdPaginated(String productoId, int page, int size) {
        // Verificar que el producto existe
        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + productoId));

        // Obtener todos los lotes con stock disponible para este producto
        List<Object[]> todosLotesConStock;
        try {
            // Intentar primero con la consulta JPQL
            todosLotesConStock = transaccionAlmacenRepo.findLotesWithStockByProductoIdOrderByExpirationDate(productoId);
        } catch (Exception e) {
            // Si falla, usar la consulta SQL nativa como alternativa
            log.warn("Error al ejecutar consulta JPQL para lotes, usando SQL nativo: " + e.getMessage());
            todosLotesConStock = transaccionAlmacenRepo.findLotesWithStockByProductoIdNative(productoId);
        }

        // Crear el DTO de respuesta
        LoteDisponiblePageResponseDTO responseDTO = new LoteDisponiblePageResponseDTO();
        responseDTO.setProductoId(productoId);
        responseDTO.setNombreProducto(producto.getNombre());

        List<LoteRecomendadoDTO> todosLotesDisponibles = new ArrayList<>();

        // Procesar cada lote con stock y filtrar solo los que tienen cantidad > 0
        for (Object[] result : todosLotesConStock) {
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

                // Filtrar solo lotes con stock disponible mayor a 0 (doble verificación)
                if (stockDisponible == null || stockDisponible <= 0) {
                    continue;
                }

                // Crear DTO para este lote
                LoteRecomendadoDTO loteDTO = new LoteRecomendadoDTO();
                loteDTO.setLoteId(lote.getId());
                loteDTO.setBatchNumber(lote.getBatchNumber());
                loteDTO.setProductionDate(lote.getProductionDate());
                loteDTO.setExpirationDate(lote.getExpirationDate());
                loteDTO.setCantidadDisponible(stockDisponible);
                loteDTO.setCantidadRecomendada(0); // No estamos recomendando cantidades

                todosLotesDisponibles.add(loteDTO);
            } catch (Exception e) {
                log.error("Error al procesar lote disponible: " + e.getMessage(), e);
                // Continuamos con el siguiente lote
            }
        }

        // Calcular información de paginación
        long totalElements = todosLotesDisponibles.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        // Aplicar paginación manual
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, todosLotesDisponibles.size());

        List<LoteRecomendadoDTO> lotesPaginados = startIndex < todosLotesDisponibles.size() 
            ? todosLotesDisponibles.subList(startIndex, endIndex)
            : new ArrayList<>();

        // Configurar respuesta paginada
        responseDTO.setLotesDisponibles(lotesPaginados);
        responseDTO.setTotalPages(totalPages);
        responseDTO.setTotalElements(totalElements);
        responseDTO.setCurrentPage(page);
        responseDTO.setSize(size);

        return responseDTO;
    }


    /*  PARA DISPENSACIONES NO PLANIFICADAS. Durante una etapa de desarrollo mas temprana se contemplo brindar
     * soporte para transacciones de almacen planificadas y no planificadas, es decir ingresos y salidas de
     * almacen arbitrarios. Sin embargo recientemente la empresa desea certificarse en un año en BPM, buenas
     * practicas de manufactura y portanto ya no se deberia hacer ninguna transaccion de almacen que no este
     * soportada en una orden de compra, de produccion o similar. sin embargo dejo los metodos a continuacion
     * por si se necesitan para una referencia futura o si depronto toca usarlos pero en teoria se deberia.
     */

    /**
     * Creates an unplanned dispensation transaction without a production order.
     * This method checks if unplanned dispensation is allowed by system configuration.
     *
     * @param dispensacionDTO The DTO containing the dispensation information
     * @return The created transaction
     */
    @Transactional
    public TransaccionAlmacen createDispensacionNoPlanificada(DispensacionNoPlanificadaDTO dispensacionDTO) {
        // Dispensación no planificada no permitida por defecto (directiva eliminada; ver Super Master si se reintroduce).
        throw new RuntimeException("La dispensación no planificada no está permitida según la configuración del sistema");
    }


    /**
     * Recomienda lotes para dispensación de múltiples productos.
     * Procesa cada solicitud individualmente y combina los resultados en un solo DTO.
     *
     * @param items Lista de solicitudes de recomendación (producto y cantidad)
     * @return DTO con todos los items recomendados para dispensación
     */
    public DispensacionNoPlanificadaDTO recomendarLotesParaDispensacionMultiple(List<RecomendacionLotesRequestDTO> items) {
        // Crear el DTO de respuesta
        DispensacionNoPlanificadaDTO dispensacionDTO = new DispensacionNoPlanificadaDTO();
        dispensacionDTO.setObservaciones("Recomendación automática de lotes para múltiples productos");
        dispensacionDTO.setItems(new ArrayList<>());

        // Procesar cada solicitud
        for (RecomendacionLotesRequestDTO item : items) {
            try {
                // Obtener recomendación para este producto
                DispensacionNoPlanificadaDTO recomendacionIndividual = recomendarLotesParaDispensacion(
                        item.getProductoId(), item.getCantidad());

                // Agregar los items recomendados a la respuesta
                dispensacionDTO.getItems().addAll(recomendacionIndividual.getItems());

                // Si hubo alguna advertencia, agregarla a las observaciones
                if (recomendacionIndividual.getObservaciones().contains("ADVERTENCIA")) {
                    dispensacionDTO.setObservaciones(dispensacionDTO.getObservaciones() +
                            "\n" + recomendacionIndividual.getObservaciones());
                }
            } catch (Exception e) {
                log.error("Error al procesar recomendación para producto " + item.getProductoId() + ": " + e.getMessage(), e);
                dispensacionDTO.setObservaciones(dispensacionDTO.getObservaciones() +
                        "\nError al procesar producto " + item.getProductoId() + ": " + e.getMessage());
            }
        }

        return dispensacionDTO;
    }

    /**
     * Obtiene la lista completa desglosada de todos los materiales base necesarios
     * para una orden de producción, descomponiendo recursivamente los semiterminados.
     * Incluye también los materiales de empaque del CasePack.
     * 
     * @param ordenProduccionId ID de la orden de producción
     * @return DTO con insumos de receta e insumos de empaque
     */
    public InsumosDesglosadosResponseDTO getInsumosDesglosados(int ordenProduccionId) {
        // Obtener la orden de producción
        OrdenProduccion ordenProduccion = ordenProduccionRepo.findById(ordenProduccionId)
            .orElseThrow(() -> new RuntimeException("Orden de producción no encontrada con ID: " + ordenProduccionId));

        // Obtener el producto terminado de la orden
        Producto producto = ordenProduccion.getProducto();
        if (!(producto instanceof Terminado)) {
            throw new RuntimeException("El producto de la orden de producción debe ser un Terminado");
        }

        Terminado terminado = (Terminado) producto;

        // Obtener la cantidad de la orden (multiplicador)
        double cantidadOrden = ordenProduccion.getCantidadProducir();

        // Obtener insumos desglosados recursivamente del producto terminado
        List<InsumoWithStockDTO> insumosRecursivos = productoService.getInsumosWithStock(producto.getProductoId());

        // Aplanar la estructura recursiva y consolidar por producto
        Map<String, InsumoDesglosadoDTO> insumosConsolidados = new HashMap<>();

        aplanarInsumos(insumosRecursivos, insumosConsolidados, cantidadOrden, 1.0);

        // Obtener materiales de empaque
        List<InsumoDesglosadoDTO> insumosEmpaque = obtenerInsumosEmpaque(terminado, cantidadOrden);

        // Crear respuesta con ambas listas
        InsumosDesglosadosResponseDTO response = new InsumosDesglosadosResponseDTO();
        response.setInsumosReceta(new ArrayList<>(insumosConsolidados.values()));
        response.setInsumosEmpaque(insumosEmpaque);

        return response;
    }

    /**
     * Endpoint combinado creado para soportar dispensaciones parciales y visualización por semiterminados.
     * Históricamente se aplanaban insumos, pero el proceso requiere preservar la jerarquía y
     * mostrar historial de dispensaciones asociadas a la ODP en una sola llamada.
     */
    public DispensacionResumenDTO getDispensacionResumen(int ordenProduccionId) {
        OrdenProduccion ordenProduccion = ordenProduccionRepo.findById(ordenProduccionId)
                .orElseThrow(() -> new RuntimeException("Orden de producción no encontrada con ID: " + ordenProduccionId));

        Producto producto = ordenProduccion.getProducto();
        if (!(producto instanceof Terminado)) {
            throw new RuntimeException("El producto de la orden de producción debe ser un Terminado");
        }

        Terminado terminado = (Terminado) producto;
        double cantidadOrden = ordenProduccion.getCantidadProducir();

        List<InsumoWithStockDTO> insumosRecursivos = productoService.getInsumosWithStock(producto.getProductoId());
        List<InsumoRecursivoDTO> insumosReceta = construirInsumosRecursivos(insumosRecursivos, cantidadOrden, 1.0);

        List<InsumoDesglosadoDTO> insumosEmpaque = obtenerInsumosEmpaqueBase(terminado);
        CasePackResponseDTO casePack = CasePackResponseDTO.fromCasePack(terminado.getCasePack());

        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OD,
                        ordenProduccionId
                );
        List<TransaccionAlmacenDetalleDTO> historial = transacciones.stream()
                .map(this::convertirATransaccionAlmacenDetalleDTO)
                .collect(Collectors.toList());

        DispensacionResumenDTO response = new DispensacionResumenDTO();
        response.setInsumosReceta(insumosReceta);
        response.setInsumosEmpaque(insumosEmpaque);
        response.setCasePack(casePack);
        response.setHistorialDispensaciones(historial);
        return response;
    }

    /**
     * Obtiene los materiales de empaque del CasePack del producto terminado.
     * 
     * @param terminado Producto terminado con CasePack
     * @param cantidadOrden Cantidad a producir de la orden
     * @return Lista de materiales de empaque desglosados
     */
    private List<InsumoDesglosadoDTO> obtenerInsumosEmpaque(Terminado terminado, double cantidadOrden) {
        List<InsumoDesglosadoDTO> insumosEmpaque = new ArrayList<>();

        // Verificar si el terminado tiene CasePack y materiales de empaque
        if (terminado.getCasePack() == null || terminado.getCasePack().getInsumosEmpaque() == null) {
            return insumosEmpaque; // Retornar lista vacía si no hay materiales de empaque
        }

        CasePack casePack = terminado.getCasePack();
        
        for (InsumoEmpaque insumoEmpaque : casePack.getInsumosEmpaque()) {
            Material material = insumoEmpaque.getMaterial();
            if (material == null) {
                continue; // Saltar si el material es null
            }

            // Calcular cantidad total: cantidad del insumo de empaque * cantidad de la orden
            double cantidadTotal = insumoEmpaque.getCantidad() * cantidadOrden;

            // Crear DTO para el material de empaque
            InsumoDesglosadoDTO dto = new InsumoDesglosadoDTO();
            dto.setProductoId(material.getProductoId());
            dto.setProductoNombre(material.getNombre());
            dto.setCantidadTotalRequerida(cantidadTotal);
            // Usar uom del insumo de empaque si existe, sino usar tipoUnidades del material
            dto.setTipoUnidades(insumoEmpaque.getUom() != null && !insumoEmpaque.getUom().isEmpty() 
                ? insumoEmpaque.getUom() 
                : (material.getTipoUnidades() != null ? material.getTipoUnidades() : "U"));
            dto.setTipoProducto("MATERIAL_EMPAQUE"); // Diferenciador para materiales de empaque
            dto.setInventareable(material.isInventareable());
            dto.setSeguimientoId(null); // Los materiales de empaque no tienen seguimientoId

            insumosEmpaque.add(dto);
        }

        return insumosEmpaque;
    }

    /**
     * Obtiene materiales de empaque sin aplicar el multiplicador de la orden (cantidad base).
     */
    private List<InsumoDesglosadoDTO> obtenerInsumosEmpaqueBase(Terminado terminado) {
        List<InsumoDesglosadoDTO> insumosEmpaque = new ArrayList<>();
        if (terminado.getCasePack() == null || terminado.getCasePack().getInsumosEmpaque() == null) {
            return insumosEmpaque;
        }

        CasePack casePack = terminado.getCasePack();
        for (InsumoEmpaque insumoEmpaque : casePack.getInsumosEmpaque()) {
            Material material = insumoEmpaque.getMaterial();
            if (material == null) {
                continue;
            }

            InsumoDesglosadoDTO dto = new InsumoDesglosadoDTO();
            dto.setProductoId(material.getProductoId());
            dto.setProductoNombre(material.getNombre());
            dto.setCantidadTotalRequerida(insumoEmpaque.getCantidad());
            dto.setTipoUnidades(insumoEmpaque.getUom() != null && !insumoEmpaque.getUom().isEmpty()
                    ? insumoEmpaque.getUom()
                    : (material.getTipoUnidades() != null ? material.getTipoUnidades() : "U"));
            dto.setTipoProducto("MATERIAL_EMPAQUE");
            dto.setInventareable(material.isInventareable());
            dto.setSeguimientoId(null);
            insumosEmpaque.add(dto);
        }

        return insumosEmpaque;
    }

    private List<InsumoRecursivoDTO> construirInsumosRecursivos(
            List<InsumoWithStockDTO> insumos,
            double cantidadOrden,
            double multiplicadorActual
    ) {
        List<InsumoRecursivoDTO> resultado = new ArrayList<>();
        for (InsumoWithStockDTO insumo : insumos) {
            double cantidadTotal = insumo.getCantidadRequerida() * cantidadOrden * multiplicadorActual;
            InsumoRecursivoDTO dto = new InsumoRecursivoDTO();
            dto.setInsumoId(insumo.getInsumoId());
            dto.setProductoId(insumo.getProductoId());
            dto.setProductoNombre(insumo.getProductoNombre());
            dto.setCantidadTotalRequerida(cantidadTotal);
            dto.setTipoUnidades(insumo.getTipoUnidades() != null ? insumo.getTipoUnidades() : "KG");
            dto.setTipoProducto(
                    insumo.getTipoProducto() == InsumoWithStockDTO.TipoProducto.M
                            ? "MATERIAL"
                            : "SEMITERMINADO"
            );
            dto.setInventareable(insumo.getInventareable() != null ? insumo.getInventareable() : true);

            if (insumo.getSubInsumos() != null && !insumo.getSubInsumos().isEmpty()) {
                double nuevoMultiplicador = multiplicadorActual * insumo.getCantidadRequerida();
                dto.setSubInsumos(construirInsumosRecursivos(insumo.getSubInsumos(), cantidadOrden, nuevoMultiplicador));
            }
            resultado.add(dto);
        }
        return resultado;
    }

    private TransaccionAlmacenDetalleDTO convertirATransaccionAlmacenDetalleDTO(TransaccionAlmacen transaccion) {
        TransaccionAlmacenResponseDTO base = convertirATransaccionAlmacenResponseDTO(transaccion);
        TransaccionAlmacenDetalleDTO detalle = new TransaccionAlmacenDetalleDTO();
        detalle.setTransaccionId(base.getTransaccionId());
        detalle.setFechaTransaccion(base.getFechaTransaccion());
        detalle.setIdEntidadCausante(base.getIdEntidadCausante());
        detalle.setTipoEntidadCausante(base.getTipoEntidadCausante());
        detalle.setObservaciones(base.getObservaciones());
        detalle.setEstadoContable(base.getEstadoContable());
        detalle.setUsuarioAprobador(base.getUsuarioAprobador());

        List<MovimientoDetalleDTO> movimientos = (transaccion.getMovimientosTransaccion() == null)
                ? new ArrayList<>()
                : transaccion.getMovimientosTransaccion().stream()
                    .map(movimiento -> {
                        MovimientoDetalleDTO dto = new MovimientoDetalleDTO();
                        dto.setMovimientoId(movimiento.getMovimientoId());
                        if (movimiento.getProducto() != null) {
                            dto.setProductoId(movimiento.getProducto().getProductoId());
                            dto.setProductoNombre(movimiento.getProducto().getNombre());
                            dto.setTipoUnidades(movimiento.getProducto().getTipoUnidades());
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
        detalle.setMovimientos(movimientos);
        return detalle;
    }

    /**
     * Método recursivo para aplanar la estructura de insumos y consolidar cantidades
     */
    private void aplanarInsumos(
        List<InsumoWithStockDTO> insumos, 
        Map<String, InsumoDesglosadoDTO> consolidado,
        double cantidadOrden,
        double multiplicadorActual
    ) {
        for (InsumoWithStockDTO insumo : insumos) {
            double cantidadTotal = insumo.getCantidadRequerida() * cantidadOrden * multiplicadorActual;

            // Si tiene subinsumos (es semiterminado), procesarlos recursivamente
            if (insumo.getSubInsumos() != null && !insumo.getSubInsumos().isEmpty()) {
                // Multiplicador para los subinsumos: cantidad requerida del semiterminado
                double nuevoMultiplicador = multiplicadorActual * insumo.getCantidadRequerida();
                aplanarInsumos(insumo.getSubInsumos(), consolidado, cantidadOrden, nuevoMultiplicador);
            } else {
                // Es un material base, agregarlo o consolidar cantidad
                String productoId = insumo.getProductoId();

                if (consolidado.containsKey(productoId)) {
                    // Sumar a la cantidad existente
                    InsumoDesglosadoDTO existente = consolidado.get(productoId);
                    existente.setCantidadTotalRequerida(
                        existente.getCantidadTotalRequerida() + cantidadTotal
                    );
                } else {
                    // Crear nuevo registro
                    InsumoDesglosadoDTO nuevo = new InsumoDesglosadoDTO();
                    nuevo.setProductoId(insumo.getProductoId());
                    nuevo.setProductoNombre(insumo.getProductoNombre());
                    nuevo.setCantidadTotalRequerida(cantidadTotal);
                    nuevo.setTipoUnidades(insumo.getTipoUnidades() != null ? insumo.getTipoUnidades() : "KG");
                    nuevo.setTipoProducto(
                        insumo.getTipoProducto() == InsumoWithStockDTO.TipoProducto.M 
                            ? "MATERIAL" 
                            : "SEMITERMINADO"
                    );
                    nuevo.setInventareable(insumo.getInventareable() != null ? insumo.getInventareable() : true);
                    consolidado.put(productoId, nuevo);
                }
            }
        }
    }

    /**
     * Busca dispensaciones (transacciones tipo OP) con filtros flexibles.
     * Permite filtrar por ID de transacción, ID de orden de producción, y fechas (rango o específica).
     *
     * @param filtro DTO con los criterios de búsqueda
     * @return Página de transacciones que cumplen con los filtros
     * @throws RuntimeException si no se proporciona ningún filtro activo
     */
    public Page<TransaccionAlmacen> buscarDispensacionesFiltradas(FiltroHistDispensacionDTO filtro) {
        // Construir Pageable con ordenamiento por fechaTransaccion DESC
        Pageable pageable = PageRequest.of(
                filtro.getPage(),
                filtro.getSize(),
                Sort.by("fechaTransaccion").descending()
        );

        // Determinar qué filtros están activos (tipo > 0)
        boolean tieneFiltroId = (filtro.getTipoFiltroId() != null && filtro.getTipoFiltroId() > 0);
        boolean tieneFiltroFecha = (filtro.getTipoFiltroFecha() != null && filtro.getTipoFiltroFecha() > 0);

        // Si ambos filtros están en "Ninguno" (0), usar método simple que trae todos los registros
        if (!tieneFiltroId && !tieneFiltroFecha) {
            return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteOrderByFechaTransaccionDesc(
                    TransaccionAlmacen.TipoEntidadCausante.OD,
                    pageable
            );
        }

        // Validar y preparar parámetros de fecha (si hay filtro de fecha activo)
        LocalDateTime fechaInicio = null;
        LocalDateTime fechaFin = null;
        
        if (tieneFiltroFecha) {
            if (filtro.getTipoFiltroFecha() == 1) {
                // Rango de fechas - si faltan fechas, usar método simple
                if (filtro.getFechaInicio() == null || filtro.getFechaFin() == null) {
                    return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteOrderByFechaTransaccionDesc(
                            TransaccionAlmacen.TipoEntidadCausante.OD,
                            pageable
                    );
                }
                if (filtro.getFechaInicio().isAfter(filtro.getFechaFin())) {
                    throw new RuntimeException("La fecha de inicio no puede ser posterior a la fecha de fin");
                }
                fechaInicio = filtro.getFechaInicio().atStartOfDay();
                fechaFin = filtro.getFechaFin().atTime(23, 59, 59, 999999999);
            } else if (filtro.getTipoFiltroFecha() == 2) {
                // Fecha específica - si falta fecha, usar método simple
                if (filtro.getFechaEspecifica() == null) {
                    return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteOrderByFechaTransaccionDesc(
                            TransaccionAlmacen.TipoEntidadCausante.OD,
                            pageable
                    );
                }
                fechaInicio = filtro.getFechaEspecifica().atStartOfDay();
                fechaFin = filtro.getFechaEspecifica().atTime(23, 59, 59, 999999999);
            }
        }

        // Caso 1: Solo filtro de ID (transaccionId)
        if (tieneFiltroId && !tieneFiltroFecha) {
            if (filtro.getTipoFiltroId() == 1) {
                Integer transaccionId = filtro.getTransaccionId();
                // Si el ID está vacío o inválido, usar método simple
                if (transaccionId == null || transaccionId <= 0) {
                    return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteOrderByFechaTransaccionDesc(
                            TransaccionAlmacen.TipoEntidadCausante.OD,
                            pageable
                    );
                }
                return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteAndTransaccionId(
                        TransaccionAlmacen.TipoEntidadCausante.OD,
                        transaccionId,
                        pageable
                );
            } else if (filtro.getTipoFiltroId() == 2) {
                Integer ordenProduccionId = filtro.getOrdenProduccionId();
                // Si el ID está vacío o inválido, usar método simple
                if (ordenProduccionId == null || ordenProduccionId <= 0) {
                    return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteOrderByFechaTransaccionDesc(
                            TransaccionAlmacen.TipoEntidadCausante.OD,
                            pageable
                    );
                }
                return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteAndOrdenProduccionId(
                        TransaccionAlmacen.TipoEntidadCausante.OD,
                        ordenProduccionId,
                        pageable
                );
            }
        }

        // Caso 2: Solo filtro de fecha (ya validado arriba)
        if (!tieneFiltroId && tieneFiltroFecha) {
            return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteAndFechaBetween(
                    TransaccionAlmacen.TipoEntidadCausante.OD,
                    fechaInicio,
                    fechaFin,
                    pageable
            );
        }

        // Caso 3: Ambos filtros activos
        if (tieneFiltroId && tieneFiltroFecha) {
            if (filtro.getTipoFiltroId() == 1) {
                Integer transaccionId = filtro.getTransaccionId();
                // Si el ID está vacío o inválido, usar solo filtro de fecha
                if (transaccionId == null || transaccionId <= 0) {
                    return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteAndFechaBetween(
                            TransaccionAlmacen.TipoEntidadCausante.OD,
                            fechaInicio,
                            fechaFin,
                            pageable
                    );
                }
                return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteAndTransaccionIdAndFechaBetween(
                        TransaccionAlmacen.TipoEntidadCausante.OD,
                        transaccionId,
                        fechaInicio,
                        fechaFin,
                        pageable
                );
            } else if (filtro.getTipoFiltroId() == 2) {
                Integer ordenProduccionId = filtro.getOrdenProduccionId();
                // Si el ID está vacío o inválido, usar solo filtro de fecha
                if (ordenProduccionId == null || ordenProduccionId <= 0) {
                    return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteAndFechaBetween(
                            TransaccionAlmacen.TipoEntidadCausante.OD,
                            fechaInicio,
                            fechaFin,
                            pageable
                    );
                }
                return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteAndOrdenProduccionIdAndFechaBetween(
                        TransaccionAlmacen.TipoEntidadCausante.OD,
                        ordenProduccionId,
                        fechaInicio,
                        fechaFin,
                        pageable
                );
            }
        }

        // Fallback: retornar todas las dispensaciones (método simple)
        return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteOrderByFechaTransaccionDesc(
                TransaccionAlmacen.TipoEntidadCausante.OD,
                pageable
        );
    }

    /**
     * Convierte una entidad TransaccionAlmacen a su DTO de respuesta.
     * Evita relaciones circulares que causan problemas de serialización JSON.
     *
     * @param transaccion Entidad TransaccionAlmacen a convertir
     * @return DTO sin relaciones circulares
     */
    private TransaccionAlmacenResponseDTO convertirATransaccionAlmacenResponseDTO(TransaccionAlmacen transaccion) {
        TransaccionAlmacenResponseDTO dto = new TransaccionAlmacenResponseDTO();
        dto.setTransaccionId(transaccion.getTransaccionId());
        dto.setFechaTransaccion(transaccion.getFechaTransaccion());
        dto.setIdEntidadCausante(transaccion.getIdEntidadCausante());
        dto.setTipoEntidadCausante(transaccion.getTipoEntidadCausante() != null 
                ? transaccion.getTipoEntidadCausante().name() 
                : null);
        dto.setObservaciones(transaccion.getObservaciones());
        dto.setEstadoContable(transaccion.getEstadoContable() != null 
                ? transaccion.getEstadoContable().name() 
                : null);

        // Convertir usuario aprobador si existe
        if (transaccion.getUsuarioAprobador() != null) {
            TransaccionAlmacenResponseDTO.UsuarioAprobadorDTO usuarioDTO = 
                    new TransaccionAlmacenResponseDTO.UsuarioAprobadorDTO();
            usuarioDTO.setUserId(transaccion.getUsuarioAprobador().getId());
            usuarioDTO.setNombre(transaccion.getUsuarioAprobador().getNombreCompleto());
            dto.setUsuarioAprobador(usuarioDTO);
        }

        return dto;
    }

    /**
     * Busca dispensaciones filtradas y las convierte a DTOs para evitar problemas de serialización.
     *
     * @param filtro DTO con los criterios de búsqueda
     * @return Página de DTOs de transacciones que cumplen con los filtros
     */
    public Page<TransaccionAlmacenResponseDTO> buscarDispensacionesFiltradasDTO(FiltroHistDispensacionDTO filtro) {
        Page<TransaccionAlmacen> resultados = buscarDispensacionesFiltradas(filtro);
        return resultados.map(this::convertirATransaccionAlmacenResponseDTO);
    }


}
