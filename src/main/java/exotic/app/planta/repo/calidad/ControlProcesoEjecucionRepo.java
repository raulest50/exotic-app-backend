package exotic.app.planta.repo.calidad;

import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ControlProcesoEjecucionRepo extends JpaRepository<ControlProcesoEjecucion, Long> {

    @EntityGraph(attributePaths = {
            "plantilla",
            "plantilla.areaOperativa",
            "lote",
            "lote.ordenProduccion",
            "lote.ordenProduccion.producto",
            "usuario"
    })
    @Query("""
            SELECT e
            FROM ControlProcesoEjecucion e
            WHERE (:areaId IS NULL OR e.plantilla.areaOperativa.areaId = :areaId)
              AND (:loteId IS NULL OR e.lote.id = :loteId)
              AND (:producto IS NULL
                   OR LOWER(e.lote.ordenProduccion.producto.productoId) LIKE LOWER(CONCAT('%', :producto, '%'))
                   OR LOWER(e.lote.ordenProduccion.producto.nombre) LIKE LOWER(CONCAT('%', :producto, '%')))
              AND (:fechaDesde IS NULL OR e.fechaRegistro >= :fechaDesde)
              AND (:fechaHasta IS NULL OR e.fechaRegistro <= :fechaHasta)
            """)
    Page<ControlProcesoEjecucion> buscar(
            @Param("areaId") Integer areaId,
            @Param("loteId") Long loteId,
            @Param("producto") String producto,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta,
            Pageable pageable
    );
}
