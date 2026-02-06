package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.OrdenSeguimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface OrdenSeguimientoRepo extends JpaRepository<OrdenSeguimiento, Integer> {

    List<OrdenSeguimiento> findByOrdenProduccion_OrdenId(int ordenId);

    @Transactional
    @Modifying
    @Query("UPDATE OrdenSeguimiento o SET o.estado = :estado, o.fechaFinalizacion = CURRENT_TIMESTAMP WHERE o.seguimientoId = :id")
    void updateEstadoById(@Param("id") int id, @Param("estado") int estado);

}
