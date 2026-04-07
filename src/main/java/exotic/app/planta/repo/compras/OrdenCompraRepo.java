package exotic.app.planta.repo.compras;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.Proveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrdenCompraRepo extends JpaRepository<OrdenCompraMateriales, Integer> {

    Page<OrdenCompraMateriales> findByFechaEmisionBetweenAndEstadoIn(
            LocalDateTime startDate,
            LocalDateTime endDate,
            List<Integer> estados,
            Pageable pageable
    );

    Page<OrdenCompraMateriales> findByEstado(
            int estado,
            Pageable pageable
    );

    Page<OrdenCompraMateriales> findByProveedorIdAndEstado(
            String proveedorId,
            int estado,
            Pageable pageable
    );

    Page<OrdenCompraMateriales> findByEstadoAndProveedorAndFechaEmisionBetween(
            int estado,
            Proveedor proveedor,
            LocalDateTime fechaEmisionAfter,
            LocalDateTime fechaEmisionBefore,
            Pageable pageable
    );

    @Query("""
            SELECT orden
            FROM OrdenCompraMateriales orden
            WHERE orden.fechaEmision BETWEEN :startDate AND :endDate
            AND orden.estado IN :estados
            AND (:proveedorId IS NULL OR orden.proveedor.id = :proveedorId)
            """)
    Page<OrdenCompraMateriales> findByFechaEmisionBetweenAndEstadoInAndProveedor(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("estados") List<Integer> estados,
            @Param("proveedorId") String proveedorId,
            Pageable pageable
    );

    Optional<OrdenCompraMateriales> findByOrdenCompraIdAndEstado(Integer ordenCompraId, int estado);

    /**
     * Verifica si existe al menos una orden de compra con el estado especificado
     * @param estado el estado a buscar
     * @return true si existe al menos una orden con ese estado, false en caso contrario
     */
    boolean existsByEstado(int estado);

    long countByEstado(int estado);

    /**
     * Listado liviano para notificaciones COMPRAS (campana); ordenado por fecha de emisión descendente.
     */
    List<OrdenCompraMateriales> findByEstadoOrderByFechaEmisionDesc(int estado);

    /**
     * Calcula el porcentaje de materiales recibidos para múltiples órdenes de compra en batch.
     * 
     * Esta query SQL nativa optimiza el cálculo de porcentajes recibidos haciendo una sola
     * consulta para todas las órdenes, en lugar de hacer una query por cada orden.
     * 
     * Lógica del cálculo:
     * - Suma todas las cantidades ordenadas (item_orden_compra.cantidad) agrupadas por orden_compra_id
     * - Suma todas las cantidades recibidas (movimientos.cantidad) de las transacciones asociadas
     * - Calcula: (cantidad_recibida / cantidad_ordenada) * 100
     * 
     * Notas importantes:
     * - Si no hay items ordenados, retorna 0.0
     * - Si se recibió más de lo ordenado, el porcentaje puede ser > 100% (válido)
     * - Usa LEFT JOINs para incluir órdenes sin transacciones (retornará 0.0)
     * - El valor 0 en tipo_entidad_causante corresponde al ordinal del enum TipoEntidadCausante.OCM
     *   (el campo se almacena como smallint en la base de datos, no como string)
     * 
     * @param ordenIds Lista de IDs de órdenes de compra para calcular porcentajes
     * @return Lista de arrays [ordenCompraId, porcentajeRecibido] donde:
     *         - ordenCompraId (Integer): ID de la orden de compra
     *         - porcentajeRecibido (Double): Porcentaje calculado (0.0 a N, donde N puede ser > 100)
     * 
     * @see IngresoAlmacenService#consultarOCMsPendientesRecepcion
     * @see TransaccionAlmacen.TipoEntidadCausante#OCM (ordinal = 0)
     */
    @Query(value = """
        SELECT 
            oc.orden_compra_id as ordenCompraId,
            CASE 
                WHEN COALESCE(SUM(ioc.cantidad), 0) = 0 THEN 0.0
                ELSE (COALESCE(SUM(m.cantidad), 0.0) / SUM(ioc.cantidad)) * 100.0
            END as porcentajeRecibido
        FROM orden_compra oc
        LEFT JOIN item_orden_compra ioc ON ioc.orden_compra_id = oc.orden_compra_id
        LEFT JOIN transaccion_almacen ta ON ta.tipo_entidad_causante = 0 
            AND ta.id_entidad_causante = oc.orden_compra_id
        LEFT JOIN movimientos m ON m.transaccion_id = ta.transaccion_id
        WHERE oc.orden_compra_id IN :ordenIds
        GROUP BY oc.orden_compra_id
        """, nativeQuery = true)
    List<Object[]> calcularPorcentajesRecibidos(@Param("ordenIds") List<Integer> ordenIds);
}
