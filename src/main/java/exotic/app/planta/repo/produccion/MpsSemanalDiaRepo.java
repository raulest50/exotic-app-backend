package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.MpsSemanalDia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    @EntityGraph(attributePaths = {
            "items",
            "items.terminado",
            "items.terminado.categoria"
    })
    @Query("""
            SELECT DISTINCT dia
            FROM MpsSemanalDia dia
            WHERE dia.mpsSemanal.mpsId = :mpsId
              AND dia.fecha = :fecha
            """)
    Optional<MpsSemanalDia> findByMpsIdAndFecha(
            @Param("mpsId") Integer mpsId,
            @Param("fecha") LocalDate fecha);

    @EntityGraph(attributePaths = {
            "items",
            "items.terminado",
            "items.terminado.categoria"
    })
    @Query("""
            SELECT DISTINCT dia
            FROM MpsSemanalDia dia
            WHERE dia.mpsSemanal.mpsId IN :mpsIds
              AND dia.fecha >= :fechaDesde
              AND dia.fecha <= :fechaHasta
            ORDER BY dia.fecha ASC, dia.dayIndex ASC
            """)
    List<MpsSemanalDia> findAllByMpsIdsAndDateRange(
            @Param("mpsIds") List<Integer> mpsIds,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta);
}
