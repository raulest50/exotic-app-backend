package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.EjecucionControl;
import exotic.app.planta.model.controles.ResultadoEjecucionControl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EjecucionControlRepo extends JpaRepository<EjecucionControl, Long> {
    @Query("""
            select e from EjecucionControl e
            where e.controlRequerido.ambitoSnapshot = :ambito
              and (:loteId is null or e.controlRequerido.lote.id = :loteId)
              and (:batchRecordId is null or e.controlRequerido.batchRecord.id = :batchRecordId)
              and (:search is null or :search = ''
                   or lower(e.controlRequerido.planCodigoSnapshot) like lower(concat('%', :search, '%'))
                   or lower(e.controlRequerido.planNombreSnapshot) like lower(concat('%', :search, '%'))
                   or lower(e.controlRequerido.lote.batchNumber) like lower(concat('%', :search, '%'))
                   or lower(e.controlRequerido.productoIdSnapshot) like lower(concat('%', :search, '%')))
              and (:desde is null or e.fechaRegistro >= :desde)
              and (:hasta is null or e.fechaRegistro < :hasta)
              and (:resultado is null or e.resultado = :resultado)
            """)
    Page<EjecucionControl> buscar(
            @Param("ambito") AmbitoControl ambito,
            @Param("loteId") Long loteId,
            @Param("batchRecordId") Long batchRecordId,
            @Param("search") String search,
            @Param("resultado") ResultadoEjecucionControl resultado,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable);
    Optional<EjecucionControl> findByIdAndControlRequerido_AmbitoSnapshot(Long id, AmbitoControl ambito);
    Optional<EjecucionControl> findByLegacyEjecucion_Id(Long legacyEjecucionId);
    List<EjecucionControl> findByControlRequerido_IdOrderByFechaRegistroDescIdDesc(Long controlRequeridoId);
    List<EjecucionControl> findByControlRequerido_BatchRecord_IdAndControlRequerido_AmbitoSnapshotOrderByFechaRegistroAscIdAsc(
            Long batchRecordId, AmbitoControl ambito);
}
