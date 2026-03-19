package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
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
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
}
