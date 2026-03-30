// src/main/java/lacosmetics/planta/lacmanufacture/resource/UserManagementResource.java
package exotic.app.planta.resource.users;

import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.dto.AssignModuloAccesoRequest;
import exotic.app.planta.model.users.dto.SearchUserDTO;
import exotic.app.planta.model.users.dto.UpdateUserAccesosRequest;
import exotic.app.planta.model.users.dto.UpdateUserInfoDTO;
import exotic.app.planta.model.users.dto.UserAssignmentStatusDTO;
import exotic.app.planta.service.users.UserManagementService;
import exotic.app.planta.service.users.UserOperationalCompatibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UserManagementResource {

    private final UserManagementService userManagementService;
    private final UserOperationalCompatibilityService userOperationalCompatibilityService;

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userManagementService.getAllUsers();
        users.forEach(user -> user.setPassword(""));
        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User created = userManagementService.createUser(user);
        created.setPassword("");
        return ResponseEntity.created(URI.create("/usuarios/" + created.getId())).body(created);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUser(@PathVariable Long userId, @RequestBody User user) {
        User updated = userManagementService.updateUser(userId, user);
        updated.setPassword("");
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{userId}/info")
    public ResponseEntity<User> patchUserInfo(
            @PathVariable Long userId,
            @RequestBody UpdateUserInfoDTO dto) {
        User updated = userManagementService.patchUserInfo(userId, dto);
        updated.setPassword("");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        try {
            userManagementService.deleteUser(userId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(e.getMessage());
        }
    }

    @PutMapping("/{userId}/deactivate")
    public ResponseEntity<?> deactivateUser(@PathVariable Long userId) {
        try {
            User updated = userManagementService.deactivateUser(userId);
            updated.setPassword("");
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PutMapping("/{userId}/activate")
    public ResponseEntity<?> activateUser(@PathVariable Long userId) {
        try {
            User updated = userManagementService.activateUser(userId);
            updated.setPassword("");
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @DeleteMapping("/{userId}/modulo-accesos/{moduloAccesoId}")
    public ResponseEntity<User> removeModuloAccesoFromUser(
            @PathVariable Long userId,
            @PathVariable Long moduloAccesoId) {
        User updated = userManagementService.removeModuloAccesoFromUser(userId, moduloAccesoId);
        updated.setPassword("");
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{userId}/modulo-accesos")
    public ResponseEntity<User> assignModuloAcceso(
            @PathVariable Long userId,
            @RequestBody AssignModuloAccesoRequest request) {
        User updated = userManagementService.assignModuloAcceso(userId, request);
        updated.setPassword("");
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{userId}/accesos")
    public ResponseEntity<User> replaceUserAccesos(
            @PathVariable Long userId,
            @RequestBody UpdateUserAccesosRequest request) {
        User updated = userManagementService.replaceUserAccesos(userId, request);
        updated.setPassword("");
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{userId}/assignment-status")
    public ResponseEntity<UserAssignmentStatusDTO> getUserAssignmentStatus(
            @PathVariable Long userId,
            @RequestParam(required = false) Integer excludeAreaId) {
        return ResponseEntity.ok(userOperationalCompatibilityService.buildAssignmentStatus(userId, excludeAreaId));
    }

    @GetMapping("/filter")
    public ResponseEntity<List<User>> getUsersByEstado(@RequestParam int estado) {
        List<User> users = userManagementService.getUsersByEstado(estado);
        users.forEach(user -> user.setPassword(""));
        return ResponseEntity.ok(users);
    }

    @PostMapping("/search_by_dto")
    public ResponseEntity<List<User>> searchUserbyDTO(
            @RequestBody SearchUserDTO searchUserDTO,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<User> users = userManagementService.searchUser_by_DTO(searchUserDTO, page, size);
        users.forEach(user -> user.setPassword(""));
        return ResponseEntity.ok(users);
    }
}
