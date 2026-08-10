package exotic.app.planta.repo.organigrama;

import exotic.app.planta.model.organigrama.Cargo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CargoOrganigramaRepo extends JpaRepository<Cargo, String> {
    List<Cargo> findAllByOrderByIdCargoAsc();
}
