package exotic.app.planta.repo.producto.costos;

import exotic.app.planta.model.producto.costos.CargaCostosPropagacionItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CargaCostosPropagacionItemRepo
        extends JpaRepository<CargaCostosPropagacionItem, Long> {
    Page<CargaCostosPropagacionItem> findByLote_IdOrderByNivelAscProductoIdAsc(
            UUID loteId,
            Pageable pageable);

    List<CargaCostosPropagacionItem> findByLote_IdOrderByNivelAscProductoIdAsc(UUID loteId);
}
