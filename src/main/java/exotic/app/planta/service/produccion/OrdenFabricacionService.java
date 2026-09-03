package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.EstadoDispensacionMateriales;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.PoliticaDispensacionInicio;
import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.TipoEventoSeguimiento;
import exotic.app.planta.model.produccion.dto.OrdenFabricacionDTOs;
import exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionEventoRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class OrdenFabricacionService {

    private static final BigDecimal CANTIDAD_MAXIMA =
            new BigDecimal("99999999999999.9999");

    private final OrdenFabricacionRepo ordenRepo;
    private final SemiTerminadoRepo semiTerminadoRepo;
    private final ManufacturingVersionRepo manufacturingVersionRepo;
    private final LoteRepo loteRepo;
    private final UserRepository userRepository;
    private final BatchRecordRepo batchRecordRepo;
    private final VencimientoLoteService vencimientoLoteService;
    private final BatchRecordService batchRecordService;
    private final MasterDirectiveService masterDirectiveService;
    private final OrdenFabricacionOperacionService operacionService;
    private final OrdenFabricacionOperacionEventoRepo operacionEventoRepo;
    private final TransaccionAlmacenHeaderRepo transaccionRepo;
    private final LoteManufacturaNumeroService loteNumeroService;
    private final Clock applicationClock;

    public OrdenFabricacionDTOs.Response crear(
            OrdenFabricacionDTOs.CreateRequest request,
            User actor
    ) {
        if (request == null || actor == null) {
            throw new IllegalArgumentException("La solicitud y el usuario autenticado son obligatorios.");
        }
        String loteNumero = normalizarObligatorio(request.getLote(),
                "El número de lote es obligatorio.");
        if (loteRepo.findByBatchNumber(loteNumero) != null) {
            throw new IllegalArgumentException("El lote '" + loteNumero + "' ya existe.");
        }

        SemiTerminado semi = semiTerminadoRepo.findById(
                        normalizarObligatorio(request.getSemiTerminadoId(),
                                "El semiterminado es obligatorio."))
                .orElseThrow(() -> new NoSuchElementException("Semiterminado no encontrado."));
        if (!semi.isRequiereOrdenFabricacion()) {
            throw new IllegalArgumentException(
                    "El semiterminado no está configurado para orden de fabricación.");
        }
        ManufacturingVersions version = manufacturingVersionRepo
                .findTopByProductoOrderByVersionNumberDesc(semi)
                .orElseThrow(() -> new IllegalStateException(
                        "El semiterminado no tiene una versión de manufactura disponible."));
        User responsable = request.getResponsableId() == null
                ? actor
                : userRepository.findById(request.getResponsableId())
                .orElseThrow(() -> new NoSuchElementException("Responsable no encontrado."));

        return crearInterna(
                semi,
                version,
                request.getCantidadPlanificada(),
                loteNumero,
                request.getFechaLanzamiento(),
                request.getFechaFinalPlanificada(),
                actor,
                responsable,
                normalizar(request.getObservaciones()),
                null,
                false);
    }

    /** Emite una OF ligada a una OP; se usa dentro de la misma transaccion creadora. */
    public OrdenFabricacionDTOs.Response crearAutomatica(
            SemiTerminado semi,
            BigDecimal cantidad,
            OrdenProduccion ordenOrigen,
            User actor
    ) {
        if (semi == null || ordenOrigen == null || actor == null) {
            throw new IllegalArgumentException(
                    "Semiterminado, OP origen y usuario son obligatorios para la OF automatica.");
        }
        if (ordenRepo.existsByOrdenProduccionOrigen_OrdenIdAndSemiTerminado_ProductoId(
                ordenOrigen.getOrdenId(), semi.getProductoId())) {
            throw new IllegalStateException(
                    "La OP ya tiene una OF para el semiterminado " + semi.getProductoId() + ".");
        }
        ManufacturingVersions version = manufacturingVersionRepo
                .findTopByProductoOrderByVersionNumberDesc(semi)
                .orElseThrow(() -> new IllegalStateException(
                        "El semiterminado " + semi.getProductoId()
                                + " no tiene una version de manufactura disponible."));
        String loteNumero = loteNumeroService.siguiente(semi.getProductoId());
        return crearInterna(
                semi, version, cantidad, loteNumero,
                LocalDateTime.now(applicationClock),
                ordenOrigen.getFechaFinalPlanificada(),
                actor, actor,
                "Generada automaticamente para abastecer la OP " + ordenOrigen.getOrdenId(),
                ordenOrigen, true);
    }

    private OrdenFabricacionDTOs.Response crearInterna(
            SemiTerminado semi,
            ManufacturingVersions version,
            BigDecimal cantidad,
            String loteNumero,
            LocalDateTime fechaLanzamiento,
            LocalDateTime fechaFinalPlanificada,
            User actor,
            User responsable,
            String observaciones,
            OrdenProduccion ordenOrigen,
            boolean liberarInmediatamente
    ) {
        boolean batchRecordWorkflowEnabled =
                masterDirectiveService.lockBatchRecordWorkflowForNewOrder();
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad planificada de la OF debe ser mayor que cero.");
        }
        BigDecimal cantidadNormalizada = cantidad.setScale(4, RoundingMode.HALF_UP);
        if (cantidadNormalizada.signum() <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad planificada es menor que la precision admitida (0.0001).");
        }
        if (cantidadNormalizada.compareTo(CANTIDAD_MAXIMA) > 0) {
            throw new IllegalArgumentException(
                    "La cantidad planificada excede el valor maximo admitido.");
        }
        boolean liberar = liberarInmediatamente || fechaLanzamiento == null
                || !fechaLanzamiento.isAfter(ahora);
        OrdenFabricacion orden = new OrdenFabricacion();
        orden.setSemiTerminado(semi);
        orden.setManufacturingVersion(version);
        orden.setOrdenProduccionOrigen(ordenOrigen);
        orden.setEstado(liberar
                ? EstadoOrdenFabricacion.LIBERADA
                : EstadoOrdenFabricacion.PLANIFICADA);
        orden.setLiberadaEn(liberar ? ahora : null);
        orden.setCantidadPlanificada(cantidadNormalizada);
        orden.setUnidadMedida(normalizarObligatorio(
                semi.getTipoUnidades(), "El semiterminado no tiene unidad de medida."));
        orden.setFechaLanzamiento(fechaLanzamiento);
        orden.setFechaFinalPlanificada(fechaFinalPlanificada);
        orden.setCreadaPor(actor);
        orden.setResponsable(responsable);
        orden.setObservaciones(observaciones);
        boolean noBloqueante = masterDirectiveService.isDispensacionNoBloqueaInicioProduccion();
        orden.setPoliticaDispensacionInicio(noBloqueante
                ? PoliticaDispensacionInicio.NO_BLOQUEANTE
                : PoliticaDispensacionInicio.BLOQUEANTE);
        orden.setFechaAplicacionPoliticaDispensacion(ahora);
        orden.setEstadoDispensacionMateriales(noBloqueante
                ? EstadoDispensacionMateriales.LIBERADA_SIN_DISPENSACION
                : EstadoDispensacionMateriales.PENDIENTE);
        ordenRepo.saveAndFlush(orden);

        Lote lote = new Lote();
        lote.setBatchNumber(loteNumero);
        lote.setProducto(semi);
        lote.setOrdenFabricacion(orden);
        lote.setEstadoCalidad(batchRecordWorkflowEnabled
                ? EstadoCalidadLote.CUARENTENA
                : EstadoCalidadLote.SIN_CLASIFICAR);
        vencimientoLoteService.copiarPoliticaVigente(semi, lote);
        loteRepo.saveAndFlush(lote);

        BatchRecord record = batchRecordWorkflowEnabled
                ? batchRecordService.crearParaOrdenFabricacion(orden, lote, actor)
                : null;
        operacionService.inicializar(orden, record);
        if (record != null) batchRecordService.materializarRequisitos(orden);
        return toResponse(orden, lote, record);
    }

    @Transactional(readOnly = true)
    public Page<OrdenFabricacionDTOs.SemiterminadoOption> buscarSemiterminadosElegibles(
            String search,
            int page,
            int size
    ) {
        return semiTerminadoRepo.buscarElegiblesOrdenFabricacion(
                        normalizar(search),
                        PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), 50),
                                Sort.by(Sort.Direction.ASC, "nombre")))
                .map(semi -> OrdenFabricacionDTOs.SemiterminadoOption.builder()
                        .productoId(semi.getProductoId())
                        .nombre(semi.getNombre())
                        .unidadMedida(semi.getTipoUnidades())
                        .build());
    }

    @Transactional(readOnly = true)
    public Page<OrdenFabricacionDTOs.Response> buscar(String search, int page, int size) {
        return ordenRepo.buscar(
                        normalizar(search),
                        PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.DESC, "fechaCreacion")
                        ))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrdenFabricacionDTOs.Response detalle(Long id) {
        OrdenFabricacion orden = requireEntity(id);
        return toResponse(orden);
    }

    @Transactional(readOnly = true)
    public OrdenFabricacionDTOs.Response detalleOperativo(Long id, Long userId) {
        if (userId == null || !operacionService.esResponsableDeAlgunaOperacion(id, userId)) {
            throw new AccessDeniedException(
                    "La OF no pertenece a un area operativa a cargo del usuario.");
        }
        return toResponse(requireEntity(id));
    }

    @Transactional(readOnly = true)
    public OrdenFabricacion requireEntity(Long id) {
        return ordenRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "Orden de fabricación no encontrada."));
    }

    public OrdenFabricacionDTOs.Response cancelar(Long id, User actor) {
        OrdenFabricacion orden = ordenRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Orden de fabricación no encontrada."));
        if (orden.getEstado() != EstadoOrdenFabricacion.BORRADOR
                && orden.getEstado() != EstadoOrdenFabricacion.PLANIFICADA
                && orden.getEstado() != EstadoOrdenFabricacion.LIBERADA) {
            throw new IllegalStateException(
                    "Solo una orden sin ejecucion puede cancelarse.");
        }
        if (operacionEventoRepo
                .existsByOperacion_OrdenFabricacion_OrdenFabricacionIdAndActorTipoAndTipoEvento(
                        id, ActorTipoEventoSeguimiento.USER, TipoEventoSeguimiento.OPERATIVO)
                || transaccionRepo.countByTipoEntidadCausanteAndIdEntidadCausante(
                        TransaccionAlmacen.TipoEntidadCausante.OD_OF,
                        Math.toIntExact(id)) > 0) {
            throw new IllegalStateException(
                    "La OF tiene ejecucion o dispensaciones y ya no puede cancelarse.");
        }
        orden.setEstado(EstadoOrdenFabricacion.CANCELADA);
        ordenRepo.save(orden);
        batchRecordService.anularPorCancelacion(orden, actor);
        return toResponse(orden);
    }

    /** Cancela de forma atomica las OF automaticas; cualquier evidencia bloquea la cancelacion de la OP. */
    public void cancelarVinculadasPorCancelacionOp(OrdenProduccion ordenProduccion, User actor) {
        if (ordenProduccion == null) return;
        List<OrdenFabricacion> vinculadas = ordenRepo.findByOrdenProduccionOrigen_OrdenId(
                ordenProduccion.getOrdenId());
        for (OrdenFabricacion orden : vinculadas) {
            if (orden.getEstado() == EstadoOrdenFabricacion.CANCELADA) continue;
            boolean tieneEjecucion = operacionEventoRepo
                    .existsByOperacion_OrdenFabricacion_OrdenFabricacionIdAndActorTipoAndTipoEvento(
                            orden.getOrdenFabricacionId(),
                            ActorTipoEventoSeguimiento.USER,
                            TipoEventoSeguimiento.OPERATIVO);
            boolean tieneDispensacion = transaccionRepo
                    .countByTipoEntidadCausanteAndIdEntidadCausante(
                            TransaccionAlmacen.TipoEntidadCausante.OD_OF,
                            Math.toIntExact(orden.getOrdenFabricacionId())) > 0;
            boolean estadoCancelable = orden.getEstado() == EstadoOrdenFabricacion.PLANIFICADA
                    || orden.getEstado() == EstadoOrdenFabricacion.LIBERADA
                    || orden.getEstado() == EstadoOrdenFabricacion.BORRADOR;
            if (!estadoCancelable || tieneEjecucion || tieneDispensacion) {
                throw new IllegalStateException(
                        "La OP no puede cancelarse porque su OF "
                                + orden.getOrdenFabricacionId()
                                + " ya tiene ejecucion o dispensacion.");
            }
        }
        for (OrdenFabricacion orden : vinculadas) {
            if (orden.getEstado() == EstadoOrdenFabricacion.CANCELADA) continue;
            orden.setEstado(EstadoOrdenFabricacion.CANCELADA);
            ordenRepo.save(orden);
            batchRecordService.anularPorCancelacion(orden, actor);
        }
    }

    private OrdenFabricacionDTOs.Response toResponse(OrdenFabricacion orden) {
        Lote lote = loteRepo.findByOrdenFabricacion_OrdenFabricacionId(
                        orden.getOrdenFabricacionId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "La orden de fabricación no tiene lote asociado."));
        BatchRecord record = batchRecordRepo
                .findByOrdenFabricacion_OrdenFabricacionId(orden.getOrdenFabricacionId())
                .orElse(null);
        return toResponse(orden, lote, record);
    }

    private OrdenFabricacionDTOs.Response toResponse(
            OrdenFabricacion orden,
            Lote lote,
            BatchRecord record
    ) {
        return OrdenFabricacionDTOs.Response.builder()
                .ordenFabricacionId(orden.getOrdenFabricacionId())
                .estado(orden.getEstado())
                .semiTerminadoId(orden.getSemiTerminado().getProductoId())
                .semiTerminadoNombre(orden.getSemiTerminado().getNombre())
                .manufacturingVersionId(orden.getManufacturingVersion().getId())
                .manufacturingVersionNumber(orden.getManufacturingVersion().getVersionNumber())
                .cantidadPlanificada(orden.getCantidadPlanificada())
                .unidadMedida(orden.getUnidadMedida())
                .loteId(lote.getId())
                .lote(lote.getBatchNumber())
                .estadoCalidadLote(lote.getEstadoCalidad())
                .batchRecordId(record == null ? null : record.getId())
                .batchRecordCodigo(record == null ? null : record.getCodigo())
                .fechaCreacion(orden.getFechaCreacion())
                .fechaLanzamiento(orden.getFechaLanzamiento())
                .fechaFinalPlanificada(orden.getFechaFinalPlanificada())
                .creadaPor(nombreUsuario(orden.getCreadaPor()))
                .responsable(nombreUsuario(orden.getResponsable()))
                .observaciones(orden.getObservaciones())
                .ordenProduccionOrigenId(orden.getOrdenProduccionOrigen() == null
                        ? null : orden.getOrdenProduccionOrigen().getOrdenId())
                .liberadaEn(orden.getLiberadaEn())
                .politicaDispensacionInicio(orden.getPoliticaDispensacionInicio().name())
                .estadoDispensacionMateriales(orden.getEstadoDispensacionMateriales().name())
                .operaciones(operacionService.listar(orden.getOrdenFabricacionId()))
                .build();
    }

    private String nombreUsuario(User user) {
        if (user == null) return null;
        return user.getNombreCompleto() != null && !user.getNombreCompleto().isBlank()
                ? user.getNombreCompleto() : user.getUsername();
    }

    private String normalizarObligatorio(String value, String mensaje) {
        String normalizado = normalizar(value);
        if (normalizado == null) throw new IllegalArgumentException(mensaje);
        return normalizado;
    }

    private String normalizar(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
