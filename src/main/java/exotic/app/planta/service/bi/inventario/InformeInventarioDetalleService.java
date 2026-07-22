package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.bi.dto.PaginaInformeInventarioDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InformeInventarioDetalleService {
    private static final int MAX_PAGE_SIZE = 50;

    private final PendientesInventarioAssembler pendingAssembler;

    public PaginaInformeInventarioDTO<InformeInventarioDTO.OcmDTO>
    getPendingPurchaseOrders(int page, int size) {
        validatePage(page, size);
        return pendingAssembler.getPendingPurchaseOrdersPage(page, size);
    }

    public PaginaInformeInventarioDTO<InformeInventarioDTO.OpMaterialDTO>
    getOpenProductionOrderMaterial(int page, int size) {
        validatePage(page, size);
        return pendingAssembler.getOpenProductionOrderMaterialPage(page, size);
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page no puede ser negativo.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size debe estar entre 1 y 50.");
        }
    }
}
