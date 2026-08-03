package exotic.app.planta.resource.productos.procesos;

import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoService;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcesoProduccionResourceDocumentoAccessTest {

    @Test
    void exigeAutenticacionParaConsultarVersiones() {
        ProcesoProduccionResource resource = new ProcesoProduccionResource(
                mock(ProcesoProduccionService.class),
                mock(ProcesoProduccionDocumentoService.class),
                mock(UserRepository.class)
        );

        assertThatThrownBy(() -> resource.getDocumentoVersiones(null, 1))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
                );
    }

    @Test
    void exigeNivelDosDeProductos() {
        ProcesoProduccionDocumentoService documentoService =
                mock(ProcesoProduccionDocumentoService.class);
        UserRepository userRepository = mock(UserRepository.class);
        ProcesoProduccionResource resource = new ProcesoProduccionResource(
                mock(ProcesoProduccionService.class),
                documentoService,
                userRepository
        );
        Authentication authentication = authentication("operario");
        when(userRepository.findByUsername("operario"))
                .thenReturn(Optional.of(userWithProductosLevel("operario", 1)));

        assertThatThrownBy(() -> resource.getDocumentoVersiones(authentication, 1))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
                );
    }

    @Test
    void permiteGestionDocumentalConNivelDosDeProductos() {
        ProcesoProduccionDocumentoService documentoService =
                mock(ProcesoProduccionDocumentoService.class);
        UserRepository userRepository = mock(UserRepository.class);
        ProcesoProduccionResource resource = new ProcesoProduccionResource(
                mock(ProcesoProduccionService.class),
                documentoService,
                userRepository
        );
        Authentication authentication = authentication("calidad");
        when(userRepository.findByUsername("calidad"))
                .thenReturn(Optional.of(userWithProductosLevel("calidad", 2)));
        when(documentoService.getVersiones(1)).thenReturn(List.of());

        assertThat(resource.getDocumentoVersiones(authentication, 1).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        verify(documentoService).getVersiones(1);
    }

    private static Authentication authentication(String username) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);
        return authentication;
    }

    private static User userWithProductosLevel(String username, int level) {
        User user = User.builder().username(username).build();
        ModuloAcceso modulo = ModuloAcceso.builder()
                .user(user)
                .modulo(ModuloSistema.PRODUCTOS)
                .build();
        TabAcceso tab = TabAcceso.builder()
                .moduloAcceso(modulo)
                .tabId("DEFINICION_PROCESOS")
                .nivel(level)
                .build();
        modulo.setTabs(Set.of(tab));
        user.setModuloAccesos(Set.of(modulo));
        return user;
    }
}
