package exotic.app.planta.model.compras;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import exotic.app.planta.model.users.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrdenCompraMaterialesSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void auditSnapshotsAreVisibleWithoutSerializingUserEntities() {
        User creator = user(10L, "creador.real", "secreto");
        User releaser = user(20L, "liberador.real", "otro-secreto");

        OrdenCompraMateriales orden = new OrdenCompraMateriales();
        orden.setUsuarioCreador(creator);
        orden.setUsuarioCreadorUsername(creator.getUsername());
        orden.setUsuarioLiberador(releaser);
        orden.setUsuarioLiberadorUsername(releaser.getUsername());
        orden.setFechaLiberacion(LocalDateTime.of(2026, 7, 25, 10, 30));

        JsonNode json = objectMapper.valueToTree(orden);

        assertFalse(json.has("usuarioCreador"));
        assertFalse(json.has("usuarioLiberador"));
        assertEquals("creador.real", json.get("usuarioCreadorUsername").asText());
        assertEquals("liberador.real", json.get("usuarioLiberadorUsername").asText());
        assertEquals("2026-07-25T10:30:00", json.get("fechaLiberacion").asText());
    }

    @Test
    void auditSnapshotsCannotBeSuppliedByClientJson() throws Exception {
        OrdenCompraMateriales orden = objectMapper.readValue(
                """
                {
                  "usuarioCreadorUsername": "usuario.suplantado",
                  "usuarioLiberadorUsername": "liberador.suplantado",
                  "fechaLiberacion": "2026-07-25T10:30:00"
                }
                """,
                OrdenCompraMateriales.class
        );

        assertNull(orden.getUsuarioCreadorUsername());
        assertNull(orden.getUsuarioLiberadorUsername());
        assertNull(orden.getFechaLiberacion());
    }

    private static User user(Long id, String username, String password) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        return user;
    }
}
