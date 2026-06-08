package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.MpsSemanalItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MpsSemanalItemRepo extends JpaRepository<MpsSemanalItem, Long> {

    long countByMpsSemanal_MpsId(Integer mpsId);

    @EntityGraph(attributePaths = {"mpsDia", "terminado", "lotesPlanificados"})
    @Query("""
            SELECT item
            FROM MpsSemanalItem item
            WHERE item.mpsSemanal.mpsId = :mpsId
            ORDER BY item.mpsDia.dayIndex ASC, item.displayOrder ASC, item.id ASC
            """)
    List<MpsSemanalItem> findAllByMpsIdOrdered(@Param("mpsId") Integer mpsId);
}
