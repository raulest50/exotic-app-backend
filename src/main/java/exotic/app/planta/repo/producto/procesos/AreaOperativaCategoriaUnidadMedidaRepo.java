package exotic.app.planta.repo.producto.procesos;

import exotic.app.planta.model.organizacion.AreaOperativaCategoriaUnidadMedida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AreaOperativaCategoriaUnidadMedidaRepo extends JpaRepository<AreaOperativaCategoriaUnidadMedida, Long> {
    List<AreaOperativaCategoriaUnidadMedida> findAllByAreaOperativa_AreaId(Integer areaId);
    List<AreaOperativaCategoriaUnidadMedida> findAllByAreaOperativa_AreaIdIn(Collection<Integer> areaIds);
    void deleteAllByAreaOperativa_AreaId(Integer areaId);
    void deleteAllByUnidadMedida_Id(Long unidadId);
}
