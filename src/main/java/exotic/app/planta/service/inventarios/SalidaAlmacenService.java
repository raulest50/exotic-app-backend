package exotic.app.planta.service.inventarios;


import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.*;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.produccion.EstadoDispensacionMateriales;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.HistorialDispensacionesQuery;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.produccion.ProduccionService;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalidaAlmacenService {

    private final OrdenProduccionRepo ordenProduccionRepo;
    private final ProductoRepo productoRepo;
    private final LoteRepo loteRepo;
    private final UserRepository userRepository;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;
    private final HistorialDispensacionesQuery historialDispensacionesQuery;
    private final ProduccionService produccionService;
    private final AreaProduccionRepo areaProduccionRepo;
    private final SeguimientoOrdenAreaService seguimientoOrdenAreaService;
    private final OrdenFabricacionRepo ordenFabricacionRepo;
    private final OrdenFabricacionOperacionRepo ordenFabricacionOperacionRepo;

    /** Registra una dispensación de materiales cuyo documento causante es una OF. */
    @Transactional
    public TransaccionAlmacen createDispensacionOrdenFabricacion(
            Long ordenFabricacionId,
            Integer areaOperativaDestinoId,
            List<DispensacionItemDTO> items,
            String observaciones,
            User actor
    ) {
        if (ordenFabricacionId == null || actor == null || actor.getId() == null) {
            throw new IllegalArgumentException("La OF y el usuario autenticado son obligatorios.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("La dispensacion de la OF requiere materiales.");
        }
        OrdenFabricacion orden = ordenFabricacionRepo.findByIdForUpdate(ordenFabricacionId)
                .orElseThrow(() -> new IllegalArgumentException("Orden de fabricacion no encontrada."));
        if (orden.getEstado() != EstadoOrdenFabricacion.LIBERADA
                && orden.getEstado() != EstadoOrdenFabricacion.EN_EJECUCION) {
            throw new IllegalStateException(
                    "Solo una OF liberada o en ejecucion admite dispensaciones.");
        }
        AreaOperativa areaDestino = areaProduccionRepo.findById(areaOperativaDestinoId)
                .orElseThrow(() -> new IllegalArgumentException("Area operativa destino no encontrada."));
        if (!ordenFabricacionOperacionRepo
                .existsByOrdenFabricacion_OrdenFabricacionIdAndAreaOperativa_AreaId(
                        ordenFabricacionId, areaDestino.getAreaId())) {
            throw new IllegalArgumentException(
                    "El area destino no pertenece al proceso congelado de la OF.");
        }

        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setTipoEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.OD_OF);
        transaccion.setIdEntidadCausante(Math.toIntExact(ordenFabricacionId));
        transaccion.setObservaciones(observaciones);
        transaccion.setUsuarioAprobador(actor);
        transaccion.setUsuariosResponsables(List.of(actor));

        List<Movimiento> movimientos = new ArrayList<>();
        for (DispensacionItemDTO item : items) {
            if (item == null || item.getProductoId() == null || item.getProductoId().isBlank()
                    || item.getCantidad() <= 0) {
                throw new IllegalArgumentException(
                        "Cada material de la OF requiere producto y cantidad positiva.");
            }
            Producto producto = productoRepo.findById(item.getProductoId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + item.getProductoId()));
            boolean consumoDirecto = producto instanceof Material material
                    && material.isConsumoDirecto();
            if (producto.isInventareable() == consumoDirecto) {
                throw new IllegalStateException(
                        "El producto " + producto.getProductoId()
                                + " tiene una configuracion de inventario/consumo invalida.");
            }

            Lote lote = null;
            if (consumoDirecto) {
                if (item.getLoteId() != null) {
                    throw new IllegalArgumentException(
                            "Un consumo directo no admite lote origen.");
                }
            } else {
                if (item.getLoteId() == null) {
                    throw new IllegalArgumentException(
                            "El material inventariable " + producto.getProductoId()
                                    + " requiere lote origen.");
                }
                lote = loteRepo.findById(item.getLoteId().longValue())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Lote origen no encontrado: " + item.getLoteId()));
                if (lote.getProducto() != null
                        && !producto.getProductoId().equals(lote.getProducto().getProductoId())) {
                    throw new IllegalArgumentException(
                            "El lote " + lote.getBatchNumber()
                                    + " no corresponde al producto " + producto.getProductoId() + ".");
                }
            }

            Movimiento movimiento = new Movimiento();
            movimiento.setCantidad(-item.getCantidad());
            movimiento.setProducto(producto);
            movimiento.setTipoMovimiento(consumoDirecto
                    ? Movimiento.TipoMovimiento.CONSUMO
                    : Movimiento.TipoMovimiento.DISPENSACION);
            movimiento.setAfectaInventario(!consumoDirecto);
            movimiento.setAlmacen(consumoDirecto ? null : Movimiento.Almacen.GENERAL);
            movimiento.setLote(lote);
            movimiento.setAreaOperativa(areaDestino);
            movimiento.setTransaccionAlmacen(transaccion);
            movimientos.add(movimiento);
        }
        transaccion.setMovimientosTransaccion(movimientos);
        TransaccionAlmacen guardada = transaccionAlmacenHeaderRepo.saveAndFlush(transaccion);
        orden.setEstadoDispensacionMateriales(EstadoDispensacionMateriales.PARCIAL);
        ordenFabricacionRepo.save(orden);
        return guardada;
    }

    /**
     * Creates a dispensation transaction for a production order.
     * This method handles the dispensation of materials from the warehouse to execute production orders.
     *
     * @param dispensacionDTO The DTO containing the dispensation information
     * @return The created transaction
     */
    @Transactional
    public TransaccionAlmacen createDispensacion(DispensacionDTO dispensacionDTO, Long userIdReportaSeguimiento) {
        boolean dispensacionV2Trace = isDispensacionV2Trace();
        if (dispensacionV2Trace) {
            log.info(
                    "[DISP_V2][PERSIST_SERVICE_START] ordenProduccionId={} areaDestinoId={} usuarioId={} usuarioAprobadorId={} usuarioRealizadorIds={} reporterUserId={} itemCount={} observaciones={}",
                    dispensacionDTO.getOrdenProduccionId(),
                    dispensacionDTO.getAreaOperativaDestinoId(),
                    dispensacionDTO.getUsuarioId(),
                    dispensacionDTO.getUsuarioAprobadorId(),
                    dispensacionDTO.getUsuarioRealizadorIds(),
                    userIdReportaSeguimiento,
                    dispensacionDTO.getItems() != null ? dispensacionDTO.getItems().size() : null,
                    dispensacionDTO.getObservaciones()
            );
        }
        // Obtain the production order
        OrdenProduccion ordenProduccion = ordenProduccionRepo.findById(dispensacionDTO.getOrdenProduccionId())
                .orElseThrow(() -> new RuntimeException("Orden de producción no encontrada con ID: " + dispensacionDTO.getOrdenProduccionId()));
        if (dispensacionV2Trace) {
            log.info(
                    "[DISP_V2][PERSIST_ORDER_FOUND] ordenProduccionId={} estado={} loteAsignado={} productoTerminadoId={} cantidadProducir={}",
                    ordenProduccion.getOrdenId(),
                    ordenProduccion.getEstadoOrden(),
                    ordenProduccion.getLoteAsignado(),
                    ordenProduccion.getProducto() != null ? ordenProduccion.getProducto().getProductoId() : null,
                    ordenProduccion.getCantidadProducir()
            );
        }

        // Validar que la orden no esté en estado TERMINADA (2) o CANCELADA (-1)
        if (ordenProduccion.getEstadoOrden() == 2 || ordenProduccion.getEstadoOrden() == -1) {
            throw new IllegalStateException("No se puede realizar dispensación para una orden " + 
                (ordenProduccion.getEstadoOrden() == 2 ? "TERMINADA" : "CANCELADA") + 
                ". Estado actual: " + ordenProduccion.getEstadoOrden());
        }

        AreaOperativa areaDestino = resolveAreaOperativaDestino(
                ordenProduccion,
                dispensacionDTO.getAreaOperativaDestinoId()
        );
        if (dispensacionV2Trace) {
            log.info(
                    "[DISP_V2][PERSIST_AREA_RESOLVED] ordenProduccionId={} areaId={} areaNombre={}",
                    ordenProduccion.getOrdenId(),
                    areaDestino != null ? areaDestino.getAreaId() : null,
                    areaDestino != null ? areaDestino.getNombre() : null
            );
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
            Producto producto = null;
            if (dispensacionV2Trace) {
                log.info(
                        "[DISP_V2][PERSIST_ITEM_START] ordenProduccionId={} productoId={} loteId={} cantidad={}",
                        ordenProduccion.getOrdenId(),
                        item.getProductoId(),
                        item.getLoteId(),
                        item.getCantidad()
                );
            }

            if (item.getProductoId() != null && !item.getProductoId().isEmpty()) {
                producto = productoRepo.findById(item.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + item.getProductoId()));
            } else {
                throw new RuntimeException("Se requiere productoId válido para cada item de dispensación.");
            }
            if (item.getCantidad() <= 0) {
                throw new IllegalArgumentException(
                        "La cantidad de dispensacion debe ser mayor a cero para " + producto.getProductoId() + "."
                );
            }
            boolean consumoDirecto = producto instanceof Material material
                    && material.isConsumoDirecto();
            if (producto.isInventareable() && consumoDirecto) {
                throw new IllegalStateException(
                        "El material " + producto.getProductoId()
                                + " tiene una configuracion invalida: inventariable y consumo directo."
                );
            }
            if (!producto.isInventareable() && !consumoDirecto) {
                throw new IllegalArgumentException(
                        "El producto " + producto.getProductoId()
                                + " no es inventariable ni esta configurado para consumo directo."
                );
            }
            if (consumoDirecto && item.getLoteId() != null) {
                throw new IllegalArgumentException(
                        "El consumo directo de " + producto.getProductoId() + " no admite lote origen."
                );
            }
            if (dispensacionV2Trace) {
                log.info(
                        "[DISP_V2][PERSIST_PRODUCT_FOUND] ordenProduccionId={} productoId={} entityType={} nombre={} inventareable={} unidad={}",
                        ordenProduccion.getOrdenId(),
                        producto.getProductoId(),
                        producto.getClass().getSimpleName(),
                        producto.getNombre(),
                        producto.isInventareable(),
                        producto.getTipoUnidades()
                );
            }

            Movimiento movimiento = new Movimiento();
            movimiento.setCantidad(-item.getCantidad());
            movimiento.setProducto(producto);
            movimiento.setTipoMovimiento(
                    consumoDirecto
                            ? Movimiento.TipoMovimiento.CONSUMO
                            : Movimiento.TipoMovimiento.DISPENSACION
            );
            movimiento.setAfectaInventario(!consumoDirecto);
            movimiento.setAlmacen(consumoDirecto ? null : Movimiento.Almacen.GENERAL);
            movimiento.setTransaccionAlmacen(transaccion);
            movimiento.setAreaOperativa(areaDestino);

            if (item.getLoteId() != null) {
                Lote lote = loteRepo.findById(Long.valueOf(item.getLoteId()))
                        .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + item.getLoteId()));
                movimiento.setLote(lote);
                if (dispensacionV2Trace) {
                    log.info(
                            "[DISP_V2][PERSIST_LOT_FOUND] ordenProduccionId={} productoId={} loteId={} batchNumber={} productionDate={} expirationDate={}",
                            ordenProduccion.getOrdenId(),
                            producto.getProductoId(),
                            lote.getId(),
                            lote.getBatchNumber(),
                            lote.getProductionDate(),
                            lote.getExpirationDate()
                    );
                }
            }

            movimientos.add(movimiento);
            if (dispensacionV2Trace) {
                log.info(
                        "[DISP_V2][PERSIST_MOVEMENT_DRAFT] ordenProduccionId={} productoId={} loteId={} cantidadMovimiento={} tipoMovimiento={} almacen={} areaId={}",
                        ordenProduccion.getOrdenId(),
                        producto.getProductoId(),
                        movimiento.getLote() != null ? movimiento.getLote().getId() : null,
                        movimiento.getCantidad(),
                        movimiento.getTipoMovimiento(),
                        movimiento.getAlmacen(),
                        movimiento.getAreaOperativa() != null ? movimiento.getAreaOperativa().getAreaId() : null
                );
            }
        }

        transaccion.setMovimientosTransaccion(movimientos);

        // Save the transaction
        if (dispensacionV2Trace) {
            log.info(
                    "[DISP_V2][PERSIST_SAVE_START] ordenProduccionId={} movementCount={}",
                    ordenProduccion.getOrdenId(),
                    movimientos.size()
            );
        }
        TransaccionAlmacen transaccionGuardada = transaccionAlmacenHeaderRepo.save(transaccion);
        if (dispensacionV2Trace) {
            log.info(
                    "[DISP_V2][PERSIST_SAVE_COMPLETE] ordenProduccionId={} transaccionId={} movementCount={}",
                    ordenProduccion.getOrdenId(),
                    transaccionGuardada.getTransaccionId(),
                    movimientos.size()
            );
        }

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
        if (dispensacionV2Trace) {
            log.info(
                    "[DISP_V2][PERSIST_ORDER_STATE] ordenProduccionId={} previousState={} resultingState={}",
                    ordenProduccion.getOrdenId(),
                    estadoActual,
                    nuevoEstado
            );
        }
        ordenProduccionRepo.updateEstadoDispensacionMaterialesById(
                ordenProduccion.getOrdenId(),
                EstadoDispensacionMateriales.PARCIAL
        );
        if (dispensacionV2Trace) {
            log.info(
                    "[DISP_V2][PERSIST_DISPENSATION_STATE_UPDATED] ordenProduccionId={} estadoDispensacion={}",
                    ordenProduccion.getOrdenId(),
                    EstadoDispensacionMateriales.PARCIAL
            );
        }

        // Logica contable dispensacion
        // Pendiente implementación de lógica contable para dispensaciones

        seguimientoOrdenAreaService.autoCompletarAlmacenGeneralPorDispensacion(
                ordenProduccion.getOrdenId(),
                userIdReportaSeguimiento,
                buildObservacionAutoCompletar(dispensacionDTO, areaDestino)
        );
        if (dispensacionV2Trace) {
            log.info(
                    "[DISP_V2][PERSIST_TRACKING_COMPLETE] ordenProduccionId={} transaccionId={} reporterUserId={} areaDestinoId={}",
                    ordenProduccion.getOrdenId(),
                    transaccionGuardada.getTransaccionId(),
                    userIdReportaSeguimiento,
                    areaDestino != null ? areaDestino.getAreaId() : null
            );
            log.info(
                    "[DISP_V2][PERSIST_SERVICE_COMPLETE] ordenProduccionId={} transaccionId={}",
                    ordenProduccion.getOrdenId(),
                    transaccionGuardada.getTransaccionId()
            );
        }

        return transaccionGuardada;
    }

    private boolean isDispensacionV2Trace() {
        return MDC.get("dispensacionV2TraceId") != null;
    }

    private AreaOperativa resolveAreaOperativaDestino(OrdenProduccion ordenProduccion, Integer areaOperativaDestinoId) {
        if (areaOperativaDestinoId == null) {
            return null;
        }

        if (areaOperativaDestinoId == SeguimientoOrdenAreaService.ALMACEN_GENERAL_AREA_ID) {
            throw new IllegalArgumentException("El área operativa destino de una dispensación no puede ser Almacen General.");
        }

        AreaOperativa areaDestino = areaProduccionRepo.findById(areaOperativaDestinoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el área operativa destino con ID: " + areaOperativaDestinoId));

        boolean areaPerteneceAlSeguimiento = seguimientoOrdenAreaService.tieneAreaOperativaEnSeguimiento(
                ordenProduccion.getOrdenId(),
                areaOperativaDestinoId
        );

        if (!areaPerteneceAlSeguimiento) {
            throw new IllegalArgumentException(
                    "El área operativa destino no pertenece a la ruta o seguimiento de la orden " + ordenProduccion.getOrdenId());
        }

        return areaDestino;
    }

    private String buildObservacionAutoCompletar(DispensacionDTO dispensacionDTO, AreaOperativa areaDestino) {
        StringBuilder observacion = new StringBuilder("Auto-completado por dispensación de materiales");

        if (areaDestino != null) {
            observacion.append(" hacia ").append(areaDestino.getNombre())
                    .append(" (ID ").append(areaDestino.getAreaId()).append(")");
        }

        if (dispensacionDTO.getObservaciones() != null && !dispensacionDTO.getObservaciones().isBlank()) {
            observacion.append(". Observaciones: ").append(dispensacionDTO.getObservaciones().trim());
        }

        return observacion.toString();
    }

    /**
     * Busca dispensaciones (transacciones tipo OD) con filtros flexibles.
     * Permite filtrar por ID de transacción, ID de orden de producción, lote de producción,
     * producto terminado, y fechas (rango o específica).
     *
     * @param filtro DTO con los criterios de búsqueda
     * @return Página de transacciones que cumplen con los filtros
     */
    public Page<TransaccionAlmacen> buscarDispensacionesFiltradas(FiltroHistDispensacionDTO filtro) {
        Pageable pageable = PageRequest.of(
                filtro.getPage(),
                filtro.getSize(),
                Sort.by(
                        Sort.Order.desc("fechaTransaccion"),
                        Sort.Order.desc("transaccionId")
                )
        );

        Integer transaccionId = null;
        Integer ordenProduccionId = null;
        String loteAsignado = null;
        String productoTerminadoId = null;
        LocalDateTime fechaInicio = null;
        LocalDateTime fechaFin = null;

        if (filtro.getTipoFiltroId() != null) {
            if (filtro.getTipoFiltroId() == 1
                    && filtro.getTransaccionId() != null
                    && filtro.getTransaccionId() > 0) {
                transaccionId = filtro.getTransaccionId();
            } else if (filtro.getTipoFiltroId() == 2
                    && filtro.getOrdenProduccionId() != null
                    && filtro.getOrdenProduccionId() > 0) {
                ordenProduccionId = filtro.getOrdenProduccionId();
            } else if (filtro.getTipoFiltroId() == 3
                    && filtro.getLoteAsignado() != null
                    && !filtro.getLoteAsignado().isBlank()) {
                loteAsignado = filtro.getLoteAsignado().trim();
            }
        }

        if (filtro.getProductoTerminadoId() != null && !filtro.getProductoTerminadoId().isBlank()) {
            productoTerminadoId = filtro.getProductoTerminadoId().trim();
        }

        boolean tieneFiltroFecha = (filtro.getTipoFiltroFecha() != null && filtro.getTipoFiltroFecha() > 0);
        if (tieneFiltroFecha) {
            if (filtro.getTipoFiltroFecha() == 1) {
                if (filtro.getFechaInicio() != null
                        && filtro.getFechaFin() != null
                        && filtro.getFechaInicio().isAfter(filtro.getFechaFin())) {
                    throw new RuntimeException("La fecha de inicio no puede ser posterior a la fecha de fin");
                }
                if (filtro.getFechaInicio() != null && filtro.getFechaFin() != null) {
                    fechaInicio = filtro.getFechaInicio().atStartOfDay();
                    fechaFin = filtro.getFechaFin().atTime(23, 59, 59, 999999999);
                }
            } else if (filtro.getTipoFiltroFecha() == 2) {
                if (filtro.getFechaEspecifica() != null) {
                    fechaInicio = filtro.getFechaEspecifica().atStartOfDay();
                    fechaFin = filtro.getFechaEspecifica().atTime(23, 59, 59, 999999999);
                }
            }
        }

        return historialDispensacionesQuery.buscar(
                transaccionId,
                ordenProduccionId,
                loteAsignado,
                productoTerminadoId,
                fechaInicio,
                fechaFin,
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
        dto.setCausaAjuste(transaccion.getCausaAjuste() != null
                ? transaccion.getCausaAjuste().name()
                : null);
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

        if (transaccion.getTipoEntidadCausante() == TransaccionAlmacen.TipoEntidadCausante.OD) {
            ordenProduccionRepo.findById(transaccion.getIdEntidadCausante())
                    .ifPresent(op -> dto.setLoteAsignado(op.getLoteAsignado()));
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
