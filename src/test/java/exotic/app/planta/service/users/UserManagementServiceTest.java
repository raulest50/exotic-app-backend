package exotic.app.planta.service.users;

import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.dto.AssignModuloAccesoRequest;
import exotic.app.planta.model.users.dto.ModuloAccesoAssignmentDTO;
import exotic.app.planta.model.users.dto.TabAccesoAssignmentDTO;
import exotic.app.planta.model.users.dto.UpdateUserAccesosRequest;
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
    private UserOperationalCompatibilityService compatibilityService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        PasswordResetTokenRepository tokenRepo = Mockito.mock(PasswordResetTokenRepository.class);
        compatibilityService = Mockito.mock(UserOperationalCompatibilityService.class);
        service = new UserManagementService(userRepository, tokenRepo, compatibilityService);

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
        verify(compatibilityService).assertCanReceiveModuloAccesos(1L);
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
        verify(compatibilityService).assertCanReceiveModuloAccesos(1L);
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
        verify(compatibilityService).assertCanReceiveModuloAccesos(1L);
    }

    @Test
    void replaceUserAccesos_updatesExistingModuloInPlace() {
        ModuloAcceso stock = ModuloAcceso.builder()
                .id(20L)
                .user(user)
                .modulo(ModuloSistema.STOCK)
                .tabs(new HashSet<>(Set.of(
                        TabAcceso.builder().id(1L).moduloAcceso(null).tabId("CONSOLIDADO").nivel(1).build(),
                        TabAcceso.builder().id(2L).moduloAcceso(null).tabId("KARDEX").nivel(1).build()
                )))
                .build();
        fixTabBackRefs(stock);
        user.getModuloAccesos().add(stock);

        User updated = service.replaceUserAccesos(1L, UpdateUserAccesosRequest.builder()
                .accesos(List.of(
                        ModuloAccesoAssignmentDTO.builder()
                                .modulo(ModuloSistema.STOCK)
                                .tabs(List.of(
                                        TabAccesoAssignmentDTO.builder().tabId("CONSOLIDADO").nivel(3).build(),
                                        TabAccesoAssignmentDTO.builder().tabId("HISTORIAL_TRANSACCIONES_ALMACEN").nivel(2).build()
                                ))
                                .build()
                ))
                .build());

        assertEquals(1, updated.getModuloAccesos().size());
        ModuloAcceso only = updated.getModuloAccesos().iterator().next();
        assertSame(stock, only);
        assertEquals(2, only.getTabs().size());
        assertTrue(only.getTabs().stream().anyMatch(t -> t.getTabId().equals("CONSOLIDADO") && t.getNivel() == 3));
        assertTrue(only.getTabs().stream().anyMatch(t -> t.getTabId().equals("HISTORIAL_TRANSACCIONES_ALMACEN") && t.getNivel() == 2));
        assertFalse(only.getTabs().stream().anyMatch(t -> t.getTabId().equals("KARDEX")));
        verify(compatibilityService).assertCanReceiveModuloAccesos(1L);
    }

    @Test
    void replaceUserAccesos_removesMissingModuloAndCreatesNewOne() {
        ModuloAcceso stock = ModuloAcceso.builder()
                .id(20L)
                .user(user)
                .modulo(ModuloSistema.STOCK)
                .tabs(new HashSet<>(Set.of(
                        TabAcceso.builder().id(1L).moduloAcceso(null).tabId("CONSOLIDADO").nivel(1).build()
                )))
                .build();
        fixTabBackRefs(stock);
        user.getModuloAccesos().add(stock);

        User updated = service.replaceUserAccesos(1L, UpdateUserAccesosRequest.builder()
                .accesos(List.of(
                        ModuloAccesoAssignmentDTO.builder()
                                .modulo(ModuloSistema.USUARIOS)
                                .tabs(List.of(
                                        TabAccesoAssignmentDTO.builder().tabId("GESTION_USUARIOS").nivel(2).build()
                                ))
                                .build()
                ))
                .build());

        assertEquals(1, updated.getModuloAccesos().size());
        ModuloAcceso only = updated.getModuloAccesos().iterator().next();
        assertEquals(ModuloSistema.USUARIOS, only.getModulo());
        assertNotSame(stock, only);
        assertTrue(only.getTabs().stream().anyMatch(t -> t.getTabId().equals("GESTION_USUARIOS") && t.getNivel() == 2));
        verify(compatibilityService).assertCanReceiveModuloAccesos(1L);
    }

    @Test
    void replaceUserAccesos_rejectsWhenUserIsAreaResponsable() {
        Mockito.doThrow(new RuntimeException("bloqueado"))
                .when(compatibilityService)
                .assertCanReceiveModuloAccesos(1L);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.replaceUserAccesos(
                1L,
                UpdateUserAccesosRequest.builder()
                        .accesos(List.of(
                                ModuloAccesoAssignmentDTO.builder()
                                        .modulo(ModuloSistema.USUARIOS)
                                        .tabs(List.of(
                                                TabAccesoAssignmentDTO.builder().tabId("GESTION_USUARIOS").nivel(1).build()
                                        ))
                                        .build()
                        ))
                        .build()
        ));

        assertEquals("bloqueado", error.getMessage());
    }

    @Test
    void assignModuloAcceso_rejectsWhenUserIsAreaResponsable() {
        Mockito.doThrow(new RuntimeException("bloqueado"))
                .when(compatibilityService)
                .assertCanReceiveModuloAccesos(1L);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.assignModuloAcceso(
                1L,
                AssignModuloAccesoRequest.builder()
                        .modulo(ModuloSistema.USUARIOS)
                        .replaceTabs(false)
                        .tabs(List.of(TabAccesoAssignmentDTO.builder().tabId("GESTION_USUARIOS").nivel(1).build()))
                        .build()
        ));

        assertEquals("bloqueado", error.getMessage());
    }

    private static void fixTabBackRefs(ModuloAcceso ma) {
        for (TabAcceso t : ma.getTabs()) {
            t.setModuloAcceso(ma);
        }
    }
}
