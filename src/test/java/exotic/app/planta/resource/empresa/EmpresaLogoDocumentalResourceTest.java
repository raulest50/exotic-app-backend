package exotic.app.planta.resource.empresa;

import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.empresa.EmpresaLogoDocumentalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmpresaLogoDocumentalResourceTest {

    private EmpresaLogoDocumentalService service;
    private Authentication authentication;
    private EmpresaLogoDocumentalResource resource;

    @BeforeEach
    void setUp() {
        service = mock(EmpresaLogoDocumentalService.class);
        UserRepository userRepository = mock(UserRepository.class);
        authentication = mock(Authentication.class);
        User user = new User();
        user.setUsername("operario");
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("operario");
        when(userRepository.findByUsername("operario")).thenReturn(Optional.of(user));
        resource = new EmpresaLogoDocumentalResource(service, userRepository);
    }

    @Test
    void getImagenVersion_retornaRecursoInmutableConEtag() {
        EmpresaLogoDocumentalVersion version = version();
        when(service.getVersion(3L)).thenReturn(version);

        ResponseEntity<byte[]> response = resource.getImagenVersion(authentication, 3L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("\"abc123\"", response.getHeaders().getETag());
        assertTrue(response.getHeaders().getCacheControl().contains("max-age=31536000"));
        assertTrue(response.getHeaders().getCacheControl().contains("private"));
        assertTrue(response.getHeaders().getCacheControl().contains("immutable"));
        assertArrayEquals(version.getContenido(), response.getBody());
    }

    @Test
    void getImagenVersion_retorna304SinPayload() {
        when(service.getVersion(3L)).thenReturn(version());

        ResponseEntity<byte[]> response =
                resource.getImagenVersion(authentication, 3L, "W/\"abc123\"");

        assertEquals(HttpStatus.NOT_MODIFIED, response.getStatusCode());
        assertEquals("\"abc123\"", response.getHeaders().getETag());
        assertNull(response.getBody());
    }

    @Test
    void getImagenVigente_exigeRevalidacion() {
        when(service.getVigente()).thenReturn(version());

        ResponseEntity<byte[]> response = resource.getImagenVigente(authentication, null);

        assertTrue(response.getHeaders().getCacheControl().contains("no-cache"));
        assertTrue(response.getHeaders().getCacheControl().contains("private"));
    }

    private static EmpresaLogoDocumentalVersion version() {
        byte[] bytes = "png".getBytes(StandardCharsets.UTF_8);
        EmpresaLogoDocumentalVersion version = new EmpresaLogoDocumentalVersion();
        version.setId(3L);
        version.setContentType("image/png");
        version.setTamanoBytes((long) bytes.length);
        version.setSha256("abc123");
        version.setContenido(bytes);
        return version;
    }
}
