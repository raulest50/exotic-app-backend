package exotic.app.planta.service.controles;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.controles.dto.ControlDTOs.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.ControlRequeridoRepo;
import exotic.app.planta.repo.controles.DesviacionControlRepo;
import exotic.app.planta.repo.controles.EjecucionControlRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ControlDeviationService {
    private final DesviacionControlRepo desviacionRepo;
    private final ControlRequeridoRepo requeridoRepo;
    private final EjecucionControlRepo ejecucionRepo;

    @Transactional(readOnly = true)
    public Page<DesviacionResponse> listar(
            AmbitoControl ambito, List<EstadoDesviacionControl> estados,
            String search, int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("Paginacion fuera de rango.");
        }
        List<EstadoDesviacionControl> filtro = estados == null || estados.isEmpty()
                ? List.of(EstadoDesviacionControl.values()) : estados;
        String filtroTexto = search == null || search.isBlank() ? null : search.trim();
        return desviacionRepo.buscar(ambito, filtro, filtroTexto,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "abiertaEn")))
                .map(this::toResponse);
    }

    @Transactional
    public DesviacionResponse resolver(
            AmbitoControl ambito, Long id, User actor, DesviacionResolveRequest request) {
        DesviacionControl item = requireForUpdate(ambito, id);
        if (item.getEstado() == EstadoDesviacionControl.CERRADA) {
            throw new IllegalStateException("Una desviacion cerrada es inmutable.");
        }
        item.setInvestigacion(limpiar(request.investigacion()));
        String resolucion = limpiar(request.resolucion());
        item.setResolucion(resolucion);
        item.setDisposicion(request.disposicion());
        item.setEstado(EstadoDesviacionControl.RESUELTA);
        item.setResueltaEn(AppTime.now());
        item.setResueltaPor(actor);
        if (ambito == AmbitoControl.PROCESO) {
            item.setJustificacionDisposicion(resolucion);
            item.setEstado(EstadoDesviacionControl.CERRADA);
            item.setCerradaEn(AppTime.now());
            item.setCerradaPor(actor);
            desviacionRepo.saveAndFlush(item);
            aplicarDisposicion(item);
            return toResponse(item);
        }
        return toResponse(desviacionRepo.saveAndFlush(item));
    }

    @Transactional
    public DesviacionResponse cerrar(
            AmbitoControl ambito, Long id, User actor, DesviacionCloseRequest request) {
        if (ambito != AmbitoControl.CALIDAD) {
            throw new IllegalArgumentException(
                    "Las desviaciones de proceso se cierran al registrar su resolucion.");
        }
        DesviacionControl item = requireForUpdate(ambito, id);
        if (item.getEstado() == EstadoDesviacionControl.CERRADA) {
            return toResponse(item);
        }
        if (item.getEstado() != EstadoDesviacionControl.RESUELTA || item.getResueltaPor() == null) {
            throw new IllegalStateException("La desviacion debe investigarse y resolverse antes de cerrarse.");
        }
        if (ambito == AmbitoControl.CALIDAD
                && item.getResueltaPor().getId().equals(actor.getId())) {
            throw new IllegalStateException("Quien cierra una desviacion de Calidad debe ser distinto de quien la resolvio.");
        }
        item.setDisposicion(request.disposicion());
        item.setJustificacionDisposicion(limpiar(request.justificacionDisposicion()));
        item.setEstado(EstadoDesviacionControl.CERRADA);
        item.setCerradaEn(AppTime.now());
        item.setCerradaPor(actor);
        desviacionRepo.saveAndFlush(item);
        aplicarDisposicion(item);
        return toResponse(item);
    }

    private void aplicarDisposicion(DesviacionControl desviacion) {
        ControlRequerido requerido = requeridoRepo.findByIdForUpdate(
                        desviacion.getControlRequerido().getId())
                .orElseThrow(() -> new NoSuchElementException("Control requerido no encontrado."));
        // Una disposicion RECHAZAR es terminal. Si ya existe, ninguna decision
        // posterior sobre otra desviacion del mismo requisito puede convertirla
        // en aceptacion ni habilitar una repeticion ordinaria.
        if (desviacionRepo.existsByControlRequerido_IdAndEstadoAndDisposicion(
                requerido.getId(), EstadoDesviacionControl.CERRADA,
                DisposicionDesviacionControl.RECHAZAR)) {
            requerido.setEstado(EstadoControlRequerido.NO_CONFORME);
            requerido.setRequiereRepeticion(false);
            requeridoRepo.flush();
            return;
        }
        switch (desviacion.getDisposicion()) {
            case ACEPTAR_JUSTIFICADAMENTE -> {
                if (!desviacionRepo.existsByControlRequerido_IdAndEstadoNot(
                        requerido.getId(), EstadoDesviacionControl.CERRADA)) {
                    requerido.setEstado(EstadoControlRequerido.ACEPTADO_POR_DESVIACION);
                    requerido.setRequiereRepeticion(false);
                }
            }
            case REPETIR -> {
                requerido.setEstado(EstadoControlRequerido.PENDIENTE);
                requerido.setRequiereRepeticion(true);
            }
            case CORREGIR_REPROCESAR -> {
                requerido.setEstado(EstadoControlRequerido.PENDIENTE);
                requerido.setRequiereRepeticion(true);
            }
            case RECHAZAR -> requerido.setEstado(EstadoControlRequerido.NO_CONFORME);
        }
        requeridoRepo.flush();
    }

    private DesviacionControl requireForUpdate(AmbitoControl ambito, Long id) {
        return desviacionRepo.findByIdAndAmbitoForUpdate(id, ambito)
                .orElseThrow(() -> new NoSuchElementException("Desviacion no encontrada en este ambito."));
    }

    private DesviacionResponse toResponse(DesviacionControl item) {
        return new DesviacionResponse(item.getId(), item.getControlRequerido().getId(),
                item.getEjecucionOrigen().getId(), item.getAmbito(), item.getEstado(),
                item.getControlRequerido().getPlanCodigoSnapshot(),
                item.getControlRequerido().getPlanNombreSnapshot(),
                item.getControlRequerido().getLote().getId(),
                item.getControlRequerido().getLote().getBatchNumber(),
                item.getControlRequerido().getProductoIdSnapshot(),
                item.getControlRequerido().getTipoOrdenSnapshot(), item.getDisposicion(),
                item.getInvestigacion(), item.getResolucion(), item.getJustificacionDisposicion(),
                item.getAbiertaEn(),
                item.getAbiertaPor().getUsername(), item.getResueltaEn(),
                item.getResueltaPor() == null ? null : item.getResueltaPor().getUsername(),
                item.getCerradaEn(), item.getCerradaPor() == null ? null : item.getCerradaPor().getUsername());
    }

    private String limpiar(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("La justificacion es obligatoria.");
        }
        return value.trim();
    }
}
