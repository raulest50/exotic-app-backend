package exotic.app.planta.repo.producto.procesos;

import exotic.app.planta.model.organizacion.AreaOperativa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface AreaProduccionRepo extends JpaRepository<AreaOperativa, Integer>, JpaSpecificationExecutor<AreaOperativa> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AreaOperativa a where a.areaId = :id")
    Optional<AreaOperativa> findByIdForUpdate(@Param("id") Integer id);
    Optional<AreaOperativa> findByNombre(String nombre);
    List<AreaOperativa> findAllByResponsableArea_Id(Long userId);
    List<AreaOperativa> findAllByResponsableAreaIsNotNullAndAreaIdNotOrderByNombreAsc(Integer areaId);
    boolean existsByResponsableArea_Id(Long userId);
    boolean existsByResponsableArea_IdAndAreaIdNot(Long userId, Integer areaId);
}
