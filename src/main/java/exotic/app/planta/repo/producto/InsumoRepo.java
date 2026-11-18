package exotic.app.planta.repo.producto;

import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InsumoRepo extends JpaRepository<Insumo, Integer> {

    interface FormulacionInsumoProjection {
        Integer getInsumoId();
        String getProductoId();
        String getNombre();
        String getTipoProducto();
        String getTipoUnidades();
        Double getCantidadRequerida();
        Boolean getRequiereOrdenFabricacion();
        java.math.BigDecimal getCantidadSalidaReceta();
    }

    /** Lee una receta sin materializar entidades, stock ni asociaciones de proceso. */
    @Query(value = """
            SELECT i.insumo_id AS "insumoId", i.input_producto_id AS "productoId",
                   p.nombre AS "nombre", p.tipo_producto AS "tipoProducto",
                   p.tipo_unidades AS "tipoUnidades", i.cantidad_requerida AS "cantidadRequerida",
                   coalesce(p.requiere_orden_fabricacion, false) AS "requiereOrdenFabricacion",
                   pp.cantidad_salida_receta AS "cantidadSalidaReceta"
              FROM insumos i
              LEFT JOIN productos p ON p.producto_id = i.input_producto_id
              LEFT JOIN procesos_produccion pp ON pp.producto_id = p.producto_id
             WHERE i.output_producto_id = :productoId
             ORDER BY i.insumo_id
             LIMIT 2001
            """, nativeQuery = true)
    List<FormulacionInsumoProjection> findFormulacionByProductoId(@Param("productoId") String productoId);

    interface CostoRecetaEdgeProjection {
        Integer getInsumoId();
        String getInputProductoId();
        String getOutputProductoId();
        Double getCantidadRequerida();
    }

    /**
     * Busca todos los insumos que referencian a un producto de entrada específico.
     *
     * @param productoId ID del producto referenciado en la receta
     * @return Lista de insumos relacionados
     */
    java.util.List<Insumo> findByProducto_ProductoId(String productoId);

    @Query(value = """
            SELECT i.insumo_id AS "insumoId",
                   i.input_producto_id AS "inputProductoId",
                   i.output_producto_id AS "outputProductoId",
                   i.cantidad_requerida AS "cantidadRequerida"
              FROM insumos i
             WHERE i.input_producto_id IN (:productoIds)
               AND i.output_producto_id IS NOT NULL
             ORDER BY i.output_producto_id, i.input_producto_id, i.insumo_id
            """, nativeQuery = true)
    List<CostoRecetaEdgeProjection> findCostoEdgesByInputProductoIds(
            @Param("productoIds") Collection<String> productoIds);

    @Query(value = """
            SELECT i.insumo_id AS "insumoId",
                   i.input_producto_id AS "inputProductoId",
                   i.output_producto_id AS "outputProductoId",
                   i.cantidad_requerida AS "cantidadRequerida"
              FROM insumos i
             WHERE i.output_producto_id IN (:productoIds)
             ORDER BY i.output_producto_id, i.input_producto_id NULLS FIRST, i.insumo_id
            """, nativeQuery = true)
    List<CostoRecetaEdgeProjection> findCostoEdgesByOutputProductoIds(
            @Param("productoIds") Collection<String> productoIds);
}
