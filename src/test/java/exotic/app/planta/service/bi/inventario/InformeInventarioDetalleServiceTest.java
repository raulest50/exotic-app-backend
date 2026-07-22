package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.bi.dto.PaginaInformeInventarioDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InformeInventarioDetalleServiceTest {

    @Test
    void delegatesIndependentPagesAndEnforcesBounds() {
        PendientesInventarioAssembler assembler = mock(PendientesInventarioAssembler.class);
        InformeInventarioDetalleService service =
                new InformeInventarioDetalleService(assembler);
        PaginaInformeInventarioDTO<InformeInventarioDTO.OcmDTO> expected =
                new PaginaInformeInventarioDTO<>(
                        List.of(), 1, 10, 11, 2, false, true);
        when(assembler.getPendingPurchaseOrdersPage(1, 10)).thenReturn(expected);

        assertEquals(expected, service.getPendingPurchaseOrders(1, 10));
        verify(assembler).getPendingPurchaseOrdersPage(1, 10);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getPendingPurchaseOrders(-1, 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getOpenProductionOrderMaterial(0, 51));
    }
}
