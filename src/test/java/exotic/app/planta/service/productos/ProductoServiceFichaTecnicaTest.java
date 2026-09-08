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
import exotic.app.planta.service.productos.fichatecnica.MaterialFichaTecnicaStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeastOnce;
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
    @Mock private MaterialFichaTecnicaStorage materialFichaTecnicaStorage;
    @Mock private ProductoCostoService productoCostoService;

    @InjectMocks
    private ProductoService productoService;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletesStoredTechnicalSheetWhenPersistenceFails() throws IOException {
        Material material = new Material();
        material.setProductoId("M-1");
        material.setInventareable(true);
        material.setPuntoReorden(0);
        MockMultipartFile file = new MockMultipartFile(
                "file", "ficha.pdf", "application/pdf", new byte[]{1}
        );
        String storedReference = "fichas_tecnicas_mp/generated.pdf";
        when(materialFichaTecnicaStorage.store(file)).thenReturn(storedReference);
        when(materialRepo.save(material)).thenThrow(new RuntimeException("database failure"));
        TransactionSynchronizationManager.initSynchronization();

        assertThrows(RuntimeException.class, () -> productoService.saveMateriaPrimaV2(material, file));
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
        );

        assertEquals(storedReference, material.getFichaTecnicaUrl());
        verify(materialFichaTecnicaStorage, atLeastOnce()).delete(storedReference);
    }
}
