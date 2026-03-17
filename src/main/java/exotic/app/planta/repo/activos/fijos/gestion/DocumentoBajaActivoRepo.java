package exotic.app.planta.repo.activos.fijos.gestion;

import exotic.app.planta.model.activos.fijos.gestion.DocumentoBajaActivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentoBajaActivoRepo extends JpaRepository<DocumentoBajaActivo, Long> {

    boolean existsByAsientoContable_Id(Long asientoId);
}
