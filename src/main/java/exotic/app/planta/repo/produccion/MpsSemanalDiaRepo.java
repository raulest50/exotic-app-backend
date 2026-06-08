package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.MpsSemanalDia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MpsSemanalDiaRepo extends JpaRepository<MpsSemanalDia, Long> {

    @EntityGraph(attributePaths = {
            "items",
            "items.terminado"
    })
    @Query("""
            SELECT DISTINCT dia
            FROM MpsSemanalDia dia
            WHERE dia.mpsSemanal.mpsId = :mpsId
            ORDER BY dia.dayIndex ASC
            """)
    List<MpsSemanalDia> findAllByMpsSemanal_MpsIdOrderByDayIndexAsc(@Param("mpsId") Integer mpsId);
}
