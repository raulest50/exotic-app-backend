package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.IngresoMasivoRequestDTO;
import exotic.app.planta.model.inventarios.dto.IngresoMasivoResponseDTO;
import exotic.app.planta.model.inventarios.dto.IngresoTerminadoConsultaResponseDTO;
import exotic.app.planta.model.inventarios.dto.IngresoTerminadoRequestDTO;
import exotic.app.planta.model.inventarios.dto.ReporteHyLRequestDTO;
import exotic.app.planta.model.inventarios.dto.TerminadoInfoDTO;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngresoTerminadosAlmacenService {

    private static final String[] HYL_HEADERS = {
            "codigo", "nombre", "precio1", "precio2", "precio3", "precio4", "cantidad", "costo"
    };

    private final OrdenProduccionRepo ordenProduccionRepo;
    private final TerminadoRepo terminadoRepo;
    private final LoteRepo loteRepo;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;
    private final UserRepository userRepository;
    private final Clock applicationClock;

    /**
     * Busca una OrdenProduccion activa (no TERMINADA ni CANCELADA) por su loteAsignado exacto.
     * Retorna un DTO con la OP, el Terminado asociado y el loteSize esperado según la Categoria.
     *
     * Temporalmente en desuso: el flujo activo de ingreso de terminados se está usando para
     * reporte diario consolidado por terminado, sin cierre de OP. Se conserva por posible
     * reintegración del workflow por lote/orden de producción.
     *
     * @param loteAsignado Número de lote exacto a buscar
     * @return DTO de consulta con datos de la OP y del producto terminado
     * @throws ResponseStatusException 404 si no existe, 409 si ya está terminada o cancelada
     */
    @Transactional(readOnly = true)
    public IngresoTerminadoConsultaResponseDTO buscarOpPorLote(String loteAsignado) {
        OrdenProduccion op = ordenProduccionRepo.findByLoteAsignado(loteAsignado)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró ninguna Orden de Producción con lote: " + loteAsignado));

        if (op.getEstadoOrden() == 2) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La Orden de Producción con lote " + loteAsignado + " ya se encuentra TERMINADA.");
        }
        if (op.getEstadoOrden() == -1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La Orden de Producción con lote " + loteAsignado + " está CANCELADA.");
        }

        if (!(op.getProducto() instanceof Terminado)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "El producto de la Orden de Producción con lote " + loteAsignado + " no es un Producto Terminado.");
        }

        Terminado terminado = (Terminado) op.getProducto();

        // Inicializar solo la categoría (única relación lazy necesaria para el wizard)
        Hibernate.initialize(terminado.getCategoria());

        int loteSizeEsperado = (terminado.getCategoria() != null)
                ? terminado.getCategoria().getLoteSize()
                : 0;

        // Mapear OrdenProduccion → OrdenProduccionDTO (solo escalares, sin entidades JPA)
        OrdenProduccionDTO opDTO = new OrdenProduccionDTO();
        opDTO.setOrdenId(op.getOrdenId());
        opDTO.setLoteAsignado(op.getLoteAsignado());
        opDTO.setEstadoOrden(op.getEstadoOrden());
        opDTO.setPoliticaDispensacionInicio(op.getPoliticaDispensacionInicio() != null
                ? op.getPoliticaDispensacionInicio().name()
                : null);
        opDTO.setFechaAplicacionPoliticaDispensacion(op.getFechaAplicacionPoliticaDispensacion());
        opDTO.setEstadoDispensacionMateriales(op.getEstadoDispensacionMateriales() != null
                ? op.getEstadoDispensacionMateriales().name()
                : null);
        opDTO.setCantidadProducir(op.getCantidadProducir());
        opDTO.setFechaCreacion(op.getFechaCreacion());
        opDTO.setFechaLanzamiento(op.getFechaLanzamiento());
        opDTO.setFechaFinalPlanificada(op.getFechaFinalPlanificada());
        opDTO.setFechaInicio(op.getFechaInicio());
        opDTO.setAreaOperativa(op.getAreaOperativa());
        opDTO.setDepartamentoOperativo(op.getDepartamentoOperativo());
        opDTO.setNumeroPedidoComercial(op.getNumeroPedidoComercial());
        opDTO.setObservaciones(op.getObservaciones());

        // Mapear Terminado → TerminadoInfoDTO (solo escalares + categoría anidada)
        TerminadoInfoDTO terminadoDTO = new TerminadoInfoDTO();
        terminadoDTO.setProductoId(terminado.getProductoId());
        terminadoDTO.setNombre(terminado.getNombre());
        terminadoDTO.setTipoUnidades(terminado.getTipoUnidades());
        terminadoDTO.setCantidadUnidad(terminado.getCantidadUnidad());
        terminadoDTO.setFotoUrl(terminado.getFotoUrl());
        terminadoDTO.setPrefijoLote(terminado.getPrefijoLote());
        terminadoDTO.setCosto(terminado.getCosto());
        terminadoDTO.setIvaPercentual(terminado.getIvaPercentual());
        terminadoDTO.setStatus(terminado.getStatus());
        terminadoDTO.setObservaciones(terminado.getObservaciones());

        if (terminado.getCategoria() != null) {
            TerminadoInfoDTO.CategoriaInfoDTO catDTO = new TerminadoInfoDTO.CategoriaInfoDTO(
                    terminado.getCategoria().getCategoriaId(),
                    terminado.getCategoria().getCategoriaNombre(),
                    terminado.getCategoria().getCategoriaDescripcion(),
                    terminado.getCategoria().getLoteSize(),
                    terminado.getCategoria().getTiempoDiasFabricacion()
            );
            terminadoDTO.setCategoria(catDTO);
        }

        return new IngresoTerminadoConsultaResponseDTO(opDTO, terminadoDTO, loteSizeEsperado);
    }

    /**
     * Registra el ingreso de producto terminado al almacén general y cierra la OrdenProduccion.
     * Crea una TransaccionAlmacen de tipo OP con un Movimiento BACKFLUSH en almacén GENERAL.
     * Actualiza el estadoOrden de la OP a 2 (TERMINADA).
     *
     * Temporalmente en desuso: el flujo activo solo genera reporte diario consolidado por
     * terminado. Este método se conserva para una posible reintegración del cierre por lote/OP.
     *
     * @param dto           Datos del ingreso: ordenProduccionId, cantidadIngresada, fechaVencimiento
     * @return La TransaccionAlmacen persistida
     * @throws ResponseStatusException si la OP no existe, ya está terminada, o la cantidad es inválida
     */
    @Transactional(rollbackFor = Exception.class)
    public TransaccionAlmacen registrarIngresoTerminado(IngresoTerminadoRequestDTO dto) {
        if (dto.getCantidadIngresada() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cantidad ingresada debe ser mayor que cero.");
        }

        OrdenProduccion op = ordenProduccionRepo.findById(dto.getOrdenProduccionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Orden de Producción no encontrada con ID: " + dto.getOrdenProduccionId()));

        if (op.getEstadoOrden() == 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La Orden de Producción ya se encuentra TERMINADA.");
        }
        if (op.getEstadoOrden() == -1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La Orden de Producción está CANCELADA.");
        }

        if (!(op.getProducto() instanceof Terminado)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El producto de la Orden de Producción no es un Producto Terminado.");
        }

        Terminado terminado = (Terminado) op.getProducto();

        // Crear o reutilizar el Lote para esta OP
        List<Lote> lotesExistentes = loteRepo.findByOrdenProduccion_OrdenId(op.getOrdenId());
        Lote lote;
        if (!lotesExistentes.isEmpty()) {
            lote = lotesExistentes.get(0);
            // Actualizar fecha de vencimiento si fue proporcionada
            if (dto.getFechaVencimiento() != null) {
                lote.setExpirationDate(dto.getFechaVencimiento());
                loteRepo.save(lote);
            }
        } else {
            lote = new Lote();
            lote.setBatchNumber(op.getLoteAsignado());
            lote.setProductionDate(LocalDate.now(applicationClock));
            lote.setExpirationDate(dto.getFechaVencimiento());
            lote.setOrdenProduccion(op);
            lote = loteRepo.save(lote);
        }

        // Construir la TransaccionAlmacen
        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setTipoEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.OP);
        transaccion.setIdEntidadCausante(op.getOrdenId());
        transaccion.setEstadoContable(TransaccionAlmacen.EstadoContable.NO_APLICA);
        transaccion.setObservaciones(dto.getObservaciones());

        if (dto.getUsername() != null && !dto.getUsername().isEmpty()) {
            Optional<User> userOpt = userRepository.findByUsername(dto.getUsername());
            userOpt.ifPresent(u -> {
                transaccion.setUsuarioAprobador(u);
                transaccion.setUsuariosResponsables(List.of(u));
            });
        }

        // Construir el Movimiento de ingreso
        Movimiento movimiento = new Movimiento();
        movimiento.setCantidad(dto.getCantidadIngresada());
        movimiento.setProducto(terminado);
        movimiento.setLote(lote);
        movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.BACKFLUSH);
        movimiento.setAlmacen(Movimiento.Almacen.GENERAL);
        movimiento.setTransaccionAlmacen(transaccion);

        List<Movimiento> movimientos = new ArrayList<>();
        movimientos.add(movimiento);
        transaccion.setMovimientosTransaccion(movimientos);

        TransaccionAlmacen saved = transaccionAlmacenHeaderRepo.save(transaccion);

        // Cerrar la OrdenProduccion
        ordenProduccionRepo.updateEstadoOrdenById(op.getOrdenId(), 2, java.time.LocalDateTime.now(applicationClock));

        log.info("Ingreso de producto terminado registrado: OP={}, lote={}, cantidad={}, transaccionId={}",
                op.getOrdenId(), op.getLoteAsignado(), dto.getCantidadIngresada(), saved.getTransaccionId());

        return saved;
    }

    /**
     * Genera una plantilla Excel consolidada con todos los productos terminados.
     * El flujo por lote/OP queda temporalmente en desuso y se conserva en los métodos
     * de registro por posible reintegración futura.
     *
     * @return byte[] con el contenido del archivo Excel
     */
    @Transactional(readOnly = true)
    public byte[] generarPlantillaExcel() {
        return generarPlantillaExcel(LocalDate.now(applicationClock));
    }

    /**
     * Genera una plantilla Excel consolidada con todos los productos terminados
     * y replica fechaReporte en cada fila para facilitar formulas externas.
     *
     * @return byte[] con el contenido del archivo Excel
     */
    @Transactional(readOnly = true)
    public byte[] generarPlantillaExcel(LocalDate fechaReporte) {
        LocalDate fechaEfectiva = fechaReporte != null ? fechaReporte : LocalDate.now(applicationClock);
        List<Terminado> terminados = terminadoRepo.findAllConCategoriaOrderByProductoIdAsc()
                .stream()
                .sorted(Comparator
                        .comparing((Terminado terminado) -> categoriaNombrePlantilla(terminado).toLowerCase())
                        .thenComparing((Terminado terminado) ->
                                terminado.getProductoId() != null ? terminado.getProductoId().toLowerCase() : ""))
                .toList();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Produccion Diaria PT");

            // Estilo para headers
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Estilo para celdas editables (verde claro)
            CellStyle editableStyle = workbook.createCellStyle();
            editableStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            editableStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            editableStyle.setBorderBottom(BorderStyle.THIN);
            editableStyle.setBorderTop(BorderStyle.THIN);
            editableStyle.setBorderLeft(BorderStyle.THIN);
            editableStyle.setBorderRight(BorderStyle.THIN);

            // Estilo para celdas de solo lectura
            CellStyle readOnlyStyle = workbook.createCellStyle();
            readOnlyStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            readOnlyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            readOnlyStyle.setBorderBottom(BorderStyle.THIN);
            readOnlyStyle.setBorderTop(BorderStyle.THIN);
            readOnlyStyle.setBorderLeft(BorderStyle.THIN);
            readOnlyStyle.setBorderRight(BorderStyle.THIN);

            // Headers (fila 0)
            String[] headers = {
                "producto_id", "producto_nombre", "categoria_nombre", "cantidad_producida", "fecha_reporte"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            sheet.createFreezePane(0, 1);

            // Filas de datos
            int rowIdx = 1;
            for (Terminado terminado : terminados) {
                Row row = sheet.createRow(rowIdx++);

                // Columna A: producto_id (solo lectura)
                Cell cellProductoId = row.createCell(0);
                cellProductoId.setCellValue(terminado.getProductoId() != null ? terminado.getProductoId() : "");
                cellProductoId.setCellStyle(readOnlyStyle);

                // Columna B: producto_nombre (solo lectura)
                Cell cellProductoNombre = row.createCell(1);
                cellProductoNombre.setCellValue(terminado.getNombre() != null ? terminado.getNombre() : "");
                cellProductoNombre.setCellStyle(readOnlyStyle);

                // Columna C: categoria_nombre (solo lectura)
                Cell cellCategoria = row.createCell(2);
                cellCategoria.setCellValue(categoriaNombrePlantilla(terminado));
                cellCategoria.setCellStyle(readOnlyStyle);

                // Columna D: cantidad_producida (EDITABLE - vacía equivale a cero)
                Cell cellCantidadProducida = row.createCell(3);
                cellCantidadProducida.setCellStyle(editableStyle);

                // Columna E: fecha_reporte (solo lectura - redundante para formulas externas)
                Cell cellFechaReporte = row.createCell(4);
                cellFechaReporte.setCellValue(fechaEfectiva.toString());
                cellFechaReporte.setCellStyle(readOnlyStyle);
            }

            // Ajustar ancho de columnas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Convertir a bytes
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }

        } catch (IOException e) {
            log.error("Error generando plantilla Excel de reporte consolidado de terminados", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error generando plantilla Excel: " + e.getMessage());
        }
    }

    private static String categoriaNombrePlantilla(Terminado terminado) {
        if (terminado == null
                || terminado.getCategoria() == null
                || terminado.getCategoria().getCategoriaNombre() == null
                || terminado.getCategoria().getCategoriaNombre().isBlank()) {
            return "Sin categoria";
        }
        return terminado.getCategoria().getCategoriaNombre();
    }

    /**
     * Genera un archivo .xls binario real (BIFF8/OLE2) para el reporte HyL.
     * La fuente son las filas ya validadas por el asistente de ingreso de terminados.
     */
    @Transactional(readOnly = true)
    public byte[] generarReporteHyLXls(ReporteHyLRequestDTO request) {
        List<ReporteHyLRow> rows = consolidarReporteHyL(request);
        Map<String, Terminado> terminadosById = cargarTerminadosHyL(rows);

        try (Workbook workbook = new HSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte HyL");
            escribirReporteHyLHeader(sheet);

            int rowIdx = 1;
            for (ReporteHyLRow row : rows) {
                double costo = request.isCostosEnCero()
                        ? 0d
                        : terminadosById.get(row.productoId()).getCosto();
                escribirReporteHyLRow(sheet.createRow(rowIdx++), row, costo);
            }

            for (int col = 0; col < HYL_HEADERS.length; col++) {
                sheet.autoSizeColumn(col);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Error generando reporte HyL XLS", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error generando reporte HyL: " + e.getMessage());
        }
    }

    private List<ReporteHyLRow> consolidarReporteHyL(ReporteHyLRequestDTO request) {
        if (request == null || request.getIngresos() == null || request.getIngresos().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El reporte HyL requiere al menos un producto con producción positiva.");
        }

        Map<String, ReporteHyLRow> byProductoId = new LinkedHashMap<>();
        for (ReporteHyLRequestDTO.IngresoHyLItemDTO item : request.getIngresos()) {
            if (item == null || item.getCantidadProducida() <= 0) {
                continue;
            }
            String productoId = normalizeRequired(item.getProductoId(), "productoId");
            String productoNombre = normalizeRequired(item.getProductoNombre(), "productoNombre");
            byProductoId.compute(
                    productoId,
                    (id, current) -> current == null
                            ? new ReporteHyLRow(id, productoNombre, item.getCantidadProducida())
                            : current.addCantidad(item.getCantidadProducida()));
        }

        if (byProductoId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El reporte HyL requiere al menos un producto con producción positiva.");
        }
        return new ArrayList<>(byProductoId.values());
    }

    private Map<String, Terminado> cargarTerminadosHyL(Collection<ReporteHyLRow> rows) {
        Set<String> productoIds = rows.stream()
                .map(ReporteHyLRow::productoId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        Map<String, Terminado> terminadosById = terminadoRepo.findByProductoIdIn(productoIds)
                .stream()
                .collect(Collectors.toMap(Terminado::getProductoId, (terminado) -> terminado));

        List<String> missing = productoIds.stream()
                .filter((productoId) -> !terminadosById.containsKey(productoId))
                .toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontró el producto terminado " + String.join(", ", missing) + " en el catálogo.");
        }
        return terminadosById;
    }

    private static void escribirReporteHyLHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        for (int col = 0; col < HYL_HEADERS.length; col++) {
            headerRow.createCell(col).setCellValue(HYL_HEADERS[col]);
        }
    }

    private static void escribirReporteHyLRow(Row excelRow, ReporteHyLRow row, double costo) {
        excelRow.createCell(0).setCellValue(row.productoId());
        excelRow.createCell(1).setCellValue(row.productoNombre());
        excelRow.createCell(2).setCellValue("");
        excelRow.createCell(3).setCellValue("");
        excelRow.createCell(4).setCellValue("");
        excelRow.createCell(5).setCellValue("");
        excelRow.createCell(6).setCellValue(row.cantidadProducida());
        excelRow.createCell(7).setCellValue(costo);
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El campo " + fieldName + " es obligatorio para el reporte HyL.");
        }
        return value.trim();
    }

    private record ReporteHyLRow(String productoId, String productoNombre, double cantidadProducida) {
        private ReporteHyLRow addCantidad(double cantidadAdicional) {
            return new ReporteHyLRow(productoId, productoNombre, cantidadProducida + cantidadAdicional);
        }
    }

    /**
     * Registra múltiples ingresos de producto terminado procesando cada uno de forma independiente.
     * Si un ingreso falla, continúa con los demás y reporta el error en el resultado.
     *
     * Temporalmente en desuso: actualmente el asistente frontend no llama este endpoint porque el
     * objetivo es generar el reporte diario consolidado sin cerrar OPs. Se conserva por posible
     * reintegración del workflow por lote/orden.
     *
     * @param request DTO con el username y la lista de ingresos a procesar
     * @return DTO con el resumen del proceso y los resultados individuales
     */
    public IngresoMasivoResponseDTO registrarIngresosMasivos(IngresoMasivoRequestDTO request) {
        List<IngresoMasivoResponseDTO.IngresoResultadoDTO> resultados = new ArrayList<>();
        int exitosos = 0;
        int fallidos = 0;

        for (IngresoMasivoRequestDTO.IngresoItemDTO item : request.getIngresos()) {
            String loteAsignado = obtenerLoteAsignado(item.getOrdenProduccionId());

            try {
                // Reutilizar lógica existente de registrarIngresoTerminado()
                IngresoTerminadoRequestDTO dto = new IngresoTerminadoRequestDTO(
                        request.getUsername(),
                        item.getOrdenProduccionId(),
                        item.getCantidadIngresada(),
                        item.getFechaVencimiento(),
                        "" // observaciones vacías para ingreso masivo
                );

                TransaccionAlmacen tx = registrarIngresoTerminado(dto);

                resultados.add(new IngresoMasivoResponseDTO.IngresoResultadoDTO(
                        item.getOrdenProduccionId(),
                        loteAsignado,
                        true,
                        null,
                        (Integer) tx.getTransaccionId()
                ));
                exitosos++;

            } catch (Exception e) {
                log.warn("Error procesando ingreso masivo para OP {}: {}", item.getOrdenProduccionId(), e.getMessage());
                resultados.add(new IngresoMasivoResponseDTO.IngresoResultadoDTO(
                        item.getOrdenProduccionId(),
                        loteAsignado,
                        false,
                        e.getMessage(),
                        null
                ));
                fallidos++;
            }
        }

        return new IngresoMasivoResponseDTO(
                fallidos == 0,
                request.getIngresos().size(),
                exitosos,
                fallidos,
                resultados
        );
    }

    /**
     * Obtiene el loteAsignado de una OrdenProduccion para mostrar en los resultados.
     */
    private String obtenerLoteAsignado(int ordenProduccionId) {
        return ordenProduccionRepo.findById(ordenProduccionId)
                .map(OrdenProduccion::getLoteAsignado)
                .orElse("N/A");
    }
}
