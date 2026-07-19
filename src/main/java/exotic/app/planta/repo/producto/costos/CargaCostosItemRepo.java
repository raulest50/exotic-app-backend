package exotic.app.planta.repo.producto.costos;

import exotic.app.planta.model.producto.costos.CargaCostosItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CargaCostosItemRepo extends JpaRepository<CargaCostosItem, Long> {
    Page<CargaCostosItem> findByLote_IdOrderByFilaExcelAsc(UUID loteId, Pageable pageable);

    List<CargaCostosItem> findByLote_IdOrderByProductoIdAsc(UUID loteId);
}
