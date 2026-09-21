package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.time.LocalDateTime;

public interface VersionPlanControlRepo extends JpaRepository<VersionPlanControl, Long> {
    interface ReferenciaVersion {
        Long getPlanId();
        Long getId();
        Integer getNumero();
        EstadoVersionPlanControl getEstado();
    }

    interface ResumenVersion extends ReferenciaVersion {
        LocalDateTime getCreadaEn();
        LocalDateTime getPublicadaEn();
        LocalDateTime getRetiradaEn();
        Integer getCantidadAplicabilidades();
        Integer getCantidadCaracteristicas();
    }

    @Query("""
            select v.plan.id as planId, v.id as id, v.numero as numero, v.estado as estado,
                   v.creadaEn as creadaEn, v.publicadaEn as publicadaEn, v.retiradaEn as retiradaEn,
                   size(v.aplicabilidades) as cantidadAplicabilidades,
                   size(v.caracteristicas) as cantidadCaracteristicas
            from VersionPlanControl v
            where v.plan.id in :planIds and v.estado in :estados
            order by v.plan.id asc, v.numero desc
            """)
    List<ResumenVersion> findResumenes(@Param("planIds") Collection<Long> planIds,
                                      @Param("estados") Collection<EstadoVersionPlanControl> estados);

    // At most three references per plan, independent of the visible state filter.
    @Query("""
            select v.plan.id as planId, v.id as id, v.numero as numero, v.estado as estado
            from VersionPlanControl v
            where v.plan.id in :planIds and (v.estado in :activos or
                (v.estado = :retirada and v.numero = (
                    select max(r.numero) from VersionPlanControl r
                    where r.plan = v.plan and r.estado = :retirada)))
            """)
    List<ReferenciaVersion> findReferencias(@Param("planIds") Collection<Long> planIds,
                                          @Param("activos") Collection<EstadoVersionPlanControl> activos,
                                          @Param("retirada") EstadoVersionPlanControl retirada);

    Optional<VersionPlanControl> findByIdAndPlan_Ambito(Long id, AmbitoControl ambito);
    Optional<VersionPlanControl> findByLegacyPlantilla_Id(Long legacyPlantillaId);
    boolean existsByPlan_IdAndLegacyPlantillaIsNull(Long planId);
    Optional<VersionPlanControl> findFirstByPlan_IdAndEstado(Long planId, EstadoVersionPlanControl estado);
    @Query("select coalesce(max(v.numero), 0) from VersionPlanControl v where v.plan.id = :planId")
    int maxNumero(@Param("planId") Long planId);
    List<VersionPlanControl> findByEstado(EstadoVersionPlanControl estado);
    List<VersionPlanControl> findByEstadoIn(Collection<EstadoVersionPlanControl> estados);
}
