package exotic.app.planta.service.productos.fichatecnica;

import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.fichatecnica.MaterialFichaTecnicaVersion;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.fichatecnica.MaterialFichaTecnicaVersionRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialFichaTecnicaServiceTest {

    @Mock private MaterialRepo materialRepo;
    @Mock private MaterialFichaTecnicaVersionRepo versionRepo;
    @Mock private MaterialFichaTecnicaStorage storage;

    @InjectMocks private MaterialFichaTecnicaService service;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void returnsAvailableCurrentVersionMetadata() {
        Material material = material("M-1");
        MaterialFichaTecnicaVersion version = version(material, 1, "fichas_tecnicas_mp/a.pdf");
        when(materialRepo.findById("M-1")).thenReturn(Optional.of(material));
        when(versionRepo.findByMaterialProductoIdAndEstado(
                "M-1", MaterialFichaTecnicaVersion.Estado.VIGENTE)).thenReturn(Optional.of(version));
        when(versionRepo.countByMaterialProductoId("M-1")).thenReturn(1L);
        when(storage.load(version.getStorageKey())).thenReturn(stored(new byte[]{1, 2, 3}));

        MaterialFichaTecnicaService.FichaTecnicaMetadata metadata = service.getMetadata("M-1");

        assertTrue(metadata.disponible());
        assertEquals(1, metadata.totalVersiones());
        assertEquals(1, metadata.versionVigente().version());
    }

    @Test
    void reportsUnregisteredMaterialWithoutVersions() {
        when(materialRepo.findById("M-1")).thenReturn(Optional.of(material("M-1")));
        when(versionRepo.findByMaterialProductoIdAndEstado(
                "M-1", MaterialFichaTecnicaVersion.Estado.VIGENTE)).thenReturn(Optional.empty());

        MaterialFichaTecnicaService.FichaTecnicaMetadata metadata = service.getMetadata("M-1");

        assertFalse(metadata.disponible());
        assertNull(metadata.versionVigente());
    }

    @Test
    void rejectsUnknownOrNonMaterialProductId() {
        when(materialRepo.findById("T-1")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getMetadata("T-1"));
    }

    @Test
    void createsInitialVersionAsCurrent() throws Exception {
        Material material = material("M-1");
        MockMultipartFile file = pdf("ficha-proveedor.pdf", new byte[]{1, 2, 3});
        when(materialRepo.findByIdForUpdate("M-1")).thenReturn(Optional.of(material));
        when(versionRepo.findByMaterialProductoIdAndEstado(
                "M-1", MaterialFichaTecnicaVersion.Estado.VIGENTE)).thenReturn(Optional.empty());
        when(versionRepo.findMaxVersionByMaterialId("M-1")).thenReturn(0);
        when(storage.store(file)).thenReturn("fichas_tecnicas_mp/new.pdf");
        when(storage.load("fichas_tecnicas_mp/new.pdf")).thenReturn(stored(file.getBytes()));
        when(versionRepo.save(any(MaterialFichaTecnicaVersion.class))).thenAnswer(invocation -> {
            MaterialFichaTecnicaVersion saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        var created = service.crearNuevaVersion("M-1", file, "", "editor");

        assertEquals(1, created.version());
        assertEquals("Carga inicial", created.motivoCambio());
        assertEquals("editor", created.creadoPor());
        assertTrue(created.disponible());
    }

    @Test
    void retiresCurrentVersionAndRequiresReason() throws Exception {
        Material material = material("M-1");
        MaterialFichaTecnicaVersion current = version(material, 1, "fichas_tecnicas_mp/old.pdf");
        current.setSha256("0".repeat(64));
        MockMultipartFile file = pdf("nueva.pdf", new byte[]{4, 5, 6});
        when(materialRepo.findByIdForUpdate("M-1")).thenReturn(Optional.of(material));
        when(versionRepo.findByMaterialProductoIdAndEstado(
                "M-1", MaterialFichaTecnicaVersion.Estado.VIGENTE)).thenReturn(Optional.of(current));

        assertThrows(IllegalArgumentException.class,
                () -> service.crearNuevaVersion("M-1", file, " ", "editor"));

        when(versionRepo.findMaxVersionByMaterialId("M-1")).thenReturn(1);
        when(storage.store(file)).thenReturn("fichas_tecnicas_mp/new.pdf");
        when(storage.load("fichas_tecnicas_mp/new.pdf")).thenReturn(stored(file.getBytes()));
        when(versionRepo.save(any(MaterialFichaTecnicaVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.crearNuevaVersion("M-1", file, "Actualización del proveedor", "editor");

        assertEquals(2, created.version());
        assertEquals(MaterialFichaTecnicaVersion.Estado.RETIRADA, current.getEstado());
        assertTrue(current.getVigenteHasta() != null);
    }

    @Test
    void rejectsPdfIdenticalToCurrentVersion() throws Exception {
        byte[] bytes = new byte[]{7, 8, 9};
        Material material = material("M-1");
        MaterialFichaTecnicaVersion current = version(material, 1, "fichas_tecnicas_mp/current.pdf");
        current.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        MockMultipartFile file = pdf("same.pdf", bytes);
        when(materialRepo.findByIdForUpdate("M-1")).thenReturn(Optional.of(material));
        when(versionRepo.findByMaterialProductoIdAndEstado(
                "M-1", MaterialFichaTecnicaVersion.Estado.VIGENTE)).thenReturn(Optional.of(current));

        assertThrows(MaterialFichaTecnicaDuplicadaException.class,
                () -> service.crearNuevaVersion("M-1", file, "Repetición", "editor"));
        verify(storage, never()).store(any());
    }

    @Test
    void listsUnavailableHistoricalVersionWithoutExposingStorage() {
        Material material = material("M-1");
        MaterialFichaTecnicaVersion unavailable = version(
                material, 1, "https://example.com/insegura.pdf");
        when(materialRepo.findById("M-1")).thenReturn(Optional.of(material));
        when(versionRepo.findAllByMaterialProductoIdOrderByVersionDesc("M-1"))
                .thenReturn(List.of(unavailable));

        var response = service.getVersiones("M-1").getFirst();

        assertFalse(response.disponible());
        assertEquals(1, response.version());
    }

    @Test
    void deletesNewFileWhenTransactionRollsBack() throws Exception {
        Material material = material("M-1");
        MockMultipartFile file = pdf("ficha.pdf", new byte[]{1, 2, 3});
        when(materialRepo.findByIdForUpdate("M-1")).thenReturn(Optional.of(material));
        when(versionRepo.findByMaterialProductoIdAndEstado(
                "M-1", MaterialFichaTecnicaVersion.Estado.VIGENTE)).thenReturn(Optional.empty());
        when(versionRepo.findMaxVersionByMaterialId("M-1")).thenReturn(0);
        when(storage.store(file)).thenReturn("fichas_tecnicas_mp/new.pdf");
        when(storage.load("fichas_tecnicas_mp/new.pdf")).thenReturn(stored(file.getBytes()));
        when(versionRepo.save(any(MaterialFichaTecnicaVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.crearNuevaVersion("M-1", file, null, "editor");
        verify(storage, never()).delete("fichas_tecnicas_mp/new.pdf");

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK
                )
        );

        verify(storage).delete("fichas_tecnicas_mp/new.pdf");
    }

    @Test
    void deletesVersionFilesOnlyAfterMaterialDeletionCommits() {
        when(versionRepo.findStorageKeysByMaterialId("M-1")).thenReturn(List.of(
                "fichas_tecnicas_mp/v1.pdf",
                "fichas_tecnicas_mp/v2.pdf"
        ));
        TransactionSynchronizationManager.initSynchronization();

        service.scheduleStorageCleanupAfterMaterialDeletion("M-1");

        verify(storage, never()).delete(any());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(storage, times(1)).delete("fichas_tecnicas_mp/v1.pdf");
        verify(storage, times(1)).delete("fichas_tecnicas_mp/v2.pdf");
    }

    private static Material material(String id) {
        Material material = new Material();
        material.setProductoId(id);
        return material;
    }

    private static MaterialFichaTecnicaVersion version(Material material, int number, String key) {
        MaterialFichaTecnicaVersion version = new MaterialFichaTecnicaVersion();
        version.setId((long) number);
        version.setMaterial(material);
        version.setVersion(number);
        version.setEstado(MaterialFichaTecnicaVersion.Estado.VIGENTE);
        version.setNombreArchivoOriginal("ficha.pdf");
        version.setContentType("application/pdf");
        version.setStorageKey(key);
        version.setVigenteDesde(LocalDateTime.now());
        version.setCreadoEn(LocalDateTime.now());
        version.setMotivoCambio("Carga inicial");
        return version;
    }

    private static MockMultipartFile pdf(String name, byte[] bytes) {
        return new MockMultipartFile("archivo", name, "application/pdf", bytes);
    }

    private static Optional<MaterialFichaTecnicaStorage.StoredTechnicalSheet> stored(byte[] bytes) {
        return Optional.of(new MaterialFichaTecnicaStorage.StoredTechnicalSheet(
                new ByteArrayResource(bytes), bytes.length
        ));
    }
}
