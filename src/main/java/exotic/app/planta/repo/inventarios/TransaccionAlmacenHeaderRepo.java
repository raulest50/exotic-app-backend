package exotic.app.planta.repo.inventarios;

import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransaccionAlmacenHeaderRepo extends JpaRepository<TransaccionAlmacen, Integer> {

    /**
     * Cuenta las transacciones de almacén con un estado contable y tipo de entidad causante específicos.
     * 
     * @param estadoContable Estado contable de las transacciones
     * @param tipoEntidadCausante Tipo de entidad causante
     * @return Número de transacciones que cumplen con los filtros
     */
    long countByEstadoContableAndTipoEntidadCausante(
        TransaccionAlmacen.EstadoContable estadoContable,
        TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante
    );

    /**
     * Consulta dinámica para filtrar transacciones de almacén por estado contable, tipo de entidad causante y rango de fechas.
     * 
     * @param estadoContable Estado contable de las transacciones (opcional)
     * @param tipoEntidadCausante Tipo de entidad causante (requerido)
     * @param fechaInicio Fecha inicial del rango (opcional)
     * @param fechaFin Fecha final del rango (opcional)
     * @param pageable Configuración de paginación
     * @return Página de transacciones que cumplen con los filtros
     */
    @Query("SELECT t FROM TransaccionAlmacen t WHERE " +
           "(:estadoContable IS NULL OR t.estadoContable = :estadoContable) AND " +
           "t.tipoEntidadCausante = :tipoEntidadCausante AND " +
           "(:fechaInicio IS NULL OR t.fechaTransaccion >= :fechaInicio) AND " +
           "(:fechaFin IS NULL OR t.fechaTransaccion <= :fechaFin)")
    Page<TransaccionAlmacen> findByFilters(
        @Param("estadoContable") TransaccionAlmacen.EstadoContable estadoContable,
        @Param("tipoEntidadCausante") TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante,
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        Pageable pageable
    );

    List<TransaccionAlmacen> findByTipoEntidadCausanteAndIdEntidadCausante(
        TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante,
        int idEntidadCausante
    );

    /**
     * Busca una transacción por ID cargando sus movimientos con fetch join.
     * Incluye las relaciones de producto y lote para evitar N+1 queries.
     *
     * @param transaccionId ID de la transacción
     * @return Transacción con movimientos cargados
     */
    @Query("SELECT DISTINCT t FROM TransaccionAlmacen t " +
           "LEFT JOIN FETCH t.movimientosTransaccion m " +
           "LEFT JOIN FETCH m.producto p " +
           "LEFT JOIN FETCH m.lote l " +
           "WHERE t.transaccionId = :transaccionId")
    Optional<TransaccionAlmacen> findByIdWithMovimientos(@Param("transaccionId") int transaccionId);

    /**
     * Busca transacciones por tipo y entidad causante cargando movimientos con fetch join.
     * Incluye las relaciones de producto y lote para evitar N+1 queries.
     *
     * @param tipoEntidadCausante Tipo de entidad causante
     * @param idEntidadCausante ID de la entidad causante
     * @return Lista de transacciones con movimientos cargados
     */
    @Query("SELECT DISTINCT t FROM TransaccionAlmacen t " +
           "LEFT JOIN FETCH t.movimientosTransaccion m " +
           "LEFT JOIN FETCH m.producto p " +
           "LEFT JOIN FETCH m.lote l " +
           "WHERE t.tipoEntidadCausante = :tipoEntidadCausante " +
           "AND t.idEntidadCausante = :idEntidadCausante")
    List<TransaccionAlmacen> findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
        @Param("tipoEntidadCausante") TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante,
        @Param("idEntidadCausante") int idEntidadCausante
    );

    /**
     * Busca dispensaciones (transacciones tipo OD) con filtros dinámicos.
     * Permite filtrar por ID de transacción, ID de orden de producción, y fechas.
     * Los parámetros opcionales se manejan con condiciones NULL-safe.
     *
     * @param transaccionId ID de la transacción (opcional)
     * @param ordenProduccionId ID de la orden de producción (opcional)
     * @param fechaInicio Fecha inicial del rango (opcional, inicio del día)
     * @param fechaFin Fecha final del rango (opcional, fin del día)
     * @param pageable Configuración de paginación
     * @return Página de transacciones que cumplen con los filtros
     */
    @Query("SELECT t FROM TransaccionAlmacen t WHERE " +
           "t.tipoEntidadCausante = :tipoEntidadCausante AND " +
           "(:transaccionId IS NULL OR t.transaccionId = :transaccionId) AND " +
           "(:ordenProduccionId IS NULL OR t.idEntidadCausante = :ordenProduccionId) AND " +
           "(:fechaInicio IS NULL OR t.fechaTransaccion >= :fechaInicio) AND " +
           "(:fechaFin IS NULL OR t.fechaTransaccion <= :fechaFin) " +
           "ORDER BY t.fechaTransaccion DESC")
    Page<TransaccionAlmacen> findDispensacionesFiltradas(
        @Param("tipoEntidadCausante") TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante,
        @Param("transaccionId") Integer transaccionId,
        @Param("ordenProduccionId") Integer ordenProduccionId,
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        Pageable pageable
    );

    /**
     * Busca todas las dispensaciones (transacciones tipo OD) sin filtros adicionales.
     * Usado cuando no se especifican filtros de ID o fecha.
     *
     * @param tipoEntidadCausante Tipo de entidad causante (OD para dispensaciones)
     * @param pageable Configuración de paginación
     * @return Página de transacciones ordenadas por fecha descendente
     */
    Page<TransaccionAlmacen> findByTipoEntidadCausanteOrderByFechaTransaccionDesc(
        TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante,
        Pageable pageable
    );

    /**
     * Busca dispensaciones filtradas solo por ID de transacción.
     */
    @Query("SELECT t FROM TransaccionAlmacen t WHERE " +
           "t.tipoEntidadCausante = :tipoEntidadCausante AND " +
           "t.transaccionId = :transaccionId " +
           "ORDER BY t.fechaTransaccion DESC")
    Page<TransaccionAlmacen> findByTipoEntidadCausanteAndTransaccionId(
        @Param("tipoEntidadCausante") TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante,
        @Param("transaccionId") Integer transaccionId,
        Pageable pageable
    );

    /**
     * Busca dispensaciones filtradas solo por ID de orden de producción.
     */
    @Query("SELECT t FROM TransaccionAlmacen t WHERE " +
           "t.tipoEntidadCausante = :tipoEntidadCausante AND " +
           "t.idEntidadCausante = :ordenProduccionId " +
           "ORDER BY t.fechaTransaccion DESC")
    Page<TransaccionAlmacen> findByTipoEntidadCausanteAndOrdenProduccionId(
        @Param("tipoEntidadCausante") TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante,
        @Param("ordenProduccionId") Integer ordenProduccionId,
        Pageable pageable
    );

    /**
     * Busca dispensaciones filtradas solo por rango de fechas.
     */
    @Query("SELECT t FROM TransaccionAlmacen t WHERE " +
           "t.tipoEntidadCausante = :tipoEntidadCausante AND " +
           "t.fechaTransaccion >= :fechaInicio AND " +
           "t.fechaTransaccion <= :fechaFin " +
           "ORDER BY t.fechaTransaccion DESC")
    Page<TransaccionAlmacen> findByTipoEntidadCausanteAndFechaBetween(
        @Param("tipoEntidadCausante") TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante,
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        Pageable pageable
    );

    /**
     * Busca dispensaciones filtradas por ID de transacción y rango de fechas.
     */
    @Query("SELECT t FROM TransaccionAlmacen t WHERE " +
           "t.tipoEntidadCausante = :tipoEntidadCausante AND " +
           "t.transaccionId = :transaccionId AND " +
           "t.fechaTransaccion >= :fechaInicio AND " +
           "t.fechaTransaccion <= :fechaFin " +
           "ORDER BY t.fechaTransaccion DESC")
    Page<TransaccionAlmacen> findByTipoEntidadCausanteAndTransaccionIdAndFechaBetween(
        @Param("tipoEntidadCausante") TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante,
        @Param("transaccionId") Integer transaccionId,
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        Pageable pageable
    );

    /**
     * Busca dispensaciones filtradas por ID de orden de producción y rango de fechas.
     */
    @Query("SELECT t FROM TransaccionAlmacen t WHERE " +
           "t.tipoEntidadCausante = :tipoEntidadCausante AND " +
           "t.idEntidadCausante = :ordenProduccionId AND " +
           "t.fechaTransaccion >= :fechaInicio AND " +
           "t.fechaTransaccion <= :fechaFin " +
           "ORDER BY t.fechaTransaccion DESC")
    Page<TransaccionAlmacen> findByTipoEntidadCausanteAndOrdenProduccionIdAndFechaBetween(
        @Param("tipoEntidadCausante") TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante,
        @Param("ordenProduccionId") Integer ordenProduccionId,
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        Pageable pageable
    );

    // ==================== OCM Queries ====================

    /**
     * Busca transacciones OCM filtradas solo por proveedor NIT.
     * Usa EXISTS con cross-entity join ya que TransaccionAlmacen.idEntidadCausante
     * es un plain int (no una relación mapeada) que apunta a OrdenCompraMateriales.ordenCompraId.
     */
    @Query("SELECT t FROM TransaccionAlmacen t WHERE " +
           "t.tipoEntidadCausante = :tipo AND " +
           "EXISTS (SELECT oc FROM OrdenCompraMateriales oc " +
           "WHERE oc.ordenCompraId = t.idEntidadCausante AND oc.proveedor.id = :proveedorNit) " +
           "ORDER BY t.fechaTransaccion DESC")
    Page<TransaccionAlmacen> findOCMByProveedor(
        @Param("tipo") TransaccionAlmacen.TipoEntidadCausante tipo,
        @Param("proveedorNit") String proveedorNit,
        Pageable pageable
    );

    /**
     * Busca transacciones OCM filtradas por proveedor NIT y rango de fechas.
     */
    @Query("SELECT t FROM TransaccionAlmacen t WHERE " +
           "t.tipoEntidadCausante = :tipo AND " +
           "EXISTS (SELECT oc FROM OrdenCompraMateriales oc " +
           "WHERE oc.ordenCompraId = t.idEntidadCausante AND oc.proveedor.id = :proveedorNit) AND " +
           "t.fechaTransaccion >= :fechaInicio AND " +
           "t.fechaTransaccion <= :fechaFin " +
           "ORDER BY t.fechaTransaccion DESC")
    Page<TransaccionAlmacen> findOCMByProveedorAndFechaBetween(
        @Param("tipo") TransaccionAlmacen.TipoEntidadCausante tipo,
        @Param("proveedorNit") String proveedorNit,
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        Pageable pageable
    );
}
