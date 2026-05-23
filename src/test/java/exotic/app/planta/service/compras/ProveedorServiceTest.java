package exotic.app.planta.service.compras;

import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.service.commons.FileStorageService;
import exotic.app.planta.service.inventarios.RecepcionOcmPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProveedorServiceTest {

    @Test
    void saveProveedorWithFiles_invalidReceptionLimit_rejectsBeforeFilesOrPersist() {
        ProveedorRepo proveedorRepo = mock(ProveedorRepo.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        RecepcionOcmPolicyService recepcionOcmPolicyService = mock(RecepcionOcmPolicyService.class);
        ProveedorService service = new ProveedorService(
                proveedorRepo,
                fileStorageService,
                recepcionOcmPolicyService
        );

        Proveedor proveedor = buildProveedor("900123");
        proveedor.setLimiteRecepcionesParcialesOcm(5);
        MultipartFile rutFile = mock(MultipartFile.class);

        when(proveedorRepo.findById("900123")).thenReturn(Optional.empty());
        doThrow(new IllegalArgumentException("limite invalido"))
                .when(recepcionOcmPolicyService)
                .validarLimiteProveedor(5);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveProveedorWithFiles(proveedor, rutFile, null)
        );

        assertEquals("limite invalido", error.getMessage());
        verifyNoInteractions(fileStorageService);
        verify(proveedorRepo, never()).save(any(Proveedor.class));
    }

    @Test
    void updateProveedorWithFiles_validReceptionLimit_copiesValue() throws Exception {
        ProveedorRepo proveedorRepo = mock(ProveedorRepo.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        RecepcionOcmPolicyService recepcionOcmPolicyService = mock(RecepcionOcmPolicyService.class);
        ProveedorService service = new ProveedorService(
                proveedorRepo,
                fileStorageService,
                recepcionOcmPolicyService
        );

        Proveedor original = buildProveedor("900123");
        original.setLimiteRecepcionesParcialesOcm(null);

        Proveedor request = buildProveedor("900123");
        request.setRegimenTributario(2);
        request.setCondicionPago("contado");
        request.setLimiteRecepcionesParcialesOcm(1);

        when(proveedorRepo.findById("900123")).thenReturn(Optional.of(original));
        when(proveedorRepo.save(any(Proveedor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Proveedor updated = service.updateProveedorWithFiles("900123", request, null, null);

        verify(recepcionOcmPolicyService).validarLimiteProveedor(1);
        assertEquals(1, updated.getLimiteRecepcionesParcialesOcm());
        assertEquals(2, updated.getRegimenTributario());
        assertEquals("contado", updated.getCondicionPago());
    }

    @Test
    void updateProveedorWithFiles_unchangedLegacyReceptionLimitAboveGlobalDoesNotRevalidateLimit() throws Exception {
        ProveedorRepo proveedorRepo = mock(ProveedorRepo.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        RecepcionOcmPolicyService recepcionOcmPolicyService = mock(RecepcionOcmPolicyService.class);
        ProveedorService service = new ProveedorService(
                proveedorRepo,
                fileStorageService,
                recepcionOcmPolicyService
        );

        Proveedor original = buildProveedor("900123");
        original.setLimiteRecepcionesParcialesOcm(5);

        Proveedor request = buildProveedor("900123");
        request.setRegimenTributario(2);
        request.setCondicionPago("contado");
        request.setLimiteRecepcionesParcialesOcm(5);

        when(proveedorRepo.findById("900123")).thenReturn(Optional.of(original));
        when(proveedorRepo.save(any(Proveedor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Proveedor updated = service.updateProveedorWithFiles("900123", request, null, null);

        verify(recepcionOcmPolicyService, never()).validarLimiteProveedor(5);
        assertEquals(5, updated.getLimiteRecepcionesParcialesOcm());
        assertEquals(2, updated.getRegimenTributario());
        assertEquals("contado", updated.getCondicionPago());
    }

    private Proveedor buildProveedor(String id) {
        Proveedor proveedor = new Proveedor();
        proveedor.setId(id);
        proveedor.setNombre("Proveedor " + id);
        proveedor.setRegimenTributario(0);
        proveedor.setCondicionPago("credito");
        return proveedor;
    }
}
