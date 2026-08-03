package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalProcesoProduccionDocumentoStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void almacenaCargaConClaveRelativaYPermiteEliminarla() throws Exception {
        StorageProperties properties = mock(StorageProperties.class);
        when(properties.getUPLOAD_DIR()).thenReturn(tempDir.toString());
        LocalProcesoProduccionDocumentoStorage storage =
                new LocalProcesoProduccionDocumentoStorage(properties);

        ProcesoProduccionDocumentoStorage.StoredFile stored =
                storage.store(22, "contenido".getBytes(), ".pdf");
        Resource resource = storage.load(stored.storageKey());

        assertThat(stored.storageKey()).startsWith("procesos-produccion/22/documentos/");
        assertThat(stored.storageKey()).endsWith(".pdf");
        assertThat(resource.exists()).isTrue();
        assertThat(resource.getContentAsByteArray()).isEqualTo("contenido".getBytes());

        storage.deleteIfExists(stored.storageKey());
        assertThat(resource.exists()).isFalse();
    }

    @Test
    void impideResolverRutasFueraDelDirectorioBase() {
        StorageProperties properties = mock(StorageProperties.class);
        when(properties.getUPLOAD_DIR()).thenReturn(tempDir.toString());
        LocalProcesoProduccionDocumentoStorage storage =
                new LocalProcesoProduccionDocumentoStorage(properties);

        assertThatThrownBy(() -> storage.load("../archivo.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es valida");
    }
}
