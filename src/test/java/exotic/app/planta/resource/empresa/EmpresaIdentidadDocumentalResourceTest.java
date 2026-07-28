package exotic.app.planta.resource.empresa;

import exotic.app.planta.model.empresa.dto.EmpresaIdentidadDocumentalVigenteResponse;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.empresa.EmpresaIdentidadDocumentalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmpresaIdentidadDocumentalResourceTest {

    private EmpresaIdentidadDocumentalService service;
    private Authentication authentication;
    private EmpresaIdentidadDocumentalResource resource;

    @BeforeEach
    void setUp() {
        service = mock(EmpresaIdentidadDocumentalService.class);
        UserRepository userRepository = mock(UserRepository.class);
        authentication = mock(Authentication.class);
        User user = new User();
        user.setUsername("operario");
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("operario");
        when(userRepository.findByUsername("operario")).thenReturn(Optional.of(user));
        resource = new EmpresaIdentidadDocumentalResource(service, userRepository);
    }

    @Test
    void getVigente_retornaEtagYPoliticaDeRevalidacion() {
        when(service.getVigente()).thenReturn(response());

        ResponseEntity<EmpresaIdentidadDocumentalVigenteResponse> result =
                resource.getVigente(authentication, null);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("\"identidad-2-logo-3\"", result.getHeaders().getETag());
        assertTrue(result.getHeaders().getCacheControl().contains("no-cache"));
        assertTrue(result.getHeaders().getCacheControl().contains("private"));
        assertEquals("identidad-2-logo-3", result.getBody().revision());
    }

    @Test
    void getVigente_retorna304CuandoCoincideIfNoneMatch() {
        when(service.getVigente()).thenReturn(response());

        ResponseEntity<EmpresaIdentidadDocumentalVigenteResponse> result =
                resource.getVigente(authentication, "W/\"anterior\", \"identidad-2-logo-3\"");

        assertEquals(HttpStatus.NOT_MODIFIED, result.getStatusCode());
        assertEquals("\"identidad-2-logo-3\"", result.getHeaders().getETag());
        assertNull(result.getBody());
    }

    private static EmpresaIdentidadDocumentalVigenteResponse response() {
        return new EmpresaIdentidadDocumentalVigenteResponse(
                "identidad-2-logo-3",
                new EmpresaIdentidadDocumentalVigenteResponse.IdentidadLegal(
                        2L,
                        2,
                        "Laboratorios Novum S.A.S.",
                        "Novum",
                        "NIT",
                        "902038623",
                        "5",
                        "3000000000",
                        "documental@example.com"
                ),
                new EmpresaIdentidadDocumentalVigenteResponse.Logo(
                        3L,
                        3,
                        "sha",
                        "image/png",
                        100L,
                        200,
                        200,
                        "/api/empresa-logo-documental/versiones/3/imagen"
                )
        );
    }
}
