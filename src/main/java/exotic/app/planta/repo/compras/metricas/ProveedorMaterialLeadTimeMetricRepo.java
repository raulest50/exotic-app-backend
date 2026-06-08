package exotic.app.planta.repo.compras.metricas;

import exotic.app.planta.model.compras.metricas.ProveedorMaterialLeadTimeMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProveedorMaterialLeadTimeMetricRepo extends JpaRepository<ProveedorMaterialLeadTimeMetric, Long> {

    Optional<ProveedorMaterialLeadTimeMetric> findByProveedor_PkAndMaterial_ProductoId(Long proveedorPk, String productoId);

    List<ProveedorMaterialLeadTimeMetric> findByProveedor_Pk(Long proveedorPk);

    List<ProveedorMaterialLeadTimeMetric> findByMaterial_ProductoId(String productoId);
}
