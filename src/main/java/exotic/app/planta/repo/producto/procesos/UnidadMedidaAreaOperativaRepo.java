package exotic.app.planta.repo.producto.procesos;

import exotic.app.planta.model.organizacion.UnidadMedidaAreaOperativa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnidadMedidaAreaOperativaRepo extends JpaRepository<UnidadMedidaAreaOperativa, Long> {
    List<UnidadMedidaAreaOperativa> findAllByAreaOperativa_AreaIdOrderByNombreAsc(Integer areaId);
    Optional<UnidadMedidaAreaOperativa> findByIdAndAreaOperativa_AreaId(Long id, Integer areaId);
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
