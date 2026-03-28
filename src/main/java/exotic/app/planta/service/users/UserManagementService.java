// src/main/java/lacosmetics/planta/lacmanufacture/service/UserManagementService.java
package exotic.app.planta.service.users;

import exotic.app.planta.config.PasswordConfig;
import exotic.app.planta.model.users.MapaAccesos;
import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.dto.AssignModuloAccesoRequest;
import exotic.app.planta.model.users.dto.ModuloAccesoAssignmentDTO;
import exotic.app.planta.model.users.dto.SearchUserDTO;
import exotic.app.planta.model.users.dto.TabAccesoAssignmentDTO;
import exotic.app.planta.model.users.dto.UpdateUserAccesosRequest;
import exotic.app.planta.model.users.dto.UpdateUserInfoDTO;
import exotic.app.planta.repo.usuarios.PasswordResetTokenRepository;
import exotic.app.planta.repo.usuarios.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getUsersByEstado(int estado) {
        return userRepository.findByEstado(estado);
    }

    public User createUser(User user) {
        user.setPassword(PasswordConfig.encodePassword(user.getPassword(), user.getUsername()));
        return userRepository.save(user);
    }

    public User updateUser(Long userId, User updatedUser) {
        User existing = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        existing.setUsername(updatedUser.getUsername());

        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
            existing.setPassword(PasswordConfig.encodePassword(updatedUser.getPassword(), existing.getUsername()));
        }

        // moduloAccesos se gestiona solo via assignModuloAcceso / removeModuloAccesoFromUser
        return userRepository.save(existing);
    }

    public User deactivateUser(Long userId) {
        User existing = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("master".equalsIgnoreCase(existing.getUsername()) || "super_master".equalsIgnoreCase(existing.getUsername())) {
            throw new RuntimeException("Cannot deactivate master or super_master user");
        }

        if (existing.getEstado() == 1) {
            existing.setEstado(2);
            return userRepository.save(existing);
        } else {
            throw new RuntimeException("User is already inactive");
        }
    }

    public User activateUser(Long userId) {
        User existing = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (existing.getEstado() == 2) {
            existing.setEstado(1);
            return userRepository.save(existing);
        } else {
            throw new RuntimeException("User is already active");
        }
    }

    public void deleteUser(Long userId) {
        User existing = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("master".equalsIgnoreCase(existing.getUsername()) || "super_master".equalsIgnoreCase(existing.getUsername())) {
            throw new RuntimeException("Cannot delete master or super_master user");
        }

        try {
            passwordResetTokenRepository.deleteByUser(existing);
            userRepository.delete(existing);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Cannot delete user because it is referenced by other entities. Consider deactivating the user instead.", e);
        }
    }

    public User removeModuloAccesoFromUser(Long userId, Long moduloAccesoId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if ("master".equalsIgnoreCase(user.getUsername()) || "super_master".equalsIgnoreCase(user.getUsername())) {
            throw new RuntimeException("Cannot remove accesos from master or super_master user");
        }
        boolean removed = user.getModuloAccesos().removeIf(ma -> ma.getId() != null && ma.getId().equals(moduloAccesoId));
        if (!removed) {
            throw new RuntimeException("ModuloAcceso not found for user");
        }
        return userRepository.save(user);
    }

    public User assignModuloAcceso(Long userId, AssignModuloAccesoRequest request) {
        if (request.getModulo() == null) {
            throw new RuntimeException("Modulo requerido");
        }
        boolean replace = Boolean.TRUE.equals(request.getReplaceTabs());
        List<TabAccesoAssignmentDTO> tabsList = request.getTabs();

        if (!replace && (tabsList == null || tabsList.isEmpty())) {
            throw new RuntimeException("Debe indicar al menos un tab con nivel");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("master".equalsIgnoreCase(user.getUsername()) || "super_master".equalsIgnoreCase(user.getUsername())) {
            throw new RuntimeException("Cannot assign accesos to master or super_master user");
        }

        if (replace && (tabsList == null || tabsList.isEmpty())) {
            user.getModuloAccesos().removeIf(ma -> ma.getModulo() == request.getModulo());
            return userRepository.save(user);
        }

        Set<String> tabIds = tabsList.stream()
                .map(TabAccesoAssignmentDTO::getTabId)
                .collect(Collectors.toSet());
        try {
            MapaAccesos.validateAssignments(request.getModulo(), tabIds);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e.getMessage());
        }

        ModuloAcceso moduloAcceso = user.getModuloAccesos().stream()
                .filter(ma -> ma.getModulo() == request.getModulo())
                .findFirst()
                .orElseGet(() -> {
                    ModuloAcceso created = ModuloAcceso.builder()
                            .user(user)
                            .modulo(request.getModulo())
                            .tabs(new HashSet<>())
                            .build();
                    user.getModuloAccesos().add(created);
                    return created;
                });

        if (replace) {
            moduloAcceso.getTabs().removeIf(t -> !tabIds.contains(t.getTabId()));
        }

        for (TabAccesoAssignmentDTO t : tabsList) {
            TabAcceso existingTab = moduloAcceso.getTabs().stream()
                    .filter(x -> x.getTabId().equals(t.getTabId()))
                    .findFirst()
                    .orElse(null);
            if (existingTab != null) {
                existingTab.setNivel(t.getNivel());
            } else {
                moduloAcceso.getTabs().add(TabAcceso.builder()
                        .moduloAcceso(moduloAcceso)
                        .tabId(t.getTabId())
                        .nivel(t.getNivel())
                        .build());
            }
        }

        return userRepository.save(user);
    }

    public User replaceUserAccesos(Long userId, UpdateUserAccesosRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("master".equalsIgnoreCase(user.getUsername()) || "super_master".equalsIgnoreCase(user.getUsername())) {
            throw new RuntimeException("Cannot assign accesos to master or super_master user");
        }

        List<ModuloAccesoAssignmentDTO> accesos = request != null && request.getAccesos() != null
                ? request.getAccesos()
                : List.of();

        Set<ModuloSistema> seenModulos = new HashSet<>();
        for (ModuloAccesoAssignmentDTO acceso : accesos) {
            if (acceso.getModulo() == null) {
                throw new RuntimeException("Modulo requerido");
            }
            if (!seenModulos.add(acceso.getModulo())) {
                throw new RuntimeException("Modulo duplicado en el payload: " + acceso.getModulo());
            }

            List<TabAccesoAssignmentDTO> tabs = acceso.getTabs() != null ? acceso.getTabs() : List.of();
            Set<String> tabIds = new LinkedHashSet<>();
            for (TabAccesoAssignmentDTO tab : tabs) {
                if (tab == null || tab.getTabId() == null || tab.getTabId().isBlank()) {
                    throw new RuntimeException("Cada tab debe tener tabId");
                }
                if (!tabIds.add(tab.getTabId())) {
                    throw new RuntimeException("Tab duplicado en el modulo " + acceso.getModulo() + ": " + tab.getTabId());
                }
                if (tab.getNivel() < 1 || tab.getNivel() > 4) {
                    throw new RuntimeException("Nivel invalido para " + tab.getTabId() + ": " + tab.getNivel());
                }
            }

            try {
                MapaAccesos.validateAssignments(acceso.getModulo(), tabIds);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        Map<ModuloSistema, ModuloAcceso> existentesPorModulo = new EnumMap<>(ModuloSistema.class);
        for (ModuloAcceso existente : user.getModuloAccesos()) {
            existentesPorModulo.put(existente.getModulo(), existente);
        }

        user.getModuloAccesos().removeIf(existente -> !seenModulos.contains(existente.getModulo()));

        for (ModuloAccesoAssignmentDTO acceso : accesos) {
            List<TabAccesoAssignmentDTO> tabs = acceso.getTabs() != null ? acceso.getTabs() : List.of();
            if (tabs.isEmpty()) {
                continue;
            }

            ModuloAcceso moduloAcceso = existentesPorModulo.get(acceso.getModulo());
            if (moduloAcceso == null) {
                moduloAcceso = ModuloAcceso.builder()
                        .user(user)
                        .modulo(acceso.getModulo())
                        .tabs(new HashSet<>())
                        .build();
                user.getModuloAccesos().add(moduloAcceso);
                existentesPorModulo.put(acceso.getModulo(), moduloAcceso);
            }

            Set<String> requestedTabIds = tabs.stream()
                    .map(TabAccesoAssignmentDTO::getTabId)
                    .collect(Collectors.toSet());

            moduloAcceso.getTabs().removeIf(existingTab -> !requestedTabIds.contains(existingTab.getTabId()));

            Map<String, TabAcceso> existingTabs = moduloAcceso.getTabs().stream()
                    .collect(Collectors.toMap(TabAcceso::getTabId, tab -> tab));

            for (TabAccesoAssignmentDTO tab : tabs) {
                TabAcceso existingTab = existingTabs.get(tab.getTabId());
                if (existingTab != null) {
                    existingTab.setNivel(tab.getNivel());
                } else {
                    moduloAcceso.getTabs().add(TabAcceso.builder()
                            .moduloAcceso(moduloAcceso)
                            .tabId(tab.getTabId())
                            .nivel(tab.getNivel())
                            .build());
                }
            }
        }

        return userRepository.save(user);
    }

    public User patchUserInfo(Long userId, UpdateUserInfoDTO dto) {
        User existing = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        existing.setCedula(dto.getCedula());
        existing.setUsername(dto.getUsername());
        existing.setNombreCompleto(dto.getNombreCompleto());
        existing.setEmail(dto.getEmail());
        existing.setCel(dto.getCel());
        existing.setDireccion(dto.getDireccion());
        existing.setFechaNacimiento(dto.getFechaNacimiento());
        return userRepository.save(existing);
    }

    public List<User> searchUser_by_DTO(SearchUserDTO searchUserDTO, int page, int size) {
        if (page < 0) page = 0;
        if (size <= 0) size = 10;

        Pageable pageable = PageRequest.of(page, size);

        if (searchUserDTO.getSearchType() == null || searchUserDTO.getSearch() == null || searchUserDTO.getSearch().trim().isEmpty()) {
            return userRepository.findAll(pageable).getContent();
        }

        switch (searchUserDTO.getSearchType()) {
            case ID:
                try {
                    long cedula = Long.parseLong(searchUserDTO.getSearch());
                    return userRepository.findAll(
                            (root, query, cb) -> cb.equal(root.get("cedula"), cedula),
                            pageable
                    ).getContent();
                } catch (NumberFormatException e) {
                    return new ArrayList<>();
                }

            case NAME:
                return userRepository.findAll(
                        (root, query, cb) -> cb.like(
                                cb.lower(root.get("nombreCompleto")),
                                "%" + searchUserDTO.getSearch().toLowerCase() + "%"
                        ),
                        pageable
                ).getContent();

            case EMAIL:
                return userRepository.findAll(
                        (root, query, cb) -> cb.like(
                                cb.lower(root.get("email")),
                                "%" + searchUserDTO.getSearch().toLowerCase() + "%"
                        ),
                        pageable
                ).getContent();

            default:
                return new ArrayList<>();
        }
    }
}
