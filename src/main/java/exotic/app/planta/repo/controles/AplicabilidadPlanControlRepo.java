package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.AplicabilidadPlanControl;
import exotic.app.planta.model.controles.EstadoVersionPlanControl;
import exotic.app.planta.model.controles.TipoOrdenControl;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AplicabilidadPlanControlRepo extends JpaRepository<AplicabilidadPlanControl, Long> {
    @EntityGraph(attributePaths = {"version", "version.plan", "producto", "categoria",
            "areaOperativa", "proceso", "productosExcluidos"})
    @Query("""
            select a from AplicabilidadPlanControl a
            where a.version.estado = :estado
              and (a.tipoOrden = :tipoOrden or a.tipoOrden = exotic.app.planta.model.controles.TipoOrdenControl.AMBAS)
              and (a.legadoGlobal = true or a.producto.productoId = :productoId
                or (a.producto is null and a.categoria.categoriaId = :categoriaId)
                or (:categoriaCompleta = true and exists (
                    select t.productoId from Terminado t
                    where t.productoId = a.producto.productoId and t.categoria.categoriaId = :categoriaId)))
            order by a.version.plan.codigo, a.id
            """)
    List<AplicabilidadPlanControl> findParaRuta(
            @Param("estado") EstadoVersionPlanControl estado,
            @Param("tipoOrden") TipoOrdenControl tipoOrden,
            @Param("productoId") String productoId,
            @Param("categoriaId") Integer categoriaId,
            @Param("categoriaCompleta") boolean categoriaCompleta);
}
