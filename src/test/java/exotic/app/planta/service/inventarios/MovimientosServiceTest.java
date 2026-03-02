package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.producto.dto.ProductoStockDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.repo.master.configs.MasterDirectiveRepo;
import exotic.app.planta.service.contabilidad.ContabilidadService;
import exotic.app.planta.service.produccion.ProduccionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class MovimientosServiceTest {

    @Test
    void getStockOf2ReturnsCorrectStock() {
        TransaccionAlmacenRepo transRepo = Mockito.mock(TransaccionAlmacenRepo.class);
        ProductoRepo prodRepo = Mockito.mock(ProductoRepo.class);
        TransaccionAlmacenHeaderRepo headerRepo = Mockito.mock(TransaccionAlmacenHeaderRepo.class);
        OrdenCompraRepo ordenRepo = Mockito.mock(OrdenCompraRepo.class);
        SemiTerminadoRepo semiRepo = Mockito.mock(SemiTerminadoRepo.class);
        TerminadoRepo termRepo = Mockito.mock(TerminadoRepo.class);
        MaterialRepo materialRepo = Mockito.mock(MaterialRepo.class);
        LoteRepo loteRepo = Mockito.mock(LoteRepo.class);
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        ContabilidadService contService = Mockito.mock(ContabilidadService.class);
        ProduccionService produccionService = Mockito.mock(ProduccionService.class);
        OrdenProduccionRepo ordenProduccionRepo = Mockito.mock(OrdenProduccionRepo.class);
        MasterDirectiveRepo masterDirectiveRepo = Mockito.mock(MasterDirectiveRepo.class);

        MovimientosService service = new MovimientosService(transRepo, prodRepo, headerRepo,
                ordenRepo, semiRepo, termRepo, materialRepo,
                loteRepo, userRepo, contService, produccionService, ordenProduccionRepo, masterDirectiveRepo);

        Material producto = new Material();
        producto.setProductoId("P1");

        when(prodRepo.findByProductoId("P1")).thenReturn(Optional.of(producto));

        Movimiento m1 = new Movimiento();
        m1.setCantidad(5);
        m1.setProducto(producto);

        Movimiento m2 = new Movimiento();
        m2.setCantidad(3);
        m2.setProducto(producto);

        when(transRepo.findByProducto_ProductoId("P1")).thenReturn(List.of(m1, m2));

        Optional<ProductoStockDTO> result = service.getStockOf2("P1");

        assertTrue(result.isPresent());
        assertEquals(8.0, result.get().getStock());
        assertEquals("P1", result.get().getProducto().getProductoId());
    }
}
