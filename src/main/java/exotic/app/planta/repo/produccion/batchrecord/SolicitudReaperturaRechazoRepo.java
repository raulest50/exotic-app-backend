package exotic.app.planta.repo.produccion.batchrecord;

import exotic.app.planta.model.produccion.batchrecord.EstadoSolicitudReaperturaRechazo;
import exotic.app.planta.model.produccion.batchrecord.SolicitudReaperturaRechazo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SolicitudReaperturaRechazoRepo
        extends JpaRepository<SolicitudReaperturaRechazo, Long> {

    @EntityGraph(attributePaths = {
            "solicitadaPor", "aprobadaPor", "revisionSolicitud", "revisionAprobacion",
            "firmaSolicitud", "firmaAprobacion"
    })
    List<SolicitudReaperturaRechazo> findByBatchRecord_IdOrderBySolicitadaEnAscIdAsc(
            Long batchRecordId);

    boolean existsByBatchRecord_IdAndEstado(
            Long batchRecordId,
            EstadoSolicitudReaperturaRechazo estado
    );

    boolean existsByBatchRecord_IdAndCicloRevisionNumeroAndEstado(
            Long batchRecordId,
            long cicloRevisionNumero,
            EstadoSolicitudReaperturaRechazo estado
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SolicitudReaperturaRechazo s WHERE s.id = :id")
    Optional<SolicitudReaperturaRechazo> findByIdForUpdate(@Param("id") Long id);
}
