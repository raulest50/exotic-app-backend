package exotic.app.planta.service.compras;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.dto.UpdateEstadoOrdenCompraRequest;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComprasServiceCierreConcurrenteTest {
    @Mock private OrdenCompraRepo ordenes;
    @InjectMocks private ComprasService service;

    @Test
    void unaSolicitudObsoletaNoReabreNiCancelaUnaOcmQueYaSeCerro() {
        var orden = new OrdenCompraMateriales();
        orden.setOrdenCompraId(7);
        orden.setEstado(3);
        when(ordenes.findByOrdenCompraIdForUpdate(7)).thenReturn(Optional.of(orden));
        var actor = new User();
        actor.setId(1L);
        actor.setUsername("master");
        var request = new UpdateEstadoOrdenCompraRequest();
        request.setNewEstado(2);
        assertThrows(IllegalStateException.class, () -> service.updateEstadoOrdenCompra(7, request, actor));
        assertThrows(IllegalStateException.class, () -> service.cancelOrdenCompra(7));
        assertEquals(3, orden.getEstado());
        verify(ordenes, never()).save(any());
    }
}
