package exotic.app.planta.repo.calidad;

import exotic.app.planta.model.calidad.ControlProcesoPlantilla;
import exotic.app.planta.model.calidad.EstadoControlProcesoPlantilla;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ControlProcesoPlantillaRepo extends JpaRepository<ControlProcesoPlantilla, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ControlProcesoPlantilla p where p.id = :id")
    Optional<ControlProcesoPlantilla> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from ControlProcesoPlantilla p
            where p.areaOperativa.areaId = :areaId
              and p.estado = :estado
            """)
    Optional<ControlProcesoPlantilla> findByAreaIdAndEstadoForUpdate(
            @Param("areaId") Integer areaId,
            @Param("estado") EstadoControlProcesoPlantilla estado);

    @EntityGraph(attributePaths = {"areaOperativa", "caracteristicas"})
    Optional<ControlProcesoPlantilla> findFirstByAreaOperativa_AreaIdAndEstado(
            Integer areaId,
            EstadoControlProcesoPlantilla estado
    );

    @EntityGraph(attributePaths = {"areaOperativa", "caracteristicas"})
    @Query("""
            SELECT DISTINCT p
            FROM ControlProcesoPlantilla p
            WHERE (:areaId IS NULL OR p.areaOperativa.areaId = :areaId)
              AND (:estado IS NULL OR p.estado = :estado)
            ORDER BY p.areaOperativa.nombre ASC, p.version DESC
            """)
    List<ControlProcesoPlantilla> buscar(
            @Param("areaId") Integer areaId,
            @Param("estado") EstadoControlProcesoPlantilla estado
    );

    @Query("""
            SELECT COALESCE(MAX(p.version), 0)
            FROM ControlProcesoPlantilla p
            WHERE p.areaOperativa.areaId = :areaId
            """)
    Integer maxVersionByAreaId(@Param("areaId") Integer areaId);
}
