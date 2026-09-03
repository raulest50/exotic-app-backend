package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DesviacionControlRepo extends JpaRepository<DesviacionControl, Long> {
    @Query("""
            select d from DesviacionControl d
            where d.ambito = :ambito
              and (:estados is null or d.estado in :estados)
              and (:search is null or :search = ''
                   or lower(d.controlRequerido.planCodigoSnapshot) like lower(concat('%', :search, '%'))
                   or lower(d.controlRequerido.planNombreSnapshot) like lower(concat('%', :search, '%'))
                   or lower(d.controlRequerido.lote.batchNumber) like lower(concat('%', :search, '%'))
                   or lower(d.controlRequerido.productoIdSnapshot) like lower(concat('%', :search, '%'))
                   or lower(d.controlRequerido.productoNombreSnapshot) like lower(concat('%', :search, '%')))
            """)
    Page<DesviacionControl> buscar(
            @Param("ambito") AmbitoControl ambito,
            @Param("estados") Collection<EstadoDesviacionControl> estados,
            @Param("search") String search,
            Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DesviacionControl d where d.id = :id and d.ambito = :ambito")
    Optional<DesviacionControl> findByIdAndAmbitoForUpdate(@Param("id") Long id, @Param("ambito") AmbitoControl ambito);
    boolean existsByControlRequerido_BatchRecord_IdAndEstadoNot(Long batchRecordId, EstadoDesviacionControl estado);
    long countByControlRequerido_BatchRecord_IdAndEstadoNot(Long batchRecordId, EstadoDesviacionControl estado);
    boolean existsByControlRequerido_IdAndEstadoNot(Long controlRequeridoId, EstadoDesviacionControl estado);
    Optional<DesviacionControl> findByEjecucionOrigen_Id(Long ejecucionId);
    boolean existsByControlRequerido_IdAndEstadoAndDisposicion(
            Long controlRequeridoId, EstadoDesviacionControl estado,
            DisposicionDesviacionControl disposicion);
    List<DesviacionControl> findByControlRequerido_IdOrderByIdAsc(Long controlRequeridoId);
}
