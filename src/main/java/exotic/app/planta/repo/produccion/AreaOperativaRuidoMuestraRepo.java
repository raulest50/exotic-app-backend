package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.AreaOperativaRuidoMuestra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AreaOperativaRuidoMuestraRepo extends JpaRepository<AreaOperativaRuidoMuestra, Long> {

    boolean existsByAreaOperativa_AreaIdAndUsuario_IdAndCreatedAtAfter(
            Integer areaId,
            Long usuarioId,
            LocalDateTime createdAt
    );
}
