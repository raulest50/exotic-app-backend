package exotic.app.planta.model.producto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MaterialSerializationTest {

    @Test
    void doesNotExposeTechnicalSheetStorageReference() throws Exception {
        Material material = new Material();
        material.setProductoId("M-1");
        material.setFichaTecnicaUrl("C:/private/data/ficha.pdf");

        String json = new ObjectMapper().writeValueAsString(material);

        assertFalse(json.contains("fichaTecnicaUrl"));
        assertFalse(json.contains("private/data"));
    }
}
