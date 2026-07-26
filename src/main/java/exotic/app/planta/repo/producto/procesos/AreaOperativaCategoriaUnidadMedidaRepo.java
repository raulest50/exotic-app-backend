package exotic.app.planta.repo.producto.procesos;

import exotic.app.planta.model.organizacion.AreaOperativaCategoriaUnidadMedida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AreaOperativaCategoriaUnidadMedidaRepo extends JpaRepository<AreaOperativaCategoriaUnidadMedida, Long> {
    List<AreaOperativaCategoriaUnidadMedida> findAllByAreaOperativa_AreaId(Integer areaId);
    List<AreaOperativaCategoriaUnidadMedida> findAllByAreaOperativa_AreaIdIn(Collection<Integer> areaIds);

    @Query("""
            SELECT association
            FROM AreaOperativaCategoriaUnidadMedida association
            JOIN FETCH association.areaOperativa area
            JOIN FETCH association.categoria category
            JOIN FETCH association.unidadMedida unit
            WHERE area.areaId IN :areaIds
            """)
    List<AreaOperativaCategoriaUnidadMedida> findAnaliticaByAreaIds(
            @Param("areaIds") Collection<Integer> areaIds
    );

    void deleteAllByAreaOperativa_AreaId(Integer areaId);
    void deleteAllByUnidadMedida_Id(Long unidadId);
}
