package exotic.app.planta.service.produccion;


import exotic.app.planta.model.ventas.Vendedor;
import exotic.app.planta.repo.ventas.VendedorRepository;
import org.springframework.transaction.annotation.Transactional;
import exotic.app.planta.model.contabilidad.AsientoContable;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.manufacturing.procesos.nodo.NodoProceso;
import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.dto.ODP_Data4PDF;
import exotic.app.planta.model.produccion.dto.OrdenProduccionBatchDTO;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO_save;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.service.contabilidad.ContabilidadService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.ArrayList;
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
    private final SeguimientoOrdenAreaService seguimientoOrdenAreaService;

    @Transactional(rollbackFor = Exception.class)
    public OrdenProduccion saveOrdenProduccion(OrdenProduccionDTO_save ordenProduccionDTO) {
        Producto producto = productoRepo.findById(ordenProduccionDTO.getProductoId())
            .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado con ID: " + ordenProduccionDTO.getProductoId()));

        Long vendedorResponsableId = ordenProduccionDTO.getVendedorResponsableId();
        Vendedor vendedorResponsable = null;
        if (vendedorResponsableId != null) {
            vendedorResponsable = vendedorRepository.findById(vendedorResponsableId)
                .orElseThrow(() -> new IllegalArgumentException("Responsable no encontrado con ID: " + vendedorResponsableId));
        }

        OrdenProduccion ordenProduccion = new OrdenProduccion(producto, ordenProduccionDTO.getObservaciones(), ordenProduccionDTO.getCantidadProducir());
        ordenProduccion.setFechaLanzamiento(ordenProduccionDTO.getFechaLanzamiento());
        ordenProduccion.setFechaFinalPlanificada(ordenProduccionDTO.getFechaFinalPlanificada());
        ordenProduccion.setNumeroPedidoComercial(ordenProduccionDTO.getNumeroPedidoComercial());
        ordenProduccion.setAreaOperativa(ordenProduccionDTO.getAreaOperativa());
        ordenProduccion.setDepartamentoOperativo(ordenProduccionDTO.getDepartamentoOperativo());
        ordenProduccion.setVendedorResponsable(vendedorResponsable);

        OrdenProduccion savedOrden = ordenProduccionRepo.save(ordenProduccion);

        if (ordenProduccionDTO.getLoteBatchNumber() != null && !ordenProduccionDTO.getLoteBatchNumber().isBlank()) {
            // Validar que el batch number no exista previamente
            Lote loteExistente = loteRepo.findByBatchNumber(ordenProduccionDTO.getLoteBatchNumber());
            if (loteExistente != null) {
                throw new IllegalArgumentException(
                    "El número de lote '" + ordenProduccionDTO.getLoteBatchNumber() +
                    "' ya está asignado a otra orden de producción"
                );
            }

            Lote lote = new Lote();
            lote.setBatchNumber(ordenProduccionDTO.getLoteBatchNumber());
            lote.setOrdenProduccion(savedOrden);
            loteRepo.save(lote);
            savedOrden.setLoteAsignado(lote.getBatchNumber());
            ordenProduccionRepo.save(savedOrden);
        }

        // Inicializar seguimiento por áreas operativas
        seguimientoOrdenAreaService.inicializarSeguimiento(savedOrden);

        return savedOrden;
    }

    /**
     * Crea múltiples órdenes de producción en una única transacción, una por cada número de
     * lote recibido. Si cualquier lote ya existe, toda la operación hace rollback.
     */
    @Transactional(rollbackFor = Exception.class)
    public List<OrdenProduccion> saveMultipleOrdenesProduccion(OrdenProduccionBatchDTO dto) {
        Producto producto = productoRepo.findById(dto.getProductoId())
            .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado con ID: " + dto.getProductoId()));

        Long vendedorResponsableId = dto.getVendedorResponsableId();
        Vendedor vendedorResponsable = null;
        if (vendedorResponsableId != null) {
            vendedorResponsable = vendedorRepository.findById(vendedorResponsableId)
                .orElseThrow(() -> new IllegalArgumentException("Responsable no encontrado con ID: " + vendedorResponsableId));
        }

        List<String> loteBatchNumbers = dto.getLoteBatchNumbers();
        if (loteBatchNumbers == null || loteBatchNumbers.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un número de lote para crear múltiples órdenes.");
        }

        List<OrdenProduccion> savedOrdenes = new ArrayList<>();
        for (String loteBatchNumber : loteBatchNumbers) {
            OrdenProduccion ordenProduccion = new OrdenProduccion(
                producto, dto.getObservaciones(), dto.getCantidadProducir());
            ordenProduccion.setFechaLanzamiento(dto.getFechaLanzamiento());
            ordenProduccion.setFechaFinalPlanificada(dto.getFechaFinalPlanificada());
            ordenProduccion.setNumeroPedidoComercial(dto.getNumeroPedidoComercial());
            ordenProduccion.setAreaOperativa(dto.getAreaOperativa());
            ordenProduccion.setDepartamentoOperativo(dto.getDepartamentoOperativo());
            ordenProduccion.setVendedorResponsable(vendedorResponsable);

            OrdenProduccion savedOrden = ordenProduccionRepo.save(ordenProduccion);

            if (loteBatchNumber != null && !loteBatchNumber.isBlank()) {
                Lote loteExistente = loteRepo.findByBatchNumber(loteBatchNumber);
                if (loteExistente != null) {
                    throw new IllegalArgumentException(
                        "El número de lote '" + loteBatchNumber + "' ya está asignado a otra orden de producción"
                    );
                }
                Lote lote = new Lote();
                lote.setBatchNumber(loteBatchNumber);
                lote.setOrdenProduccion(savedOrden);
                loteRepo.save(lote);
                savedOrden.setLoteAsignado(lote.getBatchNumber());
                ordenProduccionRepo.save(savedOrden);
            }

            // Inicializar seguimiento por áreas operativas
            seguimientoOrdenAreaService.inicializarSeguimiento(savedOrden);

            savedOrdenes.add(savedOrden);
        }

        return savedOrdenes;
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
        page.getContent().forEach(orden -> {
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

    public Page<OrdenProduccionDTO> getOrdenesProduccionByLoteAsignadoForDispensacion(String loteAsignado, Pageable pageable) {
        Page<OrdenProduccion> ordenesPage = ordenProduccionRepo.findByLoteAsignadoContainingAndOpenOrInProgress(loteAsignado, pageable);
        return ordenesPage.map(this::convertToDto);
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
        dto.setLoteAsignado(orden.getLoteAsignado());
        if (orden.getVendedorResponsable() != null) {
            dto.setResponsableId(orden.getVendedorResponsable().getCedula());
        }

        return dto;
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
    private List<AreaOperativa> getAreasProduccionOrdenadas(Terminado terminado) {
        List<AreaOperativa> areasProduccion = new ArrayList<>();
        AreaOperativa areaTerminado = null;

        // Obtener el área de producción del terminado
        if (getLastAreaOperativa(terminado) != null) {
            areaTerminado = getLastAreaOperativa(terminado);
        }

        // Recolectar áreas de producción de los semiterminados
        for (Insumo insumo : terminado.getInsumos()) {
            Producto producto = insumo.getProducto();
            if (producto instanceof SemiTerminado) {
                SemiTerminado semiterminado = (SemiTerminado) producto;

                if (getLastAreaOperativa(semiterminado) != null) {

                    AreaOperativa areaSemiterminado = getLastAreaOperativa(semiterminado);

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
     * nodo de tipo proceso en el metodo de fabricacion relacional.
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
        if (!getNodoProcesos(terminado).isEmpty()) {

            for (NodoProceso nodo : getNodoProcesos(terminado)) {
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

                if (!getNodoProcesos(semiterminado).isEmpty()) {

                    for (NodoProceso nodo : getNodoProcesos(semiterminado)) {
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

    private AreaOperativa getLastAreaOperativa(Producto producto) {
        List<NodoProceso> nodoProcesos = getNodoProcesos(producto);
        if (nodoProcesos.isEmpty()) {
            return null;
        }
        return nodoProcesos.get(nodoProcesos.size() - 1).getAreaOperativa();
    }

    private List<NodoProceso> getNodoProcesos(Producto producto) {
        if (producto instanceof Terminado terminado) {
            if (terminado.getProcesoProduccionCompleto() == null || terminado.getProcesoProduccionCompleto().getNodes() == null) {
                return List.of();
            }
            return terminado.getProcesoProduccionCompleto().getNodes().stream()
                    .filter(NodoProceso.class::isInstance)
                    .map(NodoProceso.class::cast)
                    .collect(Collectors.toList());
        }
        if (producto instanceof SemiTerminado semiTerminado) {
            if (semiTerminado.getProcesoProduccionCompleto() == null || semiTerminado.getProcesoProduccionCompleto().getNodes() == null) {
                return List.of();
            }
            return semiTerminado.getProcesoProduccionCompleto().getNodes().stream()
                    .filter(NodoProceso.class::isInstance)
                    .map(NodoProceso.class::cast)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
