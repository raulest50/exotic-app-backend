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

public interface ControlRequeridoRepo extends JpaRepository<ControlRequerido, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ControlRequerido r where r.id = :id")
    Optional<ControlRequerido> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select r from ControlRequerido r
            where r.ambitoSnapshot = :ambito
              and r.estado in :estados
              and (:loteId is null or r.lote.id = :loteId)
              and (:batchRecordId is null or r.batchRecord.id = :batchRecordId)
              and (:batchRecordEtapaId is null or r.batchRecordEtapa.id = :batchRecordEtapaId)
              and (:areaId is null or r.areaOperativaIdSnapshot = :areaId)
              and (:tipoOrden is null or r.tipoOrdenSnapshot = :tipoOrden)
              and (:momento is null or r.momentoSnapshot = :momento)
              and (:vencimientoDesde is null or r.lote.expirationDate >= :vencimientoDesde)
              and (:vencimientoHasta is null or r.lote.expirationDate <= :vencimientoHasta)
              and (r.batchRecord is null
                   or (r.ambitoSnapshot = exotic.app.planta.model.controles.AmbitoControl.PROCESO
                       and (r.batchRecord.estado = exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord.EN_EJECUCION
                            or (r.batchRecord.estado in (
                                exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord.DEVUELTO_PRODUCCION,
                                exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord.EN_CORRECCION)
                                and r.requiereRepeticion = true
                                and r.cicloRevisionNumero = r.batchRecord.cicloRevisionActual)))
                   or (r.ambitoSnapshot = exotic.app.planta.model.controles.AmbitoControl.CALIDAD
                       and ((r.momentoSnapshot = exotic.app.planta.model.controles.MomentoControl.REVISION_FINAL
                             and r.batchRecord.estado = exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord.PENDIENTE_REVISION)
                            or (r.momentoSnapshot = exotic.app.planta.model.controles.MomentoControl.DURANTE_FABRICACION
                                and (r.batchRecord.estado = exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord.EN_EJECUCION
                                     or r.batchRecord.estado = exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord.EN_CORRECCION
                                     or (r.batchRecord.estado = exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord.PENDIENTE_REVISION
                                         and (r.requiereRepeticion = true
                                              or r.requiereRevalidacion = true
                                              or r.estado = exotic.app.planta.model.controles.EstadoControlRequerido.POR_REVALIDAR)))))))
              and (:search is null or :search = ''
                   or lower(r.planCodigoSnapshot) like lower(concat('%', :search, '%'))
                   or lower(r.planNombreSnapshot) like lower(concat('%', :search, '%'))
                   or lower(r.lote.batchNumber) like lower(concat('%', :search, '%'))
                   or lower(r.productoIdSnapshot) like lower(concat('%', :search, '%'))
                   or lower(r.productoNombreSnapshot) like lower(concat('%', :search, '%'))
                   or lower(r.areaOperativaNombreSnapshot) like lower(concat('%', :search, '%'))
                   or lower(r.procesoNombreSnapshot) like lower(concat('%', :search, '%'))
                   or lower(r.batchRecordEtapa.nombre) like lower(concat('%', :search, '%')))
            """)
    Page<ControlRequerido> buscarPendientes(
            @Param("ambito") AmbitoControl ambito,
            @Param("estados") Collection<EstadoControlRequerido> estados,
            @Param("loteId") Long loteId,
            @Param("batchRecordId") Long batchRecordId,
            @Param("batchRecordEtapaId") Long batchRecordEtapaId,
            @Param("areaId") Integer areaId,
            @Param("tipoOrden") TipoOrdenControl tipoOrden,
            @Param("momento") MomentoControl momento,
            @Param("vencimientoDesde") java.time.LocalDate vencimientoDesde,
            @Param("vencimientoHasta") java.time.LocalDate vencimientoHasta,
            @Param("search") String search,
            Pageable pageable);

    List<ControlRequerido> findByBatchRecord_IdOrderByIdAsc(Long batchRecordId);
    Optional<ControlRequerido> findByBatchRecordEtapa_IdAndVersionPlan_Id(
            Long batchRecordEtapaId, Long versionPlanId);
    Optional<ControlRequerido> findByLegacyEjecucion_Id(Long legacyEjecucionId);
    boolean existsByVersionPlan_Id(Long versionPlanId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ControlRequerido r where r.batchRecord.id = :batchRecordId order by r.id")
    List<ControlRequerido> findByBatchRecordIdForUpdate(@Param("batchRecordId") Long batchRecordId);
    List<ControlRequerido> findByBatchRecord_IdAndPuntoExigenciaSnapshot(
            Long batchRecordId, PuntoExigenciaControl punto);
    List<ControlRequerido> findByBatchRecordEtapa_IdAndPuntoExigenciaSnapshot(
            Long etapaId, PuntoExigenciaControl punto);
    boolean existsByBatchRecord_IdAndVersionPlan_IdAndBatchRecordEtapa_Id(
            Long batchRecordId, Long versionId, Long etapaId);
    boolean existsByBatchRecord_IdAndVersionPlan_IdAndBatchRecordEtapaIsNull(
            Long batchRecordId, Long versionId);
    boolean existsByLote_IdAndVersionPlan_IdAndPuntoAplicacionSnapshotAndAreaOperativaIdSnapshotAndProcesoIdSnapshotAndRutaNodoIdSnapshotAndOrdenFabricacionOperacionIdSnapshotAndOrigen(
            Long loteId, Long versionId, PuntoAplicacionControl puntoAplicacion,
            Integer areaId, Integer procesoId, Long rutaNodoId, Long operacionFabricacionId,
            OrigenControlRequerido origen);
    List<ControlRequerido> findByLote_IdAndOrigenAndAmbitoSnapshotOrderByIdAsc(
            Long loteId, OrigenControlRequerido origen, AmbitoControl ambito);
}
