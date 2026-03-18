// src/main/java/lacosmetics/planta/lacmanufacture/resource/UserManagementResource.java
package exotic.app.planta.resource.users;

import exotic.app.planta.model.users.Acceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.dto.SearchUserDTO;
import exotic.app.planta.model.users.dto.UpdateUserInfoDTO;
import exotic.app.planta.service.users.UserManagementService;
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

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userManagementService.getAllUsers();

        // Limpiar las contraseñas antes de devolver los usuarios
        users.forEach(user -> user.setPassword(""));

        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User created = userManagementService.createUser(user);
        return ResponseEntity.created(URI.create("/usuarios/" + created.getId())).body(created);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUser(@PathVariable Long userId, @RequestBody User user) {
        User updated = userManagementService.updateUser(userId, user);
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

    /**
     * Endpoint to deactivate a user
     * @param userId the ID of the user to deactivate
     * @return the updated user
     */
    @PutMapping("/{userId}/deactivate")
    public ResponseEntity<?> deactivateUser(@PathVariable Long userId) {
        try {
            User updated = userManagementService.deactivateUser(userId);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    /**
     * Endpoint to activate a user
     * @param userId the ID of the user to activate
     * @return the updated user
     */
    @PutMapping("/{userId}/activate")
    public ResponseEntity<?> activateUser(@PathVariable Long userId) {
        try {
            User updated = userManagementService.activateUser(userId);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

/*
    @PostMapping("/{userId}/accesos")
    public ResponseEntity<User> addAccesoToUser(
            @PathVariable Long userId,
            @RequestParam String moduloName) {
        User updated = userManagementService.addAccesoToUser(userId, moduloName);
        return ResponseEntity.ok(updated);
    }
*/

    @DeleteMapping("/{userId}/accesos/{accesoId}")
    public ResponseEntity<User> removeAccesoFromUser(
            @PathVariable Long userId,
            @PathVariable Long accesoId) {
        User updated = userManagementService.removeAccesoFromUser(userId, accesoId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{userId}/accesos/modulo")
    public ResponseEntity<User> addAccesoToUserByModulo(
            @PathVariable Long userId,
            @RequestParam Acceso.Modulo modulo,
            @RequestParam(defaultValue = "1") int nivel) {
        User updated = userManagementService.addAccesoToUserByModulo(userId, modulo, nivel);
        return ResponseEntity.ok(updated);
    }

    /**
     * Endpoint to get users by estado
     * @param estado the estado to filter by (1 = active, 2 = inactive)
     * @return list of users with the specified estado
     */
    @GetMapping("/filter")
    public ResponseEntity<List<User>> getUsersByEstado(@RequestParam int estado) {
        List<User> users = userManagementService.getUsersByEstado(estado);
        return ResponseEntity.ok(users);
    }

    @PostMapping("/search_by_dto")
    public ResponseEntity<List<User>> searchUserbyDTO(
            @RequestBody SearchUserDTO searchUserDTO,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<User> users = userManagementService.searchUser_by_DTO(searchUserDTO, page, size);

        // Limpiar las contraseñas antes de devolver los usuarios
        users.forEach(user -> user.setPassword(""));

        return ResponseEntity.ok(users);
    }
}
