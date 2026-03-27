package exotic.app.planta.repo.usuarios;

import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModuloAccesoRepository extends JpaRepository<ModuloAcceso, Long> {

    List<ModuloAcceso> findByUser(User user);
}
