package exotic.app.planta.repo.producto;

import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


// removed extend : , JpaSpecificationExecutor<Terminado>

@Repository
public interface TerminadoRepo extends JpaRepository<Terminado, String>, JpaSpecificationExecutor<Terminado> {

    List<Terminado> findByInsumos_Producto(Producto producto);

    Optional<Terminado> findByPrefijoLote(String prefijoLote);

}
