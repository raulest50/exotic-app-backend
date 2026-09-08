package exotic.app.planta.service.productos.fichatecnica;

import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.producto.MaterialRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialFichaTecnicaServiceTest {

    @Mock
    private MaterialRepo materialRepo;

    @Mock
    private MaterialFichaTecnicaStorage storage;

    @InjectMocks
    private MaterialFichaTecnicaService service;

    @Test
    void reportsAvailabilityForExistingMaterial() {
        Material material = material("M-1", "fichas_tecnicas_mp/a.pdf");
        when(materialRepo.findById("M-1")).thenReturn(Optional.of(material));
        when(storage.isAvailable(material.getFichaTecnicaUrl())).thenReturn(true);

        assertTrue(service.isAvailable("M-1"));
    }

    @Test
    void reportsMissingWhenReferenceCannotBeLoaded() {
        Material material = material("M-1", "fichas_tecnicas_mp/missing.pdf");
        when(materialRepo.findById("M-1")).thenReturn(Optional.of(material));

        assertFalse(service.isAvailable("M-1"));
    }

    @Test
    void rejectsUnknownOrNonMaterialProductId() {
        when(materialRepo.findById("T-1")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.isAvailable("T-1"));
    }

    @Test
    void returnsDownloadWhenStoredFileExists() {
        Material material = material("M-1", "fichas_tecnicas_mp/a.pdf");
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        when(materialRepo.findById("M-1")).thenReturn(Optional.of(material));
        when(storage.load(material.getFichaTecnicaUrl())).thenReturn(Optional.of(
                new MaterialFichaTecnicaStorage.StoredTechnicalSheet(resource, 3)
        ));

        MaterialFichaTecnicaService.TechnicalSheetDownload download = service.load("M-1");

        assertEquals(3, download.contentLength());
        assertEquals(resource, download.resource());
    }

    @Test
    void returnsNotFoundWhenStoredFileIsUnavailable() {
        Material material = material("M-1", "fichas_tecnicas_mp/missing.pdf");
        when(materialRepo.findById("M-1")).thenReturn(Optional.of(material));
        when(storage.load(material.getFichaTecnicaUrl())).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.load("M-1"));
    }

    private static Material material(String id, String storedReference) {
        Material material = new Material();
        material.setProductoId(id);
        material.setFichaTecnicaUrl(storedReference);
        return material;
    }
}
