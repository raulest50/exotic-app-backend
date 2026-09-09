package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.InsumoRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.service.productos.fichatecnica.MaterialFichaTecnicaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceFichaTecnicaTest {

    @Mock private ProductoRepo productoRepo;
    @Mock private MaterialRepo materialRepo;
    @Mock private SemiTerminadoRepo semiTerminadoRepo;
    @Mock private TerminadoRepo terminadoRepo;
    @Mock private CategoriaRepo categoriaRepo;
    @Mock private InsumoRepo insumoRepository;
    @Mock private TransaccionAlmacenRepo transaccionAlmacenRepo;
    @Mock private OrdenProduccionRepo ordenProduccionRepo;
    @Mock private ItemOrdenCompraRepo itemOrdenCompraRepo;
    @Mock private MaterialFichaTecnicaService materialFichaTecnicaService;
    @Mock private ProductoCostoService productoCostoService;

    @InjectMocks private ProductoService productoService;

    @Test
    void delegatesInitialPdfToVersionService() {
        Material material = validMaterial("M-1");
        MockMultipartFile file = new MockMultipartFile(
                "file", "ficha.pdf", "application/pdf", new byte[]{1}
        );
        when(materialRepo.save(material)).thenReturn(material);
        when(productoCostoService.registrarCostoInicial(any(), any())).thenReturn(
                new ProductoCostoService.ResultadoCambio(material, BigDecimal.ZERO, BigDecimal.ZERO, true, 1)
        );

        Material saved = productoService.saveMateriaPrimaV2(material, file, "creator");

        assertEquals(material, saved);
        verify(materialFichaTecnicaService).crearNuevaVersion("M-1", file, null, "creator");
    }

    @Test
    void createsMaterialWithoutTechnicalSheet() {
        Material material = validMaterial("M-2");
        when(materialRepo.save(material)).thenReturn(material);
        when(productoCostoService.registrarCostoInicial(any(), any())).thenReturn(
                new ProductoCostoService.ResultadoCambio(material, BigDecimal.ZERO, BigDecimal.ZERO, true, 1)
        );

        productoService.saveMateriaPrimaV2(material, null, "creator");

        verify(materialFichaTecnicaService, never())
                .crearNuevaVersion(any(), any(), any(), any());
    }

    @Test
    void schedulesTechnicalSheetCleanupWhenMaterialIsDeleted() {
        productoService.deleteMaterial("M-3");

        verify(materialFichaTecnicaService).scheduleStorageCleanupAfterMaterialDeletion("M-3");
        verify(materialRepo).deleteById("M-3");
    }

    private static Material validMaterial(String id) {
        Material material = new Material();
        material.setProductoId(id);
        material.setInventareable(true);
        material.setPuntoReorden(0);
        material.asignarCostoInicial(BigDecimal.ZERO);
        return material;
    }
}
