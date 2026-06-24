package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MpsSemanalOrdenInicioPolicyService {

    private final SeguimientoOrdenAreaEventoRepo seguimientoEventoRepo;

    @Transactional(readOnly = true)
    public boolean isOrdenIniciada(OrdenProduccion orden) {
        if (orden == null || orden.getEstadoOrden() == -1) {
            return false;
        }
        if (orden.getEstadoOrden() != 0 || orden.getFechaInicio() != null) {
            return true;
        }
        return seguimientoEventoRepo.existsUserStartOrCompletionEventByOrdenId(
                orden.getOrdenId(),
                ActorTipoEventoSeguimiento.USER,
                List.of(
                        EstadoSeguimientoOrdenArea.EN_PROCESO.getCode(),
                        EstadoSeguimientoOrdenArea.COMPLETADO.getCode()
                )
        );
    }

    @Transactional(readOnly = true)
    public boolean isOrdenCancelable(OrdenProduccion orden) {
        return orden != null && orden.getEstadoOrden() == 0 && !isOrdenIniciada(orden);
    }
}
