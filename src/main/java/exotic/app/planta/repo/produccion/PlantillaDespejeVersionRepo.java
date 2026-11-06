package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.despeje.PlantillaDespejeVersion;
import exotic.app.planta.model.produccion.despeje.PlantillaDespejeVersion.Estado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PlantillaDespejeVersionRepo extends JpaRepository<PlantillaDespejeVersion, Long> {
    List<PlantillaDespejeVersion> findByAreaOperativa_AreaIdOrderByNumeroDesc(Integer areaId);
    Optional<PlantillaDespejeVersion> findByAreaOperativa_AreaIdAndEstado(Integer areaId, Estado estado);
    @Query("select coalesce(max(p.numero),0) from PlantillaDespejeVersion p where p.areaOperativa.areaId = :areaId")
    int maxNumero(@Param("areaId") Integer areaId);
}
