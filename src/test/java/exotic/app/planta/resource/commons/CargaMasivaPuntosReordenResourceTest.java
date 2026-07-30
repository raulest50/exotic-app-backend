package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.CargaPuntosReordenDTOs;
import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.commons.CargaMasivaPuntosReordenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class CargaMasivaPuntosReordenResourceTest {

    private CargaMasivaPuntosReordenService service;
    private UserRepository userRepository;
    private CargaMasivaPuntosReordenResource resource;

    @BeforeEach
    void setUp() {
        service = mock(CargaMasivaPuntosReordenService.class);
        userRepository = mock(UserRepository.class);
        resource = new CargaMasivaPuntosReordenResource(service, userRepository);
    }

    @Test
    void validateRejectsUnauthenticatedRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> resource.validate(file(), null));

        assertEquals(UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(service, userRepository);
    }

    @Test
    void validateRejectsUserWithoutMassiveLoadsTab() {
        User user = user("sin-acceso");
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> resource.validate(file(), auth(user.getUsername())));

        assertEquals(FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void validateRejectsMassiveLoadsLevelZero() {
        User user = user("nivel-cero", tab("CARGAS_MASIVAS", 0));
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> resource.validate(file(), auth(user.getUsername())));

        assertEquals(FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void validateAllowsLevelOneAndDelegatesToService() {
        User user = user("operador", tab("CARGAS_MASIVAS", 1));
        MockMultipartFile file = file();
        CargaPuntosReordenDTOs.ValidationResponse validation =
                new CargaPuntosReordenDTOs.ValidationResponse(
                        true, 1, 0, 0, 1, 0, List.of(), List.of());
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(service.validateExcel(file)).thenReturn(validation);

        ResponseEntity<CargaPuntosReordenDTOs.ValidationResponse> response =
                resource.validate(file, auth(user.getUsername()));

        assertEquals(OK, response.getStatusCode());
        assertEquals(validation, response.getBody());
        verify(service).validateExcel(file);
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile(
                "file",
                "puntos_reorden.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1});
    }

    private static Authentication auth(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of());
    }

    private static User user(String username, TabAcceso... tabs) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        if (tabs.length > 0) {
            ModuloAcceso module = ModuloAcceso.builder()
                    .user(user)
                    .modulo(ModuloSistema.OPERACIONES_CRITICAS_BD)
                    .tabs(new HashSet<>(List.of(tabs)))
                    .build();
            for (TabAcceso tab : tabs) {
                tab.setModuloAcceso(module);
            }
            user.setModuloAccesos(new HashSet<>(List.of(module)));
        }
        return user;
    }

    private static TabAcceso tab(String tabId, int level) {
        return TabAcceso.builder()
                .tabId(tabId)
                .nivel(level)
                .build();
    }
}
