package exotic.app.planta.repo.producto.procesos;

import exotic.app.planta.model.organizacion.CapacidadAreaOperativa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CapacidadAreaOperativaRepo extends JpaRepository<CapacidadAreaOperativa, Long> {
    List<CapacidadAreaOperativa> findAllByAreaOperativa_AreaIdOrderByActivoDescTipoCapacidadAscPeriodoAsc(Integer areaId);
    Optional<CapacidadAreaOperativa> findByIdAndAreaOperativa_AreaId(Long id, Integer areaId);
    boolean existsByUnidadMedida_Id(Long unidadId);
}
