package exotic.app.planta.service.inventarios;


import org.springframework.transaction.annotation.Transactional;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.contabilidad.AsientoContable;
import exotic.app.planta.model.inventarios.dto.AjusteInventarioDTO;
import exotic.app.planta.model.inventarios.dto.AjusteItemDTO;
import exotic.app.planta.model.inventarios.dto.BackflushNoPlanificadoDTO;
import exotic.app.planta.model.inventarios.dto.BackflushNoPlanificadoItemDTO;
import exotic.app.planta.model.inventarios.dto.BackflushMultipleNoPlanificadoDTO;
import exotic.app.planta.model.inventarios.dto.FiltroHistorialTransaccionesDTO;
import exotic.app.planta.model.inventarios.dto.IngresoOCM_DTA;
import exotic.app.planta.model.inventarios.dto.TransaccionAlmacenResponseDTO;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.dto.ProductoStockDTO;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.produccion.OrdenSeguimientoRepo;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.contabilidad.ContabilidadService;
import exotic.app.planta.service.produccion.ProduccionService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import exotic.app.planta.model.inventarios.dto.MovimientoExcelRequestDTO;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class MovimientosService {

    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final ProductoRepo productoRepo;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;

    private final OrdenCompraRepo ordenCompraRepo;
    private final SemiTerminadoRepo semiTerminadoRepo;
    private final TerminadoRepo terminadoRepo;
    private final MaterialRepo materialRepo;
    private final LoteRepo loteRepo;
    private final UserRepository userRepository;
    private final ContabilidadService contabilidadService;
    private final ProduccionService produccionService;
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final OrdenSeguimientoRepo ordenSeguimientoRepo;



    @Transactional
    public Movimiento saveMovimiento(Movimiento movimientoReal){
        return transaccionAlmacenRepo.save(movimientoReal);
    }

    public Optional<ProductoStockDTO> getStockOf(String producto_id){
        Optional<Producto> optionalProducto = productoRepo.findByProductoId(producto_id);
        if(optionalProducto.isPresent()){
            Double totalCantidad = transaccionAlmacenRepo.findTotalCantidadByProductoId(producto_id);
            totalCantidad = (totalCantidad != null) ? totalCantidad : 0.0;
            ProductoStockDTO productoStock = new ProductoStockDTO(optionalProducto.get(), totalCantidad);
            return Optional.of(productoStock);
        } else{
            return Optional.of(new ProductoStockDTO());
        }
    }

    public Optional<ProductoStockDTO> getStockOf2(String producto_id){
        Optional<Producto> optionalProducto = productoRepo.findByProductoId(producto_id);
        if(optionalProducto.isEmpty()){
            return Optional.empty();
        }

        List<Movimiento> movimientos = transaccionAlmacenRepo.findByProducto_ProductoId(producto_id);
        double productoStock = movimientos.stream()
                .mapToDouble(Movimiento::getCantidad)
                .sum();

        return Optional.of(new ProductoStockDTO(optionalProducto.get(), productoStock));
    }


    // New method to search products and get stock
    public Page<ProductoStockDTO> searchProductsWithStock(String searchTerm, String tipoBusqueda, int page, int size){
        Pageable pageable = PageRequest.of(page, size);

        Specification<Producto> spec = (root, query, criteriaBuilder) -> {
            if ("NOMBRE".equalsIgnoreCase(tipoBusqueda)) {
                return criteriaBuilder.like(criteriaBuilder.lower(root.get("nombre")), "%" + searchTerm.toLowerCase() + "%");
            } else if ("ID".equalsIgnoreCase(tipoBusqueda)) {
                // Usar directamente el searchTerm como String para la comparación
                return criteriaBuilder.equal(root.get("productoId"), searchTerm);
            } else {
                return null;
            }
        };

        Page<Producto> productosPage = productoRepo.findAll(spec, pageable);

        List<ProductoStockDTO> productStockDTOList = productosPage.getContent().stream().map(producto -> {
            Double stockQuantity = transaccionAlmacenRepo.findTotalCantidadByProductoId(producto.getProductoId());
            stockQuantity = (stockQuantity != null) ? stockQuantity : 0.0;
            return new ProductoStockDTO(producto, stockQuantity);
        }).collect(Collectors.toList());

        return new PageImpl<>(productStockDTOList, pageable, productosPage.getTotalElements());
    }


    // Method to get movimientos for a product
    public Page<Movimiento> getMovimientosByProductoId(String productoId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return transaccionAlmacenRepo.findByProducto_ProductoIdOrderByFechaMovimientoDesc(productoId, pageable);
    }


    public TransaccionAlmacen createAjusteInventario(AjusteInventarioDTO ajusteInventarioDTO) {
        return createAjusteInventario(ajusteInventarioDTO, TransaccionAlmacen.TipoEntidadCausante.OAA);
    }

    public TransaccionAlmacen createAjusteInventario(AjusteInventarioDTO ajusteInventarioDTO, TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante) {
        boolean isCargaMasiva = tipoEntidadCausante == TransaccionAlmacen.TipoEntidadCausante.CM;
        if (isCargaMasiva) {
            log.info("[CARGA_MASIVA-Movimientos] createAjusteInventario CM. Items={}", 
                    ajusteInventarioDTO.getItems() != null ? ajusteInventarioDTO.getItems().size() : 0);
        }

        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setTipoEntidadCausante(tipoEntidadCausante);
        transaccion.setIdEntidadCausante(0);
        transaccion.setObservaciones(ajusteInventarioDTO.getObservaciones());
        transaccion.setUrlDocSoporte(ajusteInventarioDTO.getUrlDocSoporte());

        if (ajusteInventarioDTO.getUsername() != null && !ajusteInventarioDTO.getUsername().isEmpty()) {
            Optional<User> userOpt = userRepository.findByUsername(ajusteInventarioDTO.getUsername());
            if (userOpt.isPresent()) {
                transaccion.setUsuarioAprobador(userOpt.get());
            } else {
                // Si no se encuentra el usuario, registramos un warning pero continuamos
                log.warn("Usuario no encontrado con username: " + ajusteInventarioDTO.getUsername());
            }
        }

        List<Movimiento> movimientos = new ArrayList<>();
        if (ajusteInventarioDTO.getItems() != null) {
            for (AjusteItemDTO item : ajusteInventarioDTO.getItems()) {
                if (isCargaMasiva) {
                    log.debug("[CARGA_MASIVA-Movimientos] Procesando item: productoId={}, cantidad={}", item.getProductoId(), item.getCantidad());
                }
                Producto producto = productoRepo.findByProductoId(item.getProductoId())
                        .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + item.getProductoId()));

                Movimiento movimiento = new Movimiento();
                movimiento.setCantidad(item.getCantidad());
                movimiento.setProducto(producto);
                movimiento.setAlmacen(Optional.ofNullable(item.getAlmacen()).orElse(Movimiento.Almacen.GENERAL));
                movimiento.setTipoMovimiento(resolveTipoMovimiento(item));

                if (item.getLoteId() != null) {
                    Lote lote = loteRepo.findById(item.getLoteId().longValue())
                            .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + item.getLoteId()));
                    movimiento.setLote(lote);
                } else {
                    Lote nuevoLote = new Lote();
                    nuevoLote.setBatchNumber(generateBatchNumber(producto));
                    nuevoLote.setProductionDate(LocalDate.now());
                    nuevoLote.setExpirationDate(LocalDate.now().plusMonths(6));

                    nuevoLote = loteRepo.save(nuevoLote);
                    movimiento.setLote(nuevoLote);
                }

                movimiento.setTransaccionAlmacen(transaccion);
                movimientos.add(movimiento);
            }
        }

        transaccion.setMovimientosTransaccion(movimientos);
        TransaccionAlmacen saved = transaccionAlmacenHeaderRepo.save(transaccion);
        if (isCargaMasiva) {
            log.info("[CARGA_MASIVA-Movimientos] Transacción guardada. transaccionId={}, movimientosCount={}", 
                    saved.getTransaccionId(), movimientos.size());
        }
        return saved;
    }


    /**
     * El registro de esta entidad en el sistema implica el ingreso de mercancia al almacen. por tanto
     * esto se debe ver reflejado inmediatamente en la tabla de movimientos, y actualizar los precios de cada
     * materia prima y de las recetas dependientes de cada materia prima. tambien el estado de la orden de compra
     * debe cambiar automaticamente a 3, que es cerrada exitosamente
     * @param ingresoOCM_dta
     * @param file
     * @return
     */
    @Transactional
    public ResponseEntity<?> createDocIngreso(IngresoOCM_DTA ingresoOCM_dta, MultipartFile file) {
        log.info("Iniciando creación de documento de ingreso OCM. userId: {}", ingresoOCM_dta.getUserId());
        try {
            // Create folder based on current date (yyyyMMdd)
            String currentDateFolder = LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Path folderPath = Paths.get("data", currentDateFolder);
            Files.createDirectories(folderPath);

            // Generate a unique filename using a UUID and the original file name.
            String originalFilename = file.getOriginalFilename();
            String newFilename = UUID.randomUUID().toString() + "_" + originalFilename;
            Path filePath = folderPath.resolve(newFilename);

            // Save the file to disk.
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Create the DocIngresoAlmacenOC entity using the DTO constructor.
            TransaccionAlmacen ingresoOCM = new TransaccionAlmacen(ingresoOCM_dta);
            // Set the URL (or path) of the saved file.
            ingresoOCM.setUrlDocSoporte(filePath.toString());

            // Set the usuarioAprobador if userId is provided
            if (ingresoOCM_dta.getUserId() != null && !ingresoOCM_dta.getUserId().isEmpty()) {
                try {
                    Long userId = Long.parseLong(ingresoOCM_dta.getUserId());
                    log.debug("Buscando usuario con ID: {}", userId);
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + userId));
                    ingresoOCM.setUsuarioAprobador(user);
                    log.debug("Usuario aprobador asignado correctamente: {}", user.getUsername());
                } catch (NumberFormatException e) {
                    log.error("Error al convertir userId a Long: " + ingresoOCM_dta.getUserId(), e);
                } catch (RuntimeException e) {
                    log.error("Error al asignar usuario aprobador: {}", e.getMessage(), e);
                    throw e;
                }
            } else {
                log.warn("No se proporcionó userId en el DTO de ingreso OCM");
            }

            // Set the back-reference for each Movimiento and create Lote for each one
            if (ingresoOCM.getMovimientosTransaccion() != null) {
                for (Movimiento movimiento : ingresoOCM.getMovimientosTransaccion()) {
                    movimiento.setTransaccionAlmacen(ingresoOCM);

                    // Crear un nuevo lote para este movimiento
                    Lote lote = new Lote();
                    
                    // Verificar si el movimiento ya tiene un lote con batchNumber especificado
                    if (movimiento.getLote() != null && 
                        movimiento.getLote().getBatchNumber() != null && 
                        !movimiento.getLote().getBatchNumber().trim().isEmpty()) {
                        // Si se proporcionó un batchNumber, usarlo
                        lote.setBatchNumber(movimiento.getLote().getBatchNumber().trim());
                    } else {
                        // Si no se proporcionó batchNumber, generar uno automáticamente
                        lote.setBatchNumber(generateBatchNumber(movimiento.getProducto()));
                    }

                    // Verificar si el movimiento ya tiene un lote con fecha de fabricación especificada
                    if (movimiento.getLote() != null && movimiento.getLote().getProductionDate() != null) {
                        // Si se proporcionó una fecha de fabricación, usarla
                        lote.setProductionDate(movimiento.getLote().getProductionDate());
                    }
                    // Si no se proporcionó fecha de fabricación, se deja como null

                    // Verificar si el movimiento ya tiene un lote con fecha de vencimiento especificada
                    if (movimiento.getLote() != null && movimiento.getLote().getExpirationDate() != null) {
                        // Si se proporcionó una fecha de vencimiento, usarla
                        lote.setExpirationDate(movimiento.getLote().getExpirationDate());
                    }
                    // Si no se proporcionó fecha de vencimiento, se deja como null

                    // Asociar con la orden de compra
                    lote.setOrdenCompraMateriales(ingresoOCM_dta.getOrdenCompraMateriales());

                    // Guardar el lote
                    loteRepo.save(lote);

                    // Asociar el lote al movimiento
                    movimiento.setLote(lote);
                }
            }

            // Persist the entity.
            transaccionAlmacenHeaderRepo.save(ingresoOCM);

            // Ya no se cierra la orden para permitir recepciones parciales.
            OrdenCompraMateriales oc = ingresoOCM_dta.getOrdenCompraMateriales();
            //oc.setEstado(3);
            //ordenCompraRepo.save(oc);

            // Para transacciones de tipo OCM (ingreso de materiales por orden de compra)
            // NO crear asiento automático, se hará manualmente desde el módulo de pagos
            if (ingresoOCM.getTipoEntidadCausante() == TransaccionAlmacen.TipoEntidadCausante.OCM) {
                ingresoOCM.setEstadoContable(TransaccionAlmacen.EstadoContable.PENDIENTE);
                log.info("Transacción de tipo OCM: el asiento contable se creará manualmente desde el módulo de pagos");
            } 
            // Para otros tipos de transacciones (que no son OCM)
            // SÍ crear asiento automático (este bloque no se ejecutará en este método específico,
            // pero se deja como referencia para futuros métodos que manejen otros tipos de transacciones)
            else {
                // Calcular el monto total para el asiento contable
                BigDecimal montoTotal = BigDecimal.ZERO;
                for (ItemOrdenCompra itemOrdenCompra : oc.getItemsOrdenCompra()) {
                    BigDecimal valorItem = BigDecimal.valueOf(itemOrdenCompra.getPrecioUnitario() * itemOrdenCompra.getCantidad());
                    montoTotal = montoTotal.add(valorItem);
                }

                try {
                    AsientoContable asiento = contabilidadService.registrarAsientoIngresoOCM(ingresoOCM, oc, montoTotal);
                    ingresoOCM.setAsientoContable(asiento);
                    ingresoOCM.setEstadoContable(TransaccionAlmacen.EstadoContable.CONTABILIZADA);
                    transaccionAlmacenHeaderRepo.save(ingresoOCM);
                    log.info("Asiento contable registrado con ID: " + asiento.getId());
                } catch (Exception e) {
                    log.error("Error al registrar asiento contable: " + e.getMessage(), e);
                    // No interrumpimos el flujo principal si falla la contabilidad
                }
            }

            // se actualizan los precios de todos las materias primas
            for (ItemOrdenCompra itemOrdenCompra : oc.getItemsOrdenCompra()) {
                itemOrdenCompra.setOrdenCompraMateriales(oc);

                // Verify that the MateriaPrima exists
                Optional<Material> optionalMateriaPrima = materialRepo.findById(itemOrdenCompra.getMaterial().getProductoId());
                if (!optionalMateriaPrima.isPresent()) {
                    throw new RuntimeException("MateriaPrima not found with ID: " + itemOrdenCompra.getMaterial().getProductoId());
                }
                Material material = optionalMateriaPrima.get();
                itemOrdenCompra.setMaterial(material);

                // Retrieve current stock
                Double currentStockOpt = transaccionAlmacenRepo.findTotalCantidadByProductoId(material.getProductoId());
                double nuevoCosto = getNuevoCosto(itemOrdenCompra, currentStockOpt, material);

                // Update MateriaPrima's costo
                material.setCosto(nuevoCosto);

                // Save the updated MateriaPrima
                materialRepo.save(material);

                // Update costs of dependent products if necessary
                Set<String> updatedProductIds = new HashSet<>();
                updateCostoCascade(material, updatedProductIds);
            }

            return ResponseEntity.ok(ingresoOCM);
        } catch(Exception e) {
            log.error("Error saving DocIngresoAlmacenOC", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving document: " + e.getMessage());
        }
    }

    /**
     * Genera un número de lote único para un producto
     */
    private String generateBatchNumber(Producto producto) {
        // Formato: MP-YYYYMMDD-XXXX (MP=Materia Prima, fecha, secuencial)
        String prefix = "MP";
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = String.format("%04d", new Random().nextInt(10000));
        return prefix + "-" + date + "-" + random;
    }


    public void updateCostoCascade(Producto producto, Set<String> updatedProductIds) {
        // If we've already updated this product, return to prevent infinite recursion
        if (updatedProductIds.contains(producto.getProductoId())) {
            return;
        }
        updatedProductIds.add(producto.getProductoId());

        // Recalculate cost of the product if it's a SemiTerminado or Terminado
        if (producto instanceof SemiTerminado) {
            SemiTerminado semiTerminado = (SemiTerminado) producto;

            // Recalculate cost
            double newCosto = 0;
            for (Insumo insumo : semiTerminado.getInsumos()) {
                Producto insumoProducto = insumo.getProducto();
                double insumoCosto = insumoProducto.getCosto();
                double cantidadRequerida = insumo.getCantidadRequerida();
                newCosto += insumoCosto * cantidadRequerida;
            }
            semiTerminado.setCosto(newCosto);

            // Save updated SemiTerminado
            semiTerminadoRepo.save(semiTerminado);

        } else if (producto instanceof Terminado) {
            Terminado terminado = (Terminado) producto;

            // Recalculate cost
            double newCosto = 0;
            for (Insumo insumo : terminado.getInsumos()) {
                Producto insumoProducto = insumo.getProducto();
                double insumoCosto = insumoProducto.getCosto();
                double cantidadRequerida = insumo.getCantidadRequerida();
                newCosto += insumoCosto * cantidadRequerida;
            }
            terminado.setCosto(newCosto);

            // Save updated Terminado
            terminadoRepo.save(terminado);
        }

        // Now find any SemiTerminados that use this product as an Insumo
        List<SemiTerminado> semiTerminados = semiTerminadoRepo.findByInsumos_Producto(producto);
        for (SemiTerminado st : semiTerminados) {
            updateCostoCascade(st, updatedProductIds);
        }

        // And find any Terminados that use this product as an Insumo
        List<Terminado> terminados = terminadoRepo.findByInsumos_Producto(producto);
        for (Terminado t : terminados) {
            updateCostoCascade(t, updatedProductIds);
        }
    }


    private static double getNuevoCosto(ItemOrdenCompra itemOrdenCompra, Double currentStockOpt, Material material) {
        double currentStock = (currentStockOpt != null) ? currentStockOpt : 0;

        // Retrieve current costo
        double currentCosto = material.getCosto();

        // Incoming units and precioCompra from ItemCompra
        double incomingUnits = itemOrdenCompra.getCantidad();
        double incomingPrecio = itemOrdenCompra.getPrecioUnitarioFinal();

        // Calculate nuevo_costo
        if (currentStock + incomingUnits == 0) {
            throw new RuntimeException("Total stock cannot be zero after the compra for MateriaPrima ID: " + material.getProductoId());
        }

        double nuevoCosto = ((currentCosto * currentStock) + (incomingPrecio * incomingUnits)) / (currentStock + incomingUnits);
        return Math.ceil(nuevoCosto);
    }


    /**
     * Creates an unplanned backflush transaction without a production order.
     * This method checks if unplanned backflush is allowed by system configuration.
     * 
     * @param backflushDTO The DTO containing the backflush information
     * @return The created transaction
     */
    @Transactional
    public TransaccionAlmacen createBackflushNoPlanificado(BackflushNoPlanificadoDTO backflushDTO) {
        // Backflush no planificado no permitido por defecto (directiva eliminada; ver Super Master si se reintroduce).
        throw new RuntimeException("El backflush no planificado no está permitido según la configuración del sistema");
    }


    /**
     * Creates multiple unplanned backflush transactions without a production order.
     * This method checks if unplanned backflush is allowed by system configuration.
     * Allows specifying lots for each product.
     * 
     * @param backflushDTO The DTO containing the backflush information for multiple products
     * @return The created transaction
     */
    @Transactional
    public TransaccionAlmacen createBackflushMultipleNoPlanificado(BackflushMultipleNoPlanificadoDTO backflushDTO) {
        // Backflush no planificado no permitido por defecto (directiva eliminada; ver Super Master si se reintroduce).
        throw new RuntimeException("El backflush no planificado no está permitido según la configuración del sistema");
    }


    private Movimiento.TipoMovimiento resolveTipoMovimiento(AjusteItemDTO item) {
        if (item.getMotivo() != null) {
            try {
                return Movimiento.TipoMovimiento.valueOf(item.getMotivo().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fallback basado en el signo de la cantidad
            }
        }

        return item.getCantidad() >= 0 ? Movimiento.TipoMovimiento.COMPRA : Movimiento.TipoMovimiento.BAJA;
    }


    public byte[] generateMovimientosExcel(MovimientoExcelRequestDTO dto) {
        LocalDateTime startDateTime = dto.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = dto.getEndDate().atTime(LocalTime.MAX);

        Double totalAcumulado = transaccionAlmacenRepo.findTotalCantidadByProductoIdAndFechaMovimientoBefore(dto.getProductoId(), startDateTime);
        totalAcumulado = totalAcumulado != null ? totalAcumulado : 0.0;

        List<Movimiento> movimientos = transaccionAlmacenRepo
                .findByProducto_ProductoIdAndFechaMovimientoBetweenOrderByFechaMovimientoAsc(
                        dto.getProductoId(), startDateTime, endDateTime);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Movimientos");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Total hasta " + dto.getStartDate());
            header.createCell(1).setCellValue(totalAcumulado);

            int rowIdx = 1;
            for (Movimiento mov : movimientos) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(mov.getFechaMovimiento().toString());
                row.createCell(1).setCellValue(mov.getTipoMovimiento().name());
                row.createCell(2).setCellValue(mov.getCantidad());
                row.createCell(3).setCellValue(mov.getAlmacen() != null ? mov.getAlmacen().name() : "");
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error generating Excel", e);
        }
    }

    /**
     * Busca transacciones de almacén según los filtros especificados.
     * Soporta filtrado por tipo de entidad causante, fechas, proveedor,
     * ID de transacción/orden de producción, y producto terminado.
     *
     * @param filtro DTO con los criterios de filtrado
     * @return Página de transacciones que cumplen los criterios
     */
    public Page<TransaccionAlmacenResponseDTO> buscarTransaccionesAlmacenFiltradas(
            FiltroHistorialTransaccionesDTO filtro) {

        Page<TransaccionAlmacen> resultado;

        switch (filtro.getTipoEntidadCausante()) {
            case "OCM":
                resultado = buscarTransaccionesOCM(filtro);
                break;

            case "OP":
                // TODO: Implementar lógica para OP (Orden de Producción)
                throw new UnsupportedOperationException("Filtro OP aún no implementado");

            case "OAA":
                // TODO: Implementar lógica para OAA (Orden de Ajuste de Almacén)
                throw new UnsupportedOperationException("Filtro OAA aún no implementado");

            case "OD":
                // TODO: Implementar lógica para OD (Orden de Despacho)
                throw new UnsupportedOperationException("Filtro OD aún no implementado");

            case "CM":
                // TODO: Implementar lógica para CM (Carga Masiva)
                throw new UnsupportedOperationException("Filtro CM aún no implementado");

            default:
                throw new IllegalArgumentException("Tipo de entidad causante no válido: " + filtro.getTipoEntidadCausante());
        }

        return resultado.map(this::convertirATransaccionAlmacenResponseDTO);
    }

    private Page<TransaccionAlmacen> buscarTransaccionesOCM(FiltroHistorialTransaccionesDTO filtro) {
        Pageable pageable = PageRequest.of(
                filtro.getPage(),
                filtro.getSize(),
                Sort.by("fechaTransaccion").descending()
        );

        boolean tieneFiltroProveedor = filtro.getProveedorId() != null && !filtro.getProveedorId().isBlank();
        boolean tieneFiltroFecha = filtro.getTipoFiltroFecha() != null && filtro.getTipoFiltroFecha() > 0;

        LocalDateTime fechaInicio = null;
        LocalDateTime fechaFin = null;

        if (tieneFiltroFecha) {
            if (filtro.getTipoFiltroFecha() == 1) {
                if (filtro.getFechaInicio() == null || filtro.getFechaFin() == null) {
                    tieneFiltroFecha = false;
                } else {
                    if (filtro.getFechaInicio().isAfter(filtro.getFechaFin())) {
                        throw new RuntimeException("La fecha de inicio no puede ser posterior a la fecha de fin");
                    }
                    fechaInicio = filtro.getFechaInicio().atStartOfDay();
                    fechaFin = filtro.getFechaFin().atTime(23, 59, 59, 999999999);
                }
            } else if (filtro.getTipoFiltroFecha() == 2) {
                if (filtro.getFechaEspecifica() == null) {
                    tieneFiltroFecha = false;
                } else {
                    fechaInicio = filtro.getFechaEspecifica().atStartOfDay();
                    fechaFin = filtro.getFechaEspecifica().atTime(23, 59, 59, 999999999);
                }
            }
        }

        if (!tieneFiltroProveedor && !tieneFiltroFecha) {
            return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteOrderByFechaTransaccionDesc(
                    TransaccionAlmacen.TipoEntidadCausante.OCM, pageable);
        }

        if (tieneFiltroProveedor && !tieneFiltroFecha) {
            return transaccionAlmacenHeaderRepo.findOCMByProveedor(
                    TransaccionAlmacen.TipoEntidadCausante.OCM,
                    filtro.getProveedorId(), pageable);
        }

        if (!tieneFiltroProveedor && tieneFiltroFecha) {
            return transaccionAlmacenHeaderRepo.findByTipoEntidadCausanteAndFechaBetween(
                    TransaccionAlmacen.TipoEntidadCausante.OCM,
                    fechaInicio, fechaFin, pageable);
        }

        // Both filters active
        return transaccionAlmacenHeaderRepo.findOCMByProveedorAndFechaBetween(
                TransaccionAlmacen.TipoEntidadCausante.OCM,
                filtro.getProveedorId(),
                fechaInicio, fechaFin, pageable);
    }

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

        if (transaccion.getUsuarioAprobador() != null) {
            TransaccionAlmacenResponseDTO.UsuarioAprobadorDTO usuarioDTO =
                    new TransaccionAlmacenResponseDTO.UsuarioAprobadorDTO();
            usuarioDTO.setUserId(transaccion.getUsuarioAprobador().getId());
            usuarioDTO.setNombre(transaccion.getUsuarioAprobador().getNombreCompleto());
            dto.setUsuarioAprobador(usuarioDTO);
        }

        return dto;
    }
}
