package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanalLotePlanificado;
import exotic.app.planta.model.produccion.MpsSemanalLotePlanificado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MpsSemanalLotePlanificadoRepo extends JpaRepository<MpsSemanalLotePlanificado, Long> {

    long countByMpsItem_MpsSemanal_MpsId(Integer mpsId);

    long countByMpsItem_MpsSemanal_MpsIdAndEstadoNot(Integer mpsId, EstadoMpsSemanalLotePlanificado estado);

    long countByMpsItem_MpsSemanal_MpsIdAndEstado(Integer mpsId, EstadoMpsSemanalLotePlanificado estado);

    @EntityGraph(attributePaths = {"mpsItem", "mpsItem.mpsDia", "mpsItem.terminado"})
    @Query("""
            SELECT lote
            FROM MpsSemanalLotePlanificado lote
            WHERE lote.mpsItem.mpsSemanal.mpsId = :mpsId
              AND lote.estado = :estado
            ORDER BY lote.mpsItem.mpsDia.dayIndex ASC, lote.mpsItem.displayOrder ASC, lote.loteOrdinal ASC
            """)
    List<MpsSemanalLotePlanificado> findPendingByMpsIdOrdered(
            @Param("mpsId") Integer mpsId,
            @Param("estado") EstadoMpsSemanalLotePlanificado estado
    );
}
