package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.EjecucionControl;
import exotic.app.planta.model.controles.ResultadoEjecucionControl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EjecucionControlRepo extends JpaRepository<EjecucionControl, Long>,
        JpaSpecificationExecutor<EjecucionControl> {

    default Page<EjecucionControl> buscar(
            AmbitoControl ambito,
            Long loteId,
            Long batchRecordId,
            String search,
            ResultadoEjecucionControl resultado,
            LocalDateTime desde,
            LocalDateTime hasta,
            Pageable pageable) {
        return findAll(EjecucionControlSpecifications.conFiltros(
                ambito, loteId, batchRecordId, search, resultado, desde, hasta), pageable);
    }

    Optional<EjecucionControl> findByIdAndControlRequerido_AmbitoSnapshot(Long id, AmbitoControl ambito);
    Optional<EjecucionControl> findByLegacyEjecucion_Id(Long legacyEjecucionId);
    List<EjecucionControl> findByControlRequerido_IdOrderByFechaRegistroDescIdDesc(Long controlRequeridoId);
    List<EjecucionControl> findByControlRequerido_BatchRecord_IdAndControlRequerido_AmbitoSnapshotOrderByFechaRegistroAscIdAsc(
            Long batchRecordId, AmbitoControl ambito);
}
