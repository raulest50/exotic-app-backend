package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.*;
import exotic.app.planta.model.produccion.dto.MpsFabricacionDTOs.*;
import exotic.app.planta.model.produccion.fabricacion.*;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.produccion.fabricacion.*;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MpsFabricacionAprobadoEditService {
    private final MpsFabricacionService programaService;
    private final MpsFabricacionDetalleRepo detalleRepo;
    private final SemiTerminadoRepo semiRepo;
    private final OrdenFabricacionRepo ordenRepo;
    private final OrdenFabricacionService ordenService;
    private final MpsFabricacionEmisionService emisionService;
    private final MasterDirectiveService masterDirectiveService;
    @PersistenceContext
    private EntityManager entityManager;

    public ProgramaResponse agregar(LocalDate week, LineaRequest request, User actor) {
        MpsFabricacionSemanal programa = requirePrograma(week, request.getVersion());
        validarFechas(week, request);
        SemiTerminado semi = semiRepo.findById(request.getSemiTerminadoId().trim())
                .orElseThrow(() -> new IllegalArgumentException("Semiterminado no encontrado."));
        if (!semi.isRequiereOrdenFabricacion()) throw new IllegalArgumentException("El semiterminado no esta marcado para OF.");
        boolean yaProgramado = programa.getDetalles().stream()
                .anyMatch(d -> Objects.equals(d.getSemiTerminado().getProductoId(), semi.getProductoId()));
        if (!yaProgramado && !masterDirectiveService.isMpsFabricacionPermitirAgregarSemiterminadosAprobado()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La directiva para agregar semiterminados nuevos en MPS OF aprobada esta deshabilitada.");
        }
        if (semi.getTipoUnidades() == null || semi.getTipoUnidades().isBlank()) {
            throw new IllegalArgumentException("El semiterminado no tiene unidad de medida.");
        }
        MpsFabricacionDetalle detalle = new MpsFabricacionDetalle();
        detalle.setMps(programa); detalle.setSemiTerminado(semi); detalle.setUnidadMedida(semi.getTipoUnidades().trim());
        detalle.setPosicion(programa.getDetalles().stream().mapToInt(MpsFabricacionDetalle::getPosicion).max().orElse(-1) + 1);
        aplicar(detalle, request);
        programa.getDetalles().add(detalle);
        detalleRepo.saveAndFlush(detalle);
        if (programa.getEstado() == EstadoMpsSemanal.CERRADO) emisionService.emitirTanda(detalle, actor);
        return terminar(programa, actor);
    }

    public ProgramaResponse editar(LocalDate week, Long id, LineaRequest request, User actor) {
        MpsFabricacionSemanal programa = requirePrograma(week, request.getVersion());
        MpsFabricacionDetalle detalle = requireDetalle(programa, id);
        validarFechas(week, request);
        if (!Objects.equals(detalle.getSemiTerminado().getProductoId(), request.getSemiTerminadoId().trim())) {
            throw new IllegalArgumentException("Para cambiar el producto, cancele la tanda y agregue una nueva.");
        }
        OrdenFabricacion orden = lockOrden(detalle);
        if (orden != null) {
            if (detalle.getCantidad().compareTo(request.getCantidad()) != 0) {
                throw new IllegalArgumentException("La cantidad de una tanda emitida esta congelada; cancelela y agregue su reemplazo.");
            }
            orden.setFechaLanzamiento(request.getFechaInicio());
            orden.setFechaFinalPlanificada(request.getFechaFinal());
            orden.setObservaciones(normalizar(request.getObservaciones()));
            ordenService.reprogramarDesdeMps(orden);
        }
        aplicar(detalle, request);
        return terminar(programa, actor);
    }

    public ProgramaResponse cancelar(LocalDate week, Long id, Long version, User actor) {
        MpsFabricacionSemanal programa = requirePrograma(week, version);
        MpsFabricacionDetalle detalle = requireDetalle(programa, id);
        OrdenFabricacion orden = lockOrden(detalle);
        if (orden != null) ordenService.cancelarDesdeMps(orden.getOrdenFabricacionId(), actor);
        detalle.setEstado(EstadoMpsSemanalItem.CANCELADO);
        return terminar(programa, actor);
    }

    private MpsFabricacionSemanal requirePrograma(LocalDate week, Long version) {
        MpsFabricacionSemanal programa = programaService.bloquear(week, version);
        if (programa.getEstado() != EstadoMpsSemanal.APROBADO && programa.getEstado() != EstadoMpsSemanal.CERRADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La edicion por tanda aplica a MPS OF aprobado o emitido.");
        }
        return programa;
    }

    private MpsFabricacionDetalle requireDetalle(MpsFabricacionSemanal programa, Long id) {
        MpsFabricacionDetalle detalle = detalleRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tanda no encontrada."));
        if (!Objects.equals(detalle.getMps().getId(), programa.getId())) {
            throw new IllegalArgumentException("La tanda no pertenece a este MPS OF.");
        }
        if (detalle.getEstado() == EstadoMpsSemanalItem.CANCELADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La tanda ya esta cancelada.");
        }
        return detalle;
    }

    private OrdenFabricacion lockOrden(MpsFabricacionDetalle detalle) {
        if (detalle.getOrdenFabricacion() == null) return null;
        OrdenFabricacion orden = ordenRepo.findByIdForUpdate(detalle.getOrdenFabricacion().getOrdenFabricacionId())
                .orElseThrow();
        // La relacion inversa puede haberse inicializado al consultar la tanda.
        entityManager.flush();
        entityManager.refresh(orden, LockModeType.PESSIMISTIC_WRITE);
        if (!ordenService.esCancelable(orden)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La OF tiene ejecucion o dispensaciones reales.");
        }
        return orden;
    }

    private void validarFechas(LocalDate week, LineaRequest request) {
        if (request.getFechaInicio() == null || request.getFechaFinal() == null
                || request.getFechaInicio().toLocalDate().isBefore(week)
                || !request.getFechaInicio().toLocalDate().isBefore(week.plusWeeks(1))
                || request.getFechaFinal().isBefore(request.getFechaInicio())) {
            throw new IllegalArgumentException("Las fechas de la tanda no son validas para la semana.");
        }
        if (request.getCantidad() == null || request.getCantidad().signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser positiva.");
        }
    }

    private void aplicar(MpsFabricacionDetalle detalle, LineaRequest request) {
        detalle.setCantidad(request.getCantidad()); detalle.setFechaInicio(request.getFechaInicio());
        detalle.setFechaFinal(request.getFechaFinal()); detalle.setObservaciones(normalizar(request.getObservaciones()));
    }

    private ProgramaResponse terminar(MpsFabricacionSemanal programa, User actor) {
        programaService.actualizar(programa, actor, true);
        return programaService.toResponse(programa.getWeekStartDate(), programa, null);
    }

    private String normalizar(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
