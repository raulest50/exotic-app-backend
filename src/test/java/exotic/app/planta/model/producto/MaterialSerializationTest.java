package exotic.app.planta.model.producto;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.producto.fichatecnica.MaterialFichaTecnicaVersion;
import exotic.app.planta.model.producto.fichatecnica.MaterialFichaTecnicaVersionResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MaterialSerializationTest {

    @Test
    void doesNotExposeTechnicalSheetStorageReference() throws Exception {
        MaterialFichaTecnicaVersion version = new MaterialFichaTecnicaVersion();
        version.setStorageKey("C:/private/data/ficha.pdf");
        version.setSha256("a".repeat(64));
        MaterialFichaTecnicaVersionResponse response = MaterialFichaTecnicaVersionResponse.from(
                version,
                true,
                100L
        );

        String json = new ObjectMapper().writeValueAsString(response);

        assertFalse(json.contains("storageKey"));
        assertFalse(json.contains("sha256"));
        assertFalse(json.contains("private/data"));
    }
}
