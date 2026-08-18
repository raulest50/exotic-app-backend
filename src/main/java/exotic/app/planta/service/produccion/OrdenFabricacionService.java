package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
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
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class OrdenFabricacionService {

    private final OrdenFabricacionRepo ordenRepo;
    private final SemiTerminadoRepo semiTerminadoRepo;
    private final ManufacturingVersionRepo manufacturingVersionRepo;
    private final LoteRepo loteRepo;
    private final UserRepository userRepository;
    private final BatchRecordRepo batchRecordRepo;
    private final VencimientoLoteService vencimientoLoteService;
    private final BatchRecordService batchRecordService;
    private final MasterDirectiveService masterDirectiveService;

    public OrdenFabricacionDTOs.Response crear(
            OrdenFabricacionDTOs.CreateRequest request,
            User actor
    ) {
        if (request == null || actor == null) {
            throw new IllegalArgumentException("La solicitud y el usuario autenticado son obligatorios.");
        }
        if (!masterDirectiveService.lockBatchRecordWorkflowForNewOrder()) {
            throw new IllegalStateException(
                    "La creación de órdenes de fabricación está deshabilitada "
                            + "mientras el flujo de Batch Record permanezca apagado.");
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

        OrdenFabricacion orden = new OrdenFabricacion();
        orden.setSemiTerminado(semi);
        orden.setManufacturingVersion(version);
        orden.setEstado(EstadoOrdenFabricacion.PLANIFICADA);
        orden.setCantidadPlanificada(request.getCantidadPlanificada());
        orden.setUnidadMedida(normalizarObligatorio(
                semi.getTipoUnidades(), "El semiterminado no tiene unidad de medida."));
        orden.setFechaLanzamiento(request.getFechaLanzamiento());
        orden.setFechaFinalPlanificada(request.getFechaFinalPlanificada());
        orden.setCreadaPor(actor);
        orden.setResponsable(responsable);
        orden.setObservaciones(normalizar(request.getObservaciones()));
        ordenRepo.saveAndFlush(orden);

        Lote lote = new Lote();
        lote.setBatchNumber(loteNumero);
        lote.setProducto(semi);
        lote.setOrdenFabricacion(orden);
        lote.setEstadoCalidad(EstadoCalidadLote.SIN_CLASIFICAR);
        vencimientoLoteService.copiarPoliticaVigente(semi, lote);
        loteRepo.saveAndFlush(lote);

        BatchRecord record = batchRecordService.crearParaOrdenFabricacion(orden, lote, actor);
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
        OrdenFabricacion orden = ordenRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Orden de fabricación no encontrada."));
        return toResponse(orden);
    }

    public OrdenFabricacionDTOs.Response cancelar(Long id, User actor) {
        OrdenFabricacion orden = ordenRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Orden de fabricación no encontrada."));
        if (orden.getEstado() != EstadoOrdenFabricacion.BORRADOR
                && orden.getEstado() != EstadoOrdenFabricacion.PLANIFICADA) {
            throw new IllegalStateException(
                    "Solo una orden en borrador o planificada puede cancelarse.");
        }
        orden.setEstado(EstadoOrdenFabricacion.CANCELADA);
        ordenRepo.save(orden);
        batchRecordService.anularPorCancelacion(orden, actor);
        return toResponse(orden);
    }

    private OrdenFabricacionDTOs.Response toResponse(OrdenFabricacion orden) {
        Lote lote = loteRepo.findByOrdenFabricacion_OrdenFabricacionId(
                        orden.getOrdenFabricacionId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "La orden de fabricación no tiene lote asociado."));
        BatchRecord record = batchRecordRepo
                .findByOrdenFabricacion_OrdenFabricacionId(orden.getOrdenFabricacionId())
                .orElseThrow(() -> new IllegalStateException(
                        "La orden de fabricación no tiene expediente asociado."));
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
                .batchRecordId(record.getId())
                .batchRecordCodigo(record.getCodigo())
                .fechaCreacion(orden.getFechaCreacion())
                .fechaLanzamiento(orden.getFechaLanzamiento())
                .fechaFinalPlanificada(orden.getFechaFinalPlanificada())
                .creadaPor(nombreUsuario(orden.getCreadaPor()))
                .responsable(nombreUsuario(orden.getResponsable()))
                .observaciones(orden.getObservaciones())
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
