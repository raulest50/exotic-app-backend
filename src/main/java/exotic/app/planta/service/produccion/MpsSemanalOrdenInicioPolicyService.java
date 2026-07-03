package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.EstadoDispensacionMateriales;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.TipoEventoSeguimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MpsSemanalOrdenInicioPolicyService {

    private static final List<TransaccionAlmacen.TipoEntidadCausante> TRANSACCIONES_REALES_BLOQUEANTES = List.of(
            TransaccionAlmacen.TipoEntidadCausante.OD,
            TransaccionAlmacen.TipoEntidadCausante.OD_RA,
            TransaccionAlmacen.TipoEntidadCausante.RA,
            TransaccionAlmacen.TipoEntidadCausante.OP
    );

    private final SeguimientoOrdenAreaEventoRepo seguimientoEventoRepo;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;

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
                TipoEventoSeguimiento.OPERATIVO,
                List.of(
                        EstadoSeguimientoOrdenArea.EN_PROCESO.getCode(),
                        EstadoSeguimientoOrdenArea.COMPLETADO.getCode()
                )
        );
    }

    @Transactional(readOnly = true)
    public boolean hasEjecucionRealBloqueante(OrdenProduccion orden) {
        if (orden == null || orden.getEstadoOrden() == -1) {
            return false;
        }
        return isOrdenIniciada(orden)
                || hasEstadoDispensacionReal(orden)
                || hasTransaccionRealBloqueante(orden.getOrdenId());
    }

    @Transactional(readOnly = true)
    public boolean isOrdenCancelable(OrdenProduccion orden) {
        return orden != null
                && orden.getEstadoOrden() == 0
                && !isOrdenIniciada(orden)
                && !hasEstadoDispensacionReal(orden)
                && !hasTransaccionRealBloqueante(orden.getOrdenId());
    }

    private boolean hasEstadoDispensacionReal(OrdenProduccion orden) {
        return orden.getEstadoDispensacionMateriales() == EstadoDispensacionMateriales.PARCIAL
                || orden.getEstadoDispensacionMateriales() == EstadoDispensacionMateriales.COMPLETA;
    }

    private boolean hasTransaccionRealBloqueante(int ordenId) {
        return transaccionAlmacenHeaderRepo.existsByTipoEntidadCausanteInAndIdEntidadCausante(
                TRANSACCIONES_REALES_BLOQUEANTES,
                ordenId
        );
    }
}
