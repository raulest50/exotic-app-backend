package exotic.app.planta.repo.ventas;

import exotic.app.planta.model.ventas.OrdenVenta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenVentaRepo extends JpaRepository<OrdenVenta, Integer> {

    @Query("SELECT o FROM OrdenVenta o WHERE (:clienteId IS NULL OR o.cliente.clienteId = :clienteId) " +
            "AND (:estado IS NULL OR o.estado = :estado)")
    Page<OrdenVenta> findByClienteAndEstado(
            @Param("clienteId") Integer clienteId,
            @Param("estado") String estado,
            Pageable pageable
    );

    boolean existsByOrdenVentaId(int ordenVentaId);
}
