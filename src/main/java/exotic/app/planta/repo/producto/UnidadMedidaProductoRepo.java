package exotic.app.planta.repo.producto;

import exotic.app.planta.model.producto.UnidadMedidaProducto;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/** Acceso de solo lectura; el catálogo se modifica únicamente mediante migraciones. */
public interface UnidadMedidaProductoRepo extends Repository<UnidadMedidaProducto, String> {
    Optional<UnidadMedidaProducto> findById(String codigo);

    List<UnidadMedidaProducto> findAllByOrderByCodigoAsc();

    List<UnidadMedidaProducto> findByMagnitudOrderByCodigoAsc(UnidadMedidaProducto.Magnitud magnitud);
}
