package exotic.app.planta.repo.producto.costos;

import exotic.app.planta.model.producto.costos.RecetaCostosRevision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RecetaCostosRevisionRepo extends JpaRepository<RecetaCostosRevision, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecetaCostosRevision r where r.id = 1")
    Optional<RecetaCostosRevision> findSingletonForUpdate();
}
