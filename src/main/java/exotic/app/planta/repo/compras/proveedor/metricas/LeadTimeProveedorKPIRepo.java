package exotic.app.planta.repo.compras.proveedor.metricas;

import exotic.app.planta.model.compras.proveedor.metricas.LeadTimeProveedorKPI;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeadTimeProveedorKPIRepo extends JpaRepository<LeadTimeProveedorKPI, Long> {

    Optional<LeadTimeProveedorKPI> findByProveedor_Pk(Long proveedorPk);
}
