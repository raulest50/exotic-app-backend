package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.MpsSemanalItem;
import exotic.app.planta.model.produccion.EstadoMpsSemanalItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MpsSemanalItemRepo extends JpaRepository<MpsSemanalItem, Long> {

    long countByMpsSemanal_MpsId(Integer mpsId);

    long countByMpsSemanal_MpsIdAndEstadoNot(Integer mpsId, EstadoMpsSemanalItem estado);

    @EntityGraph(attributePaths = {"mpsDia", "terminado", "lotesPlanificados"})
    @Query("""
            SELECT item
            FROM MpsSemanalItem item
            WHERE item.mpsSemanal.mpsId = :mpsId
            ORDER BY item.mpsDia.dayIndex ASC, item.displayOrder ASC, item.id ASC
            """)
    List<MpsSemanalItem> findAllByMpsIdOrdered(@Param("mpsId") Integer mpsId);

    @EntityGraph(attributePaths = {
            "mpsSemanal",
            "mpsDia",
            "terminado",
            "terminado.categoria",
            "lotesPlanificados",
            "lotesPlanificados.ordenProduccion"
    })
    @Query("""
            SELECT item
            FROM MpsSemanalItem item
            WHERE item.id = :itemId
            """)
    Optional<MpsSemanalItem> findByIdForAprobadoEdit(@Param("itemId") Long itemId);
}
