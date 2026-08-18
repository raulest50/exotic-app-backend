package exotic.app.planta.repo.produccion.fabricacion;

import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacionEvento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.TipoEventoSeguimiento;

public interface OrdenFabricacionOperacionEventoRepo
        extends JpaRepository<OrdenFabricacionOperacionEvento, Long> {
    List<OrdenFabricacionOperacionEvento> findByOperacion_IdOrderByFechaEventoAscIdAsc(Long operacionId);

    boolean existsByOperacion_OrdenFabricacion_OrdenFabricacionIdAndActorTipoAndTipoEvento(
            Long ordenFabricacionId,
            ActorTipoEventoSeguimiento actorTipo,
            TipoEventoSeguimiento tipoEvento);
}
