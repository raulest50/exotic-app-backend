package exotic.app.planta.repo.activos.fijos.gestion;

import exotic.app.planta.model.activos.fijos.gestion.IncorporacionActivoHeader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface IncorporacionActivoHeaderRepo extends JpaRepository<IncorporacionActivoHeader, Long> {

    boolean existsByAsientoContable_Id(Long asientoId);

    /**
     * Cuenta las incorporaciones de activos con un estado contable específico.
     * 
     * @param estadoContable Estado contable de las incorporaciones
     * @return Número de incorporaciones que cumplen con el filtro
     */
    long countByEstadoContable(IncorporacionActivoHeader.EstadoContable estadoContable);

    /**
     * Consulta dinámica para filtrar incorporaciones de activos por estado contable, estado y rango de fechas.
     * 
     * @param estadoContable Estado contable de las incorporaciones (opcional)
     * @param estado Estado de la incorporación (opcional)
     * @param fechaInicio Fecha inicial del rango (opcional)
     * @param fechaFin Fecha final del rango (opcional)
     * @param pageable Configuración de paginación
     * @return Página de incorporaciones que cumplen con los filtros
     */
    @Query("SELECT i FROM IncorporacionActivoHeader i WHERE " +
           "(:estadoContable IS NULL OR i.estadoContable = :estadoContable) AND " +
           "(:estado IS NULL OR i.estado = :estado) AND " +
           "(:fechaInicio IS NULL OR i.fechaIncorporacion >= :fechaInicio) AND " +
           "(:fechaFin IS NULL OR i.fechaIncorporacion <= :fechaFin)")
    Page<IncorporacionActivoHeader> findByFilters(
        @Param("estadoContable") IncorporacionActivoHeader.EstadoContable estadoContable,
        @Param("estado") Integer estado,
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        Pageable pageable
    );
}
