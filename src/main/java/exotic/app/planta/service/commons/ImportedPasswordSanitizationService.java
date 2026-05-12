package exotic.app.planta.service.commons;

import exotic.app.planta.config.PasswordConfig;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImportedPasswordSanitizationService {

    private static final String OPERATION_NAME = "El saneamiento de contrasenas importadas";
    private static final String SANITIZED_PASSWORD = "staging1234";

    private final UserRepository userRepository;
    private final DangerousOperationGuard dangerousOperationGuard;

    @Transactional
    public PasswordSanitizationResult sanitizeImportedUserPasswords() {
        return sanitizeNonPrivilegedUserPasswords();
    }

    @Transactional
    public PasswordSanitizationResult sanitizeNonPrivilegedUserPasswords() {
        dangerousOperationGuard.assertLocalOrStagingOnly(OPERATION_NAME);

        List<User> users = userRepository.findAll();
        List<User> usersToUpdate = new ArrayList<>();
        int privilegedUsersSkipped = 0;
        int invalidUsersSkipped = 0;

        for (User user : users) {
            String username = user.getUsername();
            if (username == null || username.isBlank()) {
                invalidUsersSkipped++;
                continue;
            }

            if (isPrivilegedUser(username)) {
                privilegedUsersSkipped++;
                continue;
            }

            user.setPassword(PasswordConfig.encodePassword(SANITIZED_PASSWORD, username));
            usersToUpdate.add(user);
        }

        if (!usersToUpdate.isEmpty()) {
            userRepository.saveAll(usersToUpdate);
        }

        PasswordSanitizationResult result = new PasswordSanitizationResult(
                usersToUpdate.size(),
                privilegedUsersSkipped,
                invalidUsersSkipped
        );

        log.warn(
                "Saneamiento de contrasenas importadas completado. sanitizedUsers={}, privilegedUsersSkipped={}, invalidUsersSkipped={}",
                result.sanitizedUsers(),
                result.privilegedUsersSkipped(),
                result.invalidUsersSkipped()
        );

        return result;
    }

    private boolean isPrivilegedUser(String username) {
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return "master".equals(normalized) || "super_master".equals(normalized);
    }

    public record PasswordSanitizationResult(
            int sanitizedUsers,
            int privilegedUsersSkipped,
            int invalidUsersSkipped
    ) {
    }
}
