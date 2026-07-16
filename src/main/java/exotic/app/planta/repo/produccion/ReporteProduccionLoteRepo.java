package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.ReporteProduccionLote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReporteProduccionLoteRepo extends JpaRepository<ReporteProduccionLote, Long> {

    boolean existsByOrdenProduccion_OrdenIdAndEstadoNot(
            int ordenId,
            ReporteProduccionLote.Estado estado
    );

    Optional<ReporteProduccionLote> findFirstBySeguimientoOrdenArea_IdAndEstado(
            Long seguimientoId,
            ReporteProduccionLote.Estado estado
    );

    boolean existsBySeguimientoOrdenArea_IdAndEstado(
            Long seguimientoId,
            ReporteProduccionLote.Estado estado
    );

    @EntityGraph(attributePaths = {
            "ordenProduccion",
            "ordenProduccion.producto",
            "lote",
            "seguimientoOrdenArea",
            "reportadoPor"
    })
    List<ReporteProduccionLote> findByFechaProduccionAndEstadoOrderByReportadoEnAscIdAsc(
            LocalDate fechaProduccion,
            ReporteProduccionLote.Estado estado
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT reporte
            FROM ReporteProduccionLote reporte
            JOIN FETCH reporte.ordenProduccion orden
            JOIN FETCH orden.producto producto
            JOIN FETCH reporte.lote lote
            JOIN FETCH reporte.seguimientoOrdenArea seguimiento
            WHERE reporte.fechaProduccion = :fechaProduccion
              AND reporte.estado = :estado
            ORDER BY reporte.reportadoEn ASC, reporte.id ASC
            """)
    List<ReporteProduccionLote> findPendientesByFechaForUpdate(
            @Param("fechaProduccion") LocalDate fechaProduccion,
            @Param("estado") ReporteProduccionLote.Estado estado
    );

    @EntityGraph(attributePaths = {
            "ordenProduccion",
            "ordenProduccion.producto",
            "lote",
            "transaccionAlmacen"
    })
    List<ReporteProduccionLote> findByCierreProduccion_IdOrderByIdAsc(Long cierreId);

    @Query("""
            SELECT reporte.fechaProduccion AS fechaProduccion,
                   COUNT(reporte.id) AS cantidadReportes,
                   COALESCE(SUM(reporte.cantidadReportada), 0) AS totalUnidades
            FROM ReporteProduccionLote reporte
            WHERE reporte.estado = :estado
            GROUP BY reporte.fechaProduccion
            ORDER BY reporte.fechaProduccion ASC
            """)
    List<FechaPendienteProjection> resumirPendientesPorFecha(
            @Param("estado") ReporteProduccionLote.Estado estado
    );

    interface FechaPendienteProjection {
        LocalDate getFechaProduccion();
        Long getCantidadReportes();
        BigDecimal getTotalUnidades();
    }
}
