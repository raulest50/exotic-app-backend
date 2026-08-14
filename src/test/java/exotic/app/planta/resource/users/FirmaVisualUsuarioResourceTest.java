package exotic.app.planta.resource.users;

import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.users.FirmaVisualUsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirmaVisualUsuarioResourceTest {

    private FirmaVisualUsuarioService service;
    private ModuleTabAccessGuard accessGuard;
    private Authentication authentication;
    private FirmaVisualUsuarioResource resource;

    @BeforeEach
    void setUp() {
        service = mock(FirmaVisualUsuarioService.class);
        accessGuard = mock(ModuleTabAccessGuard.class);
        authentication = mock(Authentication.class);
        when(accessGuard.requireTabAccess(
                authentication,
                ModuloSistema.USUARIOS,
                "GESTION_USUARIOS",
                2,
                "No tiene permisos para administrar firmas visuales de usuarios."
        )).thenReturn(administrador());
        resource = new FirmaVisualUsuarioResource(service, accessGuard);
    }

    @Test
    void getImagenVersion_retornaContenidoInmutableConEtag() {
        when(service.getVersion(7L, 3L)).thenReturn(version());

        ResponseEntity<byte[]> response = resource.getImagenVersion(
                authentication,
                7L,
                3L,
                null
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("\"abc123\"", response.getHeaders().getETag());
        assertTrue(response.getHeaders().getCacheControl().contains("immutable"));
        assertTrue(response.getHeaders().getCacheControl().contains("private"));
        assertArrayEquals(version().getContenido(), response.getBody());
        verify(accessGuard).requireTabAccess(
                authentication,
                ModuloSistema.USUARIOS,
                "GESTION_USUARIOS",
                2,
                "No tiene permisos para administrar firmas visuales de usuarios."
        );
    }

    @Test
    void getImagenVigente_retorna304YExigeRevalidacion() {
        when(service.getVigente(7L)).thenReturn(version());

        ResponseEntity<byte[]> response = resource.getImagenVigente(
                authentication,
                7L,
                "W/\"abc123\""
        );

        assertEquals(HttpStatus.NOT_MODIFIED, response.getStatusCode());
        assertTrue(response.getHeaders().getCacheControl().contains("no-cache"));
        assertNull(response.getBody());
    }

    private static User administrador() {
        User user = new User();
        user.setId(2L);
        user.setUsername("admin");
        return user;
    }

    private static FirmaVisualUsuarioVersion version() {
        byte[] bytes = "png".getBytes(StandardCharsets.UTF_8);
        FirmaVisualUsuarioVersion version = new FirmaVisualUsuarioVersion();
        version.setId(3L);
        version.setContentType("image/png");
        version.setTamanoBytes((long) bytes.length);
        version.setSha256("abc123");
        version.setContenido(bytes);
        return version;
    }
}
