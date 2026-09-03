package exotic.app.planta.service.produccion;


import exotic.app.planta.model.ventas.Vendedor;
import exotic.app.planta.repo.ventas.VendedorRepository;
import org.springframework.transaction.annotation.Transactional;
import exotic.app.planta.model.contabilidad.AsientoContable;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.manufacturing.procesos.nodo.NodoProceso;
import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.EstadoDispensacionMateriales;
import exotic.app.planta.model.produccion.PoliticaDispensacionInicio;
import exotic.app.planta.model.produccion.dto.ODP_Data4PDF;
import exotic.app.planta.model.produccion.dto.OrdenProduccionBatchDTO;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO_save;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.contabilidad.ContabilidadService;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
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
    private final MasterDirectiveService masterDirectiveService;
    private final VencimientoLoteService vencimientoLoteService;
    private final ManufacturingVersionRepo manufacturingVersionRepo;
    private final UserRepository userRepository;
    private final BatchRecordService batchRecordService;
    private final OrdenFabricacionAutoGenerationService ordenFabricacionAutoGenerationService;
    private final OrdenFabricacionService ordenFabricacionService;
    private final Clock applicationClock;

    @Transactional(rollbackFor = Exception.class)
    public OrdenProduccion saveOrdenProduccion(
            OrdenProduccionDTO_save ordenProduccionDTO,
            User actor
    ) {
        boolean batchRecordWorkflowEnabled =
                masterDirectiveService.lockBatchRecordWorkflowForNewOrder();
        Producto producto = productoRepo.findById(ordenProduccionDTO.getProductoId())
            .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado con ID: " + ordenProduccionDTO.getProductoId()));
        ManufacturingVersions manufacturingVersion = resolverVersionManufactura(producto);
        String loteNumero = requireLote(ordenProduccionDTO.getLoteBatchNumber());

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
        ordenProduccion.setManufacturingVersion(manufacturingVersion);
        aplicarPoliticaDispensacionInicial(ordenProduccion);

        OrdenProduccion savedOrden = ordenProduccionRepo.save(ordenProduccion);

        Lote lote = crearLoteProduccion(
                loteNumero, savedOrden, producto, batchRecordWorkflowEnabled);
        savedOrden.setLoteAsignado(lote.getBatchNumber());
        ordenProduccionRepo.save(savedOrden);

        // Inicializar seguimiento por áreas operativas
        seguimientoOrdenAreaService.inicializarSeguimiento(savedOrden);
        User creador = requireActor(actor);
        if (batchRecordWorkflowEnabled) {
            batchRecordService.crearParaOrdenProduccion(
                    savedOrden, lote, creador);
        }
        ordenFabricacionAutoGenerationService.generarParaOrden(savedOrden, creador);

        return savedOrden;
    }

    /**
     * Crea múltiples órdenes de producción en una única transacción, una por cada número de
     * lote recibido. Si cualquier lote ya existe, toda la operación hace rollback.
     */
    @Transactional(rollbackFor = Exception.class)
    public List<OrdenProduccion> saveMultipleOrdenesProduccion(
            OrdenProduccionBatchDTO dto,
            User actor
    ) {
        boolean batchRecordWorkflowEnabled =
                masterDirectiveService.lockBatchRecordWorkflowForNewOrder();
        Producto producto = productoRepo.findById(dto.getProductoId())
            .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado con ID: " + dto.getProductoId()));
        ManufacturingVersions manufacturingVersion = resolverVersionManufactura(producto);
        User creador = requireActor(actor);

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
            String loteNumero = requireLote(loteBatchNumber);
            OrdenProduccion ordenProduccion = new OrdenProduccion(
                producto, dto.getObservaciones(), dto.getCantidadProducir());
            ordenProduccion.setFechaLanzamiento(dto.getFechaLanzamiento());
            ordenProduccion.setFechaFinalPlanificada(dto.getFechaFinalPlanificada());
            ordenProduccion.setNumeroPedidoComercial(dto.getNumeroPedidoComercial());
            ordenProduccion.setAreaOperativa(dto.getAreaOperativa());
            ordenProduccion.setDepartamentoOperativo(dto.getDepartamentoOperativo());
            ordenProduccion.setVendedorResponsable(vendedorResponsable);
            ordenProduccion.setManufacturingVersion(manufacturingVersion);
            aplicarPoliticaDispensacionInicial(ordenProduccion);

            OrdenProduccion savedOrden = ordenProduccionRepo.save(ordenProduccion);

            Lote lote = crearLoteProduccion(
                    loteNumero, savedOrden, producto, batchRecordWorkflowEnabled);
            savedOrden.setLoteAsignado(lote.getBatchNumber());
            ordenProduccionRepo.save(savedOrden);

            // Inicializar seguimiento por áreas operativas
            seguimientoOrdenAreaService.inicializarSeguimiento(savedOrden);
            if (batchRecordWorkflowEnabled) {
                batchRecordService.crearParaOrdenProduccion(savedOrden, lote, creador);
            }
            ordenFabricacionAutoGenerationService.generarParaOrden(savedOrden, creador);

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
        int year2 = Year.now(applicationClock).getValue() % 100;
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
                .map(this::convertToHistorialDto)
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
        List<OrdenProduccionDTO> dtoList = ordenesPage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        enriquecerUltimaAreaDispensada(dtoList);
        return new PageImpl<>(dtoList, pageable, ordenesPage.getTotalElements());
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

        OrdenProduccionDTO dto = convertToDto(orden);
        enriquecerUltimaAreaDispensada(List.of(dto));
        return dto;
    }

    public Page<OrdenProduccionDTO> getOrdenesProduccionByLoteAsignadoForDispensacion(String loteAsignado, Pageable pageable) {
        Page<OrdenProduccion> ordenesPage = ordenProduccionRepo.findByLoteAsignadoContainingAndOpenOrInProgress(loteAsignado, pageable);
        List<OrdenProduccionDTO> dtoList = ordenesPage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        enriquecerUltimaAreaDispensada(dtoList);
        return new PageImpl<>(dtoList, pageable, ordenesPage.getTotalElements());
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
        dto.setPoliticaDispensacionInicio(orden.getPoliticaDispensacionInicio() != null
                ? orden.getPoliticaDispensacionInicio().name()
                : null);
        dto.setFechaAplicacionPoliticaDispensacion(orden.getFechaAplicacionPoliticaDispensacion());
        dto.setEstadoDispensacionMateriales(orden.getEstadoDispensacionMateriales() != null
                ? orden.getEstadoDispensacionMateriales().name()
                : null);
        dto.setObservaciones(orden.getObservaciones());
        dto.setCantidadProducir(orden.getCantidadProducir());
        dto.setNumeroPedidoComercial(orden.getNumeroPedidoComercial());
        dto.setAreaOperativa(orden.getAreaOperativa());
        dto.setDepartamentoOperativo(orden.getDepartamentoOperativo());
        dto.setLoteAsignado(orden.getLoteAsignado());
        if (orden.getMpsSemanal() != null) {
            dto.setOrigenOrden("MPS");
            dto.setMpsId(orden.getMpsSemanal().getMpsId());
            dto.setMpsWeekStartDate(orden.getMpsSemanal().getWeekStartDate());
            if (orden.getMpsLotePlanificado() != null) {
                dto.setMpsLotePlanificadoId(orden.getMpsLotePlanificado().getId());
                dto.setMpsLoteOrdinal(orden.getMpsLotePlanificado().getLoteOrdinal());
            }
        } else {
            dto.setOrigenOrden("MANUAL");
        }
        if (orden.getVendedorResponsable() != null) {
            dto.setResponsableId(orden.getVendedorResponsable().getCedula());
        }

        return dto;
    }

    private OrdenProduccionDTO convertToHistorialDto(OrdenProduccion orden) {
        OrdenProduccionDTO dto = convertToDto(orden);
        Producto producto = orden.getProducto();

        if (producto == null) {
            return dto;
        }

        dto.setProductoTipo(producto.getTipo_producto());
        dto.setProductoUnidad(producto.getTipoUnidades());

        if (producto instanceof Terminado terminado && terminado.getCategoria() != null) {
            dto.setProductoCategoriaId(terminado.getCategoria().getCategoriaId());
            dto.setProductoCategoriaNombre(terminado.getCategoria().getCategoriaNombre());
        }

        return dto;
    }

    private void enriquecerUltimaAreaDispensada(List<OrdenProduccionDTO> ordenes) {
        if (ordenes == null || ordenes.isEmpty()) {
            return;
        }

        Map<Integer, String> ultimaAreaPorOrdenId = transaccionAlmacenHeaderRepo
                .findUltimaAreaDispensadaByOrdenIds(
                        TransaccionAlmacen.TipoEntidadCausante.OD,
                        ordenes.stream().map(OrdenProduccionDTO::getOrdenId).toList()
                )
                .stream()
                .collect(Collectors.toMap(
                        TransaccionAlmacenHeaderRepo.UltimaAreaDispensadaProjection::getOrdenProduccionId,
                        projection -> projection.getAreaOperativaNombre() != null
                                ? projection.getAreaOperativaNombre()
                                : "Sin dispensacion"
                ));

        ordenes.forEach(orden -> orden.setUltimaAreaDispensada(
                ultimaAreaPorOrdenId.getOrDefault(orden.getOrdenId(), "Sin dispensacion")
        ));
    }

    /**
     * Actualiza estados administrativos distintos del cierre definitivo.
     * El estado 2 se reserva al cierre de producto terminado.
     */
    @Transactional
    public OrdenProduccionDTO updateEstadoOrdenProduccion(int ordenId, int estadoOrden) {
        if (estadoOrden == 2) {
            throw new IllegalArgumentException(
                    "El cierre de una orden de produccion solo puede realizarse desde el reporte de producto terminado.");
        }
        if (estadoOrden == -1) {
            throw new IllegalArgumentException(
                    "Use la operación de cancelación para anular una orden de producción.");
        }
        ordenProduccionRepo.updateEstadoOrdenById(ordenId, estadoOrden, LocalDateTime.now(applicationClock));

        OrdenProduccion ordenProduccion = ordenProduccionRepo.findById(ordenId).orElseThrow(() -> new RuntimeException("OrdenProduccion not found"));
        return convertToDto(ordenProduccion);
    }

    /**
     * Cancela una orden de producción si se encuentra en estado abierto (0).
     *
     * @param ordenId identificador de la orden a cancelar
     * @return DTO actualizado de la orden cancelada
     */
    @Transactional
    public OrdenProduccionDTO cancelarOrdenProduccion(int ordenId, User actor) {
        OrdenProduccion ordenProduccion = ordenProduccionRepo.findById(ordenId)
            .orElseThrow(() -> new IllegalArgumentException("Orden de producción no encontrada con ID: " + ordenId));

        if (!isOrdenProduccionCancelable(ordenProduccion)) {
            throw new IllegalStateException("Solo se pueden cancelar órdenes en estado abierto (0). Estado actual: " + ordenProduccion.getEstadoOrden());
        }

        ordenProduccion.setEstadoOrden(-1);
        if (ordenProduccion.getFechaFinal() == null) {
            ordenProduccion.setFechaFinal(LocalDateTime.now(applicationClock));
        }

        ordenProduccionRepo.save(ordenProduccion);
        batchRecordService.anularPorCancelacion(ordenProduccion, requireActor(actor));
        ordenFabricacionService.cancelarVinculadasPorCancelacionOp(
                ordenProduccion, requireActor(actor));
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

    @Transactional(rollbackFor = Exception.class)
    public OrdenProduccion saveOrdenProduccionDesdeMps(
            OrdenProduccionDTO_save ordenProduccionDTO,
            MasterProductionScheduleSemanal mpsSemanal,
            MpsSemanalLotePlanificado lotePlanificado,
            String generatedByUsername
    ) {
        boolean batchRecordWorkflowEnabled =
                masterDirectiveService.lockBatchRecordWorkflowForNewOrder();
        Producto producto = productoRepo.findById(ordenProduccionDTO.getProductoId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado con ID: " + ordenProduccionDTO.getProductoId()));
        ManufacturingVersions manufacturingVersion = resolverVersionManufactura(producto);
        String loteNumero = requireLote(ordenProduccionDTO.getLoteBatchNumber());
        User creador = userRepository.findByUsername(generatedByUsername)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Usuario generador del MPS no encontrado: " + generatedByUsername));

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
        ordenProduccion.setMpsSemanal(mpsSemanal);
        ordenProduccion.setMpsLotePlanificado(lotePlanificado);
        ordenProduccion.setManufacturingVersion(manufacturingVersion);
        aplicarPoliticaDispensacionInicial(ordenProduccion);

        OrdenProduccion savedOrden = ordenProduccionRepo.save(ordenProduccion);

        Lote lote = crearLoteProduccion(
                loteNumero, savedOrden, producto, batchRecordWorkflowEnabled);
        savedOrden.setLoteAsignado(lote.getBatchNumber());
        ordenProduccionRepo.save(savedOrden);

        seguimientoOrdenAreaService.inicializarSeguimiento(savedOrden);
        if (batchRecordWorkflowEnabled) {
            batchRecordService.crearParaOrdenProduccion(savedOrden, lote, creador);
        }
        ordenFabricacionAutoGenerationService.generarParaOrden(savedOrden, creador);
        return savedOrden;
    }

    private Lote crearLoteProduccion(
            String batchNumber,
            OrdenProduccion ordenProduccion,
            Producto producto,
            boolean batchRecordWorkflowEnabled
    ) {
        Lote loteExistente = loteRepo.findByBatchNumber(batchNumber);
        if (loteExistente != null) {
            throw new IllegalArgumentException(
                    "El numero de lote '" + batchNumber
                            + "' ya esta asignado a otra orden de produccion");
        }

        Lote lote = new Lote();
        lote.setBatchNumber(batchNumber);
        lote.setOrdenProduccion(ordenProduccion);
        lote.setProducto(producto);
        lote.setEstadoCalidad(batchRecordWorkflowEnabled
                ? EstadoCalidadLote.CUARENTENA
                : EstadoCalidadLote.SIN_CLASIFICAR);
        vencimientoLoteService.copiarPoliticaVigente(producto, lote);
        if (lote.getVidaUtilCantidadAplicada() == null
                || lote.getVidaUtilUnidadAplicada() == null) {
            throw new IllegalStateException(
                    "El producto terminado no tiene una vida útil configurada en su categoría. "
                            + "Defínala antes de emitir la orden de producción.");
        }
        return loteRepo.save(lote);
    }

    private ManufacturingVersions resolverVersionManufactura(Producto producto) {
        if (!(producto instanceof Terminado)) {
            throw new IllegalArgumentException(
                    "Una orden de producción debe generar un producto terminado.");
        }
        return manufacturingVersionRepo.findTopByProductoOrderByVersionNumberDesc(producto)
                .orElseThrow(() -> new IllegalStateException(
                        "El producto " + producto.getProductoId()
                                + " no tiene una versión de manufactura disponible."));
    }

    private String requireLote(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "El número de lote es obligatorio para crear el expediente digital.");
        }
        return value.trim();
    }

    private User requireActor(User actor) {
        if (actor == null) {
            throw new IllegalArgumentException(
                    "Se requiere un usuario autenticado para crear el expediente digital.");
        }
        return actor;
    }

    private void aplicarPoliticaDispensacionInicial(OrdenProduccion ordenProduccion) {
        boolean noBloqueante = masterDirectiveService.isDispensacionNoBloqueaInicioProduccion();
        ordenProduccion.setPoliticaDispensacionInicio(noBloqueante
                ? PoliticaDispensacionInicio.NO_BLOQUEANTE
                : PoliticaDispensacionInicio.BLOQUEANTE);
        ordenProduccion.setFechaAplicacionPoliticaDispensacion(LocalDateTime.now(applicationClock));
        ordenProduccion.setEstadoDispensacionMateriales(noBloqueante
                ? EstadoDispensacionMateriales.LIBERADA_SIN_DISPENSACION
                : EstadoDispensacionMateriales.PENDIENTE);
    }
}
