package exotic.app.planta.service.users;

import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.dto.AssignModuloAccesoRequest;
import exotic.app.planta.model.users.dto.TabAccesoAssignmentDTO;
import exotic.app.planta.repo.usuarios.PasswordResetTokenRepository;
import exotic.app.planta.repo.usuarios.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserManagementServiceTest {

    private UserRepository userRepository;
    private UserManagementService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        PasswordResetTokenRepository tokenRepo = Mockito.mock(PasswordResetTokenRepository.class);
        service = new UserManagementService(userRepository, tokenRepo);

        user = new User();
        user.setId(1L);
        user.setUsername("operator");
        user.setModuloAccesos(new HashSet<>());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void assignModuloAcceso_mergeKeepsOtherTabs() {
        ModuloAcceso ma = ModuloAcceso.builder()
                .id(10L)
                .user(user)
                .modulo(ModuloSistema.USUARIOS)
                .tabs(new HashSet<>(Set.of(
                        TabAcceso.builder().id(1L).moduloAcceso(null).tabId("GESTION_USUARIOS").nivel(1).build()
                )))
                .build();
        fixTabBackRefs(ma);
        user.getModuloAccesos().add(ma);

        service.assignModuloAcceso(1L, AssignModuloAccesoRequest.builder()
                .modulo(ModuloSistema.USUARIOS)
                .replaceTabs(false)
                .tabs(List.of(TabAccesoAssignmentDTO.builder().tabId("INFO_NIVELES").nivel(2).build()))
                .build());

        Set<String> ids = ma.getTabs().stream().map(TabAcceso::getTabId).collect(Collectors.toSet());
        assertTrue(ids.contains("GESTION_USUARIOS"));
        assertTrue(ids.contains("INFO_NIVELES"));
    }

    @Test
    void assignModuloAcceso_replaceRemovesUncheckedTabs() {
        ModuloAcceso ma = ModuloAcceso.builder()
                .id(10L)
                .user(user)
                .modulo(ModuloSistema.USUARIOS)
                .tabs(new HashSet<>(Set.of(
                        TabAcceso.builder().id(1L).moduloAcceso(null).tabId("GESTION_USUARIOS").nivel(1).build(),
                        TabAcceso.builder().id(2L).moduloAcceso(null).tabId("INFO_NIVELES").nivel(2).build()
                )))
                .build();
        fixTabBackRefs(ma);
        user.getModuloAccesos().add(ma);

        service.assignModuloAcceso(1L, AssignModuloAccesoRequest.builder()
                .modulo(ModuloSistema.USUARIOS)
                .replaceTabs(true)
                .tabs(List.of(TabAccesoAssignmentDTO.builder().tabId("GESTION_USUARIOS").nivel(3).build()))
                .build());

        assertEquals(1, ma.getTabs().size());
        TabAcceso only = ma.getTabs().iterator().next();
        assertEquals("GESTION_USUARIOS", only.getTabId());
        assertEquals(3, only.getNivel());
    }

    @Test
    void assignModuloAcceso_replaceWithEmptyTabsRemovesModulo() {
        ModuloAcceso ma = ModuloAcceso.builder()
                .id(10L)
                .user(user)
                .modulo(ModuloSistema.PRODUCTOS)
                .tabs(new HashSet<>(Set.of(
                        TabAcceso.builder().id(1L).moduloAcceso(null).tabId("MAIN").nivel(1).build()
                )))
                .build();
        fixTabBackRefs(ma);
        user.getModuloAccesos().add(ma);

        service.assignModuloAcceso(1L, AssignModuloAccesoRequest.builder()
                .modulo(ModuloSistema.PRODUCTOS)
                .replaceTabs(true)
                .tabs(List.of())
                .build());

        assertTrue(user.getModuloAccesos().isEmpty());
        verify(userRepository).save(user);
    }

    private static void fixTabBackRefs(ModuloAcceso ma) {
        for (TabAcceso t : ma.getTabs()) {
            t.setModuloAcceso(ma);
        }
    }
}
