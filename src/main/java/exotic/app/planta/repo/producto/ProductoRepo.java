package exotic.app.planta.repo.producto;

import exotic.app.planta.model.producto.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductoRepo extends JpaRepository<Producto, String>, JpaSpecificationExecutor<Producto> {

    Optional<Producto> findByProductoId(String id);

    Optional<Producto> findByPrefijoLoteIgnoreCase(String prefijoLote);

}
