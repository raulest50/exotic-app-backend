package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.produccion.fabricacion.MpsFabricacionSemanalRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SemiTerMpsFabricacionTest {
    @Mock private ProductoRepo productoRepo;
    @Mock private SemiTerminadoRepo semiRepo;
    @Mock private MpsFabricacionSemanalRepo programaRepo;
    @InjectMocks private SemiTerService service;

    @Test
    void unaPropuestaImpideBorradoNormalYForzadoSinEliminarElPrograma() {
        var semi = new SemiTerminado();
        semi.setProductoId("S1");
        when(productoRepo.findById("S1")).thenReturn(Optional.of(semi));
        when(programaRepo.existsByDetalles_SemiTerminado_ProductoId("S1")).thenReturn(true);
        var estado = service.isProductoDeletable("S1");
        assertEquals(false, estado.get("deletable"));
        assertTrue(estado.get("reason").toString().contains("MPS OF"));
        assertThrows(IllegalStateException.class, () -> service.deleteProductoProvisional("S1"));
        assertThrows(IllegalStateException.class, () -> service.forceDeleteProducto("S1"));
        verifyNoInteractions(semiRepo);
        verify(programaRepo, never()).deleteAll();
    }
}
