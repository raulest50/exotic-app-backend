package exotic.app.planta.repo.calidad;

import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ControlProcesoEjecucionRepo extends JpaRepository<ControlProcesoEjecucion, Long>,
        JpaSpecificationExecutor<ControlProcesoEjecucion> {

    @EntityGraph(attributePaths = {
            "plantilla", "plantilla.areaOperativa", "batchRecordEtapa", "usuario"
    })
    List<ControlProcesoEjecucion> findByBatchRecord_IdOrderByFechaRegistroAscIdAsc(Long batchRecordId);

    @EntityGraph(attributePaths = {
            "plantilla", "plantilla.areaOperativa", "batchRecordEtapa", "usuario"
    })
    Optional<ControlProcesoEjecucion> findTopByBatchRecordEtapa_IdOrderByFechaRegistroDescIdDesc(
            Long batchRecordEtapaId);

    @EntityGraph(attributePaths = {
            "plantilla",
            "plantilla.areaOperativa",
            "lote",
            "lote.ordenProduccion",
            "lote.ordenProduccion.producto",
            "usuario"
    })
    @Override
    Page<ControlProcesoEjecucion> findAll(
            Specification<ControlProcesoEjecucion> specification,
            Pageable pageable);
}
