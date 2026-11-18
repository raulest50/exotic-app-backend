package exotic.app.planta.repo.producto;

import exotic.app.planta.model.producto.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface ProductoRepo extends JpaRepository<Producto, String>, JpaSpecificationExecutor<Producto> {

    interface CatalogoFormulacionProjection {
        String getProductoId();
        String getNombre();
        String getTipoProducto();
        String getTipoUnidades();
        String getFotoUrl();
        String getCategoriaNombre();
        Integer getStatus();
        BigDecimal getCantidadSalidaReceta();
    }

    @Query(value = """
            SELECT p.producto_id AS "productoId", p.nombre AS "nombre",
                   p.tipo_producto AS "tipoProducto", p.tipo_unidades AS "tipoUnidades",
                   p.foto_url AS "fotoUrl", c.categoria_nombre AS "categoriaNombre",
                   p.status AS "status", pp.cantidad_salida_receta AS "cantidadSalidaReceta"
              FROM productos p
              LEFT JOIN categoria c ON c.categoria_id = p.categoria_id
              LEFT JOIN procesos_produccion pp ON pp.producto_id = p.producto_id
             WHERE p.tipo_producto IN ('T', 'S')
               AND (:tipo = '' OR p.tipo_producto = :tipo)
               AND (:search = '' OR strpos(lower(p.producto_id), lower(:search)) > 0
                    OR strpos(lower(coalesce(p.nombre, '')), lower(:search)) > 0)
             ORDER BY lower(p.nombre) NULLS LAST, p.producto_id
            """, countQuery = """
            SELECT count(*) FROM productos p
             WHERE p.tipo_producto IN ('T', 'S')
               AND (:tipo = '' OR p.tipo_producto = :tipo)
               AND (:search = '' OR strpos(lower(p.producto_id), lower(:search)) > 0
                    OR strpos(lower(coalesce(p.nombre, '')), lower(:search)) > 0)
            """, nativeQuery = true)
    Page<CatalogoFormulacionProjection> buscarCatalogoFormulacion(
            @Param("search") String search, @Param("tipo") String tipo, Pageable pageable);

    @Query(value = """
            SELECT p.producto_id AS "productoId", p.nombre AS "nombre",
                   p.tipo_producto AS "tipoProducto", p.tipo_unidades AS "tipoUnidades",
                   p.foto_url AS "fotoUrl", c.categoria_nombre AS "categoriaNombre",
                   p.status AS "status", pp.cantidad_salida_receta AS "cantidadSalidaReceta"
              FROM productos p
              LEFT JOIN categoria c ON c.categoria_id = p.categoria_id
              LEFT JOIN procesos_produccion pp ON pp.producto_id = p.producto_id
             WHERE p.producto_id = :productoId AND p.tipo_producto IN ('T', 'S')
            """, nativeQuery = true)
    Optional<CatalogoFormulacionProjection> findCatalogoFormulacionById(@Param("productoId") String productoId);

    Optional<Producto> findByProductoId(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.productoId = :id")
    Optional<Producto> findByProductoIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.productoId in :ids order by p.productoId")
    List<Producto> findAllByProductoIdInForUpdate(@Param("ids") Collection<String> ids);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Producto p
               set p.costo = :nuevoCosto,
                   p.costoVersion = :nuevaVersion
             where p.productoId = :productoId
               and p.costoVersion = :versionEsperada
            """)
    int actualizarCostoSiVersion(
            @Param("productoId") String productoId,
            @Param("nuevoCosto") java.math.BigDecimal nuevoCosto,
            @Param("versionEsperada") long versionEsperada,
            @Param("nuevaVersion") long nuevaVersion);

    Optional<Producto> findByPrefijoLoteIgnoreCase(String prefijoLote);

}
