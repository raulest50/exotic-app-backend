package exotic.app.planta.repo.produccion.fabricacion;

import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrdenFabricacionRepo extends JpaRepository<OrdenFabricacion, Long> {

    @EntityGraph(attributePaths = {"semiTerminado"})
    @Query("""
            select o from OrdenFabricacion o
            where coalesce(o.fechaLanzamiento, o.fechaCreacion) >= :desde
              and coalesce(o.fechaLanzamiento, o.fechaCreacion) < :hasta
              and (:areaId is null or exists (
                  select op.id from OrdenFabricacionOperacion op
                  where op.ordenFabricacion = o and op.areaOperativa.areaId = :areaId))
            order by coalesce(o.fechaLanzamiento, o.fechaCreacion), o.ordenFabricacionId
            """)
    Page<OrdenFabricacion> findSemanaMps(@Param("desde") LocalDateTime desde,
                                       @Param("hasta") LocalDateTime hasta,
                                       @Param("areaId") Integer areaId,
                                       Pageable pageable);

    @Query("""
            select count(op) > 0 from OrdenFabricacionOperacion op
            where op.ordenFabricacion.ordenFabricacionId = :id and op.areaOperativa.areaId = :areaId
            """)
    boolean perteneceAlArea(@Param("id") Long id, @Param("areaId") int areaId);

    @EntityGraph(attributePaths = {"semiTerminado", "manufacturingVersion", "creadaPor", "responsable"})
    @Query("""
            SELECT o
            FROM OrdenFabricacion o
            LEFT JOIN Lote l ON l.ordenFabricacion = o
            WHERE (:search IS NULL OR :search = ''
                   OR LOWER(o.semiTerminado.productoId) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(o.semiTerminado.nombre) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR CAST(o.ordenFabricacionId AS string) LIKE CONCAT('%', :search, '%')
                   OR LOWER(COALESCE(l.batchNumber, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<OrdenFabricacion> buscar(@Param("search") String search, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrdenFabricacion o WHERE o.ordenFabricacionId = :id")
    Optional<OrdenFabricacion> findByIdForUpdate(@Param("id") Long id);

    boolean existsByOrdenProduccionOrigen_OrdenIdAndSemiTerminado_ProductoId(
            int ordenId, String semiTerminadoId);

    List<OrdenFabricacion> findByOrdenProduccionOrigen_OrdenId(int ordenId);

    @Query("""
            SELECT o FROM OrdenFabricacion o
            WHERE o.estado IN :estados
              AND o.fechaLanzamiento IS NOT NULL
              AND o.fechaLanzamiento <= :ahora
            """)
    List<OrdenFabricacion> findPendientesLiberacion(
            @Param("estados") Collection<exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion> estados,
            @Param("ahora") LocalDateTime ahora);
}
