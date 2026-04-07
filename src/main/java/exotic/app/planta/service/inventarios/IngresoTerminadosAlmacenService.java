package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.IngresoMasivoRequestDTO;
import exotic.app.planta.model.inventarios.dto.IngresoMasivoResponseDTO;
import exotic.app.planta.model.inventarios.dto.IngresoTerminadoConsultaResponseDTO;
import exotic.app.planta.model.inventarios.dto.IngresoTerminadoRequestDTO;
import exotic.app.planta.model.inventarios.dto.TerminadoInfoDTO;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngresoTerminadosAlmacenService {

    private final OrdenProduccionRepo ordenProduccionRepo;
    private final LoteRepo loteRepo;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;
    private final UserRepository userRepository;

    /**
     * Busca una OrdenProduccion activa (no TERMINADA ni CANCELADA) por su loteAsignado exacto.
     * Retorna un DTO con la OP, el Terminado asociado y el loteSize esperado según la Categoria.
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
                    terminado.getCategoria().getLoteSize()
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
            lote.setProductionDate(LocalDate.now());
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
        ordenProduccionRepo.updateEstadoOrdenById(op.getOrdenId(), 2);

        log.info("Ingreso de producto terminado registrado: OP={}, lote={}, cantidad={}, transaccionId={}",
                op.getOrdenId(), op.getLoteAsignado(), dto.getCantidadIngresada(), saved.getTransaccionId());

        return saved;
    }

    /**
     * Genera una plantilla Excel con las OPs abiertas de productos terminados.
     * Se consideran abiertas las ordenes no TERMINADAS (2) ni CANCELADAS (-1).
     * Las columnas de datos de la OP están pre-llenadas y las columnas editables
     * (cantidad_ingresada, fecha_vencimiento) se resaltan con fondo verde claro.
     *
     * @return byte[] con el contenido del archivo Excel
     */
    @Transactional(readOnly = true)
    public byte[] generarPlantillaExcel() {
        List<OrdenProduccion> ordenesPendientes = ordenProduccionRepo.findOrdenesPendientesConTerminado();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Ingreso Producto Terminado");

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

            // Estilo para fechas editables
            CellStyle dateEditableStyle = workbook.createCellStyle();
            dateEditableStyle.cloneStyleFrom(editableStyle);
            CreationHelper createHelper = workbook.getCreationHelper();
            dateEditableStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));

            // Headers (fila 0)
            String[] headers = {
                "lote_asignado", "orden_id", "producto_id", "producto_nombre",
                "categoria_nombre", "cantidad_esperada", "cantidad_ingresada", "fecha_vencimiento"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Fecha por defecto: hoy + 2 años
            LocalDate fechaVencimientoDefault = LocalDate.now().plusYears(2);

            // Filas de datos
            int rowIdx = 1;
            for (OrdenProduccion op : ordenesPendientes) {
                if (!(op.getProducto() instanceof Terminado)) {
                    continue;
                }

                Terminado terminado = (Terminado) op.getProducto();
                Row row = sheet.createRow(rowIdx++);

                // Columna A: lote_asignado (solo lectura)
                Cell cellLote = row.createCell(0);
                cellLote.setCellValue(op.getLoteAsignado() != null ? op.getLoteAsignado() : "");
                cellLote.setCellStyle(readOnlyStyle);

                // Columna B: orden_id (solo lectura)
                Cell cellOrdenId = row.createCell(1);
                cellOrdenId.setCellValue(op.getOrdenId());
                cellOrdenId.setCellStyle(readOnlyStyle);

                // Columna C: producto_id (solo lectura)
                Cell cellProductoId = row.createCell(2);
                cellProductoId.setCellValue(terminado.getProductoId() != null ? terminado.getProductoId() : "");
                cellProductoId.setCellStyle(readOnlyStyle);

                // Columna D: producto_nombre (solo lectura)
                Cell cellProductoNombre = row.createCell(3);
                cellProductoNombre.setCellValue(terminado.getNombre() != null ? terminado.getNombre() : "");
                cellProductoNombre.setCellStyle(readOnlyStyle);

                // Columna E: categoria_nombre (solo lectura)
                Cell cellCategoria = row.createCell(4);
                String categoriaNombre = (terminado.getCategoria() != null && terminado.getCategoria().getCategoriaNombre() != null)
                        ? terminado.getCategoria().getCategoriaNombre()
                        : "";
                cellCategoria.setCellValue(categoriaNombre);
                cellCategoria.setCellStyle(readOnlyStyle);

                // Columna F: cantidad_esperada (solo lectura)
                Cell cellCantidadEsperada = row.createCell(5);
                cellCantidadEsperada.setCellValue(op.getCantidadProducir());
                cellCantidadEsperada.setCellStyle(readOnlyStyle);

                // Columna G: cantidad_ingresada (EDITABLE - vacía)
                Cell cellCantidadIngresada = row.createCell(6);
                cellCantidadIngresada.setCellStyle(editableStyle);

                // Columna H: fecha_vencimiento (EDITABLE - pre-llenada con hoy + 2 años)
                Cell cellFechaVencimiento = row.createCell(7);
                cellFechaVencimiento.setCellValue(java.sql.Date.valueOf(fechaVencimientoDefault));
                cellFechaVencimiento.setCellStyle(dateEditableStyle);
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
            log.error("Error generando plantilla Excel de ingreso masivo", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error generando plantilla Excel: " + e.getMessage());
        }
    }

    /**
     * Registra múltiples ingresos de producto terminado procesando cada uno de forma independiente.
     * Si un ingreso falla, continúa con los demás y reporta el error en el resultado.
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
